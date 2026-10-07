package dev.rivet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DataStoreTest {
    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static Logger quietLogger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    @Test
    public void malformedFileIsQuarantinedAndNeverOverwritten() throws IOException {
        File directory = folder.newFolder("data");
        Path homes = directory.toPath().resolve("homes.yml");
        String broken = "homes:\n  alex: [unclosed\n";
        Files.writeString(homes, broken);

        DataStore store = new DataStore(directory, quietLogger());
        YamlConfiguration data = store.data("homes");
        assertTrue(data.getKeys(false).isEmpty());
        assertFalse(store.isWritable("homes"));
        try (var files = Files.list(directory.toPath())) {
            Path copy = files.filter(path -> path.getFileName().toString().startsWith("homes.yml.corrupt-"))
                .findFirst().orElse(null);
            assertNotNull(copy);
            assertEquals(broken, Files.readString(copy));
        }

        data.set("homes.sam.world", "world");
        try {
            store.save("homes");
            fail("Saving over an unreadable file must be refused");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("homes.yml"));
        }
        store.data("homes").markDirty();
        store.flushDirty();
        store.close();
        assertEquals(broken, Files.readString(homes));
        assertFalse(Files.exists(directory.toPath().resolve("homes.yml.tmp")));
    }

    @Test
    public void saveReplacesAtomicallyAndKeepsOneBackup() throws Exception {
        File directory = folder.newFolder("data");
        DataStore store = new DataStore(directory, quietLogger());
        store.data("warps").set("warps.spawn.x", 1);
        store.save("warps");
        store.data("warps").set("warps.spawn.x", 2);
        store.save("warps");

        Path warps = directory.toPath().resolve("warps.yml");
        assertFalse(Files.exists(directory.toPath().resolve("warps.yml.tmp")));
        YamlConfiguration current = new YamlConfiguration();
        current.load(warps.toFile());
        assertEquals(2, current.getInt("warps.spawn.x"));
        YamlConfiguration backup = new YamlConfiguration();
        backup.load(directory.toPath().resolve("warps.yml.bak").toFile());
        assertEquals(1, backup.getInt("warps.spawn.x"));
    }

    @Test
    public void missingFileStartsEmptyAndWritable() {
        DataStore store = new DataStore(folder.getRoot(), quietLogger());
        assertTrue(store.data("notes").getKeys(true).isEmpty());
        assertTrue(store.isWritable("notes"));
    }

    @Test
    public void dirtyFilesAreWrittenByFlushAndOnClose() throws Exception {
        File directory = folder.newFolder("data");
        DataStore store = new DataStore(directory, quietLogger());
        store.data("breeders").set("auto-breeders.a.food", 5);
        store.data("breeders").markDirty();
        assertTrue(store.isDirty("breeders"));
        assertFalse(Files.exists(directory.toPath().resolve("breeders.yml")));

        store.flushDirty();
        assertFalse(store.isDirty("breeders"));
        store.data("backpacks").set("players.x.contents", java.util.List.of());
        store.data("backpacks").markDirty();
        // close() must write what is still dirty and wait for the background write above.
        store.close();

        YamlConfiguration breeders = new YamlConfiguration();
        breeders.load(directory.toPath().resolve("breeders.yml").toFile());
        assertEquals(5, breeders.getInt("auto-breeders.a.food"));
        assertTrue(Files.exists(directory.toPath().resolve("backpacks.yml")));
    }

    @Test
    public void quarantineNamesTheCopyWithATimestamp() throws IOException {
        Path source = folder.newFile("daily.yml").toPath();
        Files.writeString(source, "x: [");
        String copy = DataStore.quarantine(source, LocalDateTime.of(2026, 10, 7, 14, 5, 9));
        assertEquals("daily.yml.corrupt-20261007-140509", copy);
        assertEquals("x: [", Files.readString(source.resolveSibling(copy)));
    }
}
