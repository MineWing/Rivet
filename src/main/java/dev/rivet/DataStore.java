package dev.rivet;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Crash-safe storage for the files in {@code data/}.
 *
 * <p>Files are loaded strictly: one that exists but cannot be parsed is copied aside as
 * {@code name.yml.corrupt-<timestamp>} and its module becomes read-only, instead of starting
 * empty and letting the next save wipe every player's entries. Writes go through
 * {@code name.yml.tmp} and an atomic move, keeping one {@code name.yml.bak} of the previous file.
 *
 * <p>Hot paths call {@link DataFile#markDirty()} instead of saving; {@link #flushDirty()} (run on
 * the main thread every few seconds) serialises dirty files there and writes them on a background
 * thread. {@link #close()} writes everything still dirty synchronously and waits for pending writes.
 */
final class DataStore {
    private static final DateTimeFormatter CORRUPT_SUFFIX =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File directory;
    private final Logger logger;
    private final Map<String, DataFile> files = new HashMap<>();
    private final Map<String, String> unsafe = new HashMap<>();
    private final Set<String> dirty = new LinkedHashSet<>();
    // Background writes that failed; merged back into dirty by the next flush on the main thread.
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();
    private final Map<String, Long> written = new ConcurrentHashMap<>();
    private final AtomicLong snapshots = new AtomicLong();
    private ExecutorService writer;

    DataStore(File directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    File file(String module) {
        return new File(directory, module + ".yml");
    }

    DataFile data(String module) {
        return files.computeIfAbsent(module, this::load);
    }

    /** Writes a module's file now, on the calling thread. */
    void save(String module) throws IOException {
        DataFile data = data(module);
        checkWritable(module);
        dirty.remove(module);
        write(module, data.saveToString(), snapshots.incrementAndGet());
    }

    /** Queues a module for the next {@link #flushDirty()} instead of writing it immediately. */
    void markDirty(String module) {
        dirty.add(module);
    }

    boolean isDirty(String module) {
        return dirty.contains(module);
    }

    boolean isWritable(String module) {
        return !unsafe.containsKey(module);
    }

    /** Serialises every dirty module on the calling thread and writes them in the background. */
    void flushDirty() {
        dirty.addAll(drainFailed());
        for (String module : List.copyOf(dirty)) {
            dirty.remove(module);
            if (!isWritable(module)) {
                logger.severe(unsafeMessage(module));
                continue;
            }
            String contents = data(module).saveToString();
            long snapshot = snapshots.incrementAndGet();
            writer().execute(() -> {
                try {
                    write(module, contents, snapshot);
                } catch (IOException exception) {
                    failed.add(module);
                    logger.severe("Could not save data/" + module + ".yml: " + exception.getMessage());
                }
            });
        }
    }

    /** Writes every dirty module synchronously and waits for background writes to finish. */
    void close() {
        dirty.addAll(drainFailed());
        for (String module : List.copyOf(dirty)) {
            try {
                save(module);
            } catch (IOException exception) {
                logger.severe("Could not save data/" + module + ".yml: " + exception.getMessage());
            }
        }
        if (writer != null) {
            writer.shutdown();
            try {
                if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.severe("Timed out waiting for Rivet data files to finish saving.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            writer = null;
        }
        // A background write that failed during shutdown gets one synchronous retry.
        for (String module : drainFailed()) {
            try {
                save(module);
            } catch (IOException exception) {
                logger.severe("Could not save data/" + module + ".yml: " + exception.getMessage());
            }
        }
    }

    private Set<String> drainFailed() {
        Set<String> drained = new HashSet<>();
        for (String module : List.copyOf(failed)) {
            if (failed.remove(module)) {
                drained.add(module);
            }
        }
        return drained;
    }

    private DataFile load(String module) {
        DataFile data = new DataFile(this, module);
        File source = file(module);
        if (!source.exists()) {
            return data;
        }
        try {
            data.load(source);
            return data;
        } catch (IOException | InvalidConfigurationException exception) {
            String copy = quarantine(source.toPath(), LocalDateTime.now());
            unsafe.put(module, copy);
            logger.severe("data/" + module + ".yml could not be read: " + exception.getMessage());
            logger.severe(unsafeMessage(module));
            // Start from a clean, empty configuration; it is never written over the original.
            return new DataFile(this, module);
        }
    }

    private String unsafeMessage(String module) {
        String copy = unsafe.get(module);
        return "data/" + module + ".yml failed to load, so Rivet will not overwrite it and changes to "
            + module + " data are not being saved. "
            + (copy == null ? "" : "A copy was kept as data/" + copy + ". ")
            + "Fix or restore the file (data/" + module + ".yml.bak holds the previous save) and restart.";
    }

    private void checkWritable(String module) throws IOException {
        if (!isWritable(module)) {
            throw new IOException(unsafeMessage(module));
        }
    }

    private void write(String module, String contents, long snapshot) throws IOException {
        synchronized (locks.computeIfAbsent(module, ignored -> new Object())) {
            // A newer snapshot of this file already landed (e.g. a synchronous save overtook a
            // queued background write); writing the older one would roll it back.
            if (snapshot < written.getOrDefault(module, 0L)) {
                return;
            }
            writeAtomically(file(module).toPath(), contents);
            written.put(module, snapshot);
        }
    }

    private synchronized ExecutorService writer() {
        if (writer == null) {
            writer = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Rivet data writer");
                thread.setDaemon(true);
                return thread;
            });
        }
        return writer;
    }

    /**
     * Copies an unreadable file next to itself as {@code name.corrupt-<timestamp>} and returns the
     * copy's file name, or {@code null} if the copy could not be made.
     */
    static String quarantine(Path source, LocalDateTime now) {
        Path copy = source.resolveSibling(source.getFileName() + ".corrupt-" + CORRUPT_SUFFIX.format(now));
        try {
            Files.copy(source, copy, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (FileAlreadyExistsException exception) {
            // Same second, same file: the earlier copy is already there.
        } catch (IOException exception) {
            return null;
        }
        return copy.getFileName().toString();
    }

    /**
     * Replaces {@code target} with {@code contents} so a crash leaves either the old or the new
     * file, never a truncated one: write and fsync {@code name.tmp}, keep the current file as
     * {@code name.bak}, then atomically move the temporary file into place.
     */
    static void writeAtomically(Path target, String contents) throws IOException {
        Files.createDirectories(target.toAbsolutePath().getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = StandardCharsets.UTF_8.encode(contents);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        if (Files.isRegularFile(target)) {
            Files.copy(target, target.resolveSibling(target.getFileName() + ".bak"),
                StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A loaded data file that knows which module it belongs to, so callers can save or debounce it. */
    static final class DataFile extends YamlConfiguration {
        private final DataStore store;
        private final String module;

        private DataFile(DataStore store, String module) {
            this.store = store;
            this.module = module;
        }

        /** Saves within a few seconds; use for frequent changes such as inventory clicks. */
        void markDirty() {
            store.markDirty(module);
        }

        /** Saves immediately; use when a delay could lose or duplicate items (close, quit, removal). */
        void saveNow() throws IOException {
            store.save(module);
        }
    }
}
