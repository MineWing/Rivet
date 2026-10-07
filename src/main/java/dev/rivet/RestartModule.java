package dev.rivet;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class RestartModule implements RivetModule {
    private final RivetPlugin plugin;
    private final YamlConfiguration settings;
    private final List<BukkitTask> tasks = new ArrayList<>();

    private List<ScheduleEntry> schedules = List.of();
    private List<Integer> warningSeconds = List.of();
    private List<TimedCommand> commands = List.of();
    private Map<DayOfWeek, List<TimedCommand>> dayCommands = Map.of();
    private String restartCommand = "stop";
    private boolean delayEnabled;
    private int delayThreshold;
    private long delayCheckIntervalSeconds;
    private long delayMaxSeconds;

    private long nextRestartAtMillis;
    private long autoDelayedSecondsSoFar;

    RestartModule(RivetPlugin plugin) {
        this.plugin = plugin;
        settings = plugin.settings("restart");
        reload();
    }

    @Override
    public void reload() {
        cancelTasks();
        schedules = parseSchedules(settings.getStringList("schedules"));
        warningSeconds = warningSeconds(settings.getIntegerList("warning-seconds"));
        commands = parseCommands(settings.getMapList("commands"));
        dayCommands = parseDayCommands(settings.getConfigurationSection("day-commands"));
        restartCommand = settings.getString("restart-command", "stop");
        delayEnabled = settings.getBoolean("delay.enabled", true);
        delayThreshold = Math.max(0, settings.getInt("delay.player-threshold", 1));
        delayCheckIntervalSeconds = Math.max(1, settings.getLong("delay.check-interval-seconds", 60));
        delayMaxSeconds = Math.max(0, settings.getLong("delay.max-delay-seconds", 1800));
        autoDelayedSecondsSoFar = 0;
        nextRestartAtMillis = nextRestart(schedules, ZonedDateTime.now())
            .map(scheduled -> scheduled.toInstant().toEpochMilli()).orElse(0L);
        scheduleUpcoming();
    }

    @Override
    public void shutdown() {
        cancelTasks();
    }

    boolean command(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("check")) {
            check(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("delay")) {
            delay(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        if (args[0].equalsIgnoreCase("cancel")) {
            cancel(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }
        message(sender, "messages.usage", "<white>Usage: /restart <check|delay|cancel></white>");
        return true;
    }

    List<String> completions(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("check"));
        if (sender.hasPermission("rivet.restart.manage")) {
            options.add("delay");
            options.add("cancel");
        }
        return options;
    }

    private void check(CommandSender sender) {
        if (nextRestartAtMillis == 0) {
            message(sender, "messages.none-scheduled", "<white>No restart is currently scheduled.</white>");
            return;
        }
        long remaining = LaggModule.secondsUntil(nextRestartAtMillis, System.currentTimeMillis());
        plugin.messageActions().run(sender, settings, "messages.status", "message",
            "<white>Next restart in <#f72a4c>%time%</#f72a4c>.</white>",
            Placeholder.unparsed("time", LaggModule.formatDuration(remaining)),
            Placeholder.unparsed("seconds", Long.toString(remaining)));
    }

    private void delay(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rivet.restart.manage")) {
            message(sender, "messages.no-permission",
                "<white>You do not have permission to manage restarts.</white>");
            return;
        }
        if (nextRestartAtMillis == 0) {
            message(sender, "messages.none-scheduled", "<white>No restart is currently scheduled.</white>");
            return;
        }
        if (args.length == 0) {
            message(sender, "messages.delay-usage",
                "<white>Usage: /restart delay <duration> [reason]</white>");
            return;
        }
        Optional<Duration> duration = CommandArgs.parseDuration(args[0]);
        if (duration.isEmpty()) {
            message(sender, "messages.invalid-duration",
                "<white>Duration must look like 30m, 1h, or 1d12h.</white>");
            return;
        }
        String reason = args.length > 1
            ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason given.";
        nextRestartAtMillis += duration.get().toSeconds() * 1_000;
        scheduleUpcoming();
        long remaining = LaggModule.secondsUntil(nextRestartAtMillis, System.currentTimeMillis());
        plugin.messageActions().run(plugin.getServer().getOnlinePlayers(), plugin.getServer().getOnlinePlayers(),
            settings, "messages.delayed", "broadcast",
            "<white>%staff% delayed the restart by %duration%: %reason%</white>",
            Placeholder.unparsed("staff", sender.getName()),
            Placeholder.unparsed("duration", LaggModule.formatDuration(duration.get().toSeconds())),
            Placeholder.unparsed("reason", reason),
            Placeholder.unparsed("time", LaggModule.formatDuration(remaining)));
    }

    private void cancel(CommandSender sender, String[] args) {
        if (!sender.hasPermission("rivet.restart.manage")) {
            message(sender, "messages.no-permission",
                "<white>You do not have permission to manage restarts.</white>");
            return;
        }
        if (nextRestartAtMillis == 0) {
            message(sender, "messages.none-scheduled", "<white>No restart is currently scheduled.</white>");
            return;
        }
        String reason = args.length > 0 ? String.join(" ", args) : "No reason given.";
        ZonedDateTime from = Instant.ofEpochMilli(nextRestartAtMillis).atZone(ZoneId.systemDefault());
        autoDelayedSecondsSoFar = 0;
        nextRestartAtMillis = nextRestart(schedules, from)
            .map(scheduled -> scheduled.toInstant().toEpochMilli()).orElse(0L);
        scheduleUpcoming();
        String time = nextRestartAtMillis == 0 ? "none scheduled"
            : LaggModule.formatDuration(LaggModule.secondsUntil(nextRestartAtMillis, System.currentTimeMillis()));
        plugin.messageActions().run(plugin.getServer().getOnlinePlayers(), plugin.getServer().getOnlinePlayers(),
            settings, "messages.cancelled", "broadcast",
            "<white>%staff% cancelled the next restart: %reason%</white>",
            Placeholder.unparsed("staff", sender.getName()),
            Placeholder.unparsed("reason", reason),
            Placeholder.unparsed("time", time));
    }

    private void message(CommandSender sender, String path, String fallback) {
        plugin.messageActions().run(sender, settings, path, "message", fallback);
    }

    private void scheduleUpcoming() {
        cancelTasks();
        if (nextRestartAtMillis == 0) {
            return;
        }
        long remaining = LaggModule.secondsUntil(nextRestartAtMillis, System.currentTimeMillis());
        DayOfWeek day = Instant.ofEpochMilli(nextRestartAtMillis).atZone(ZoneId.systemDefault()).getDayOfWeek();
        List<TimedCommand> due = new ArrayList<>(commands);
        due.addAll(dayCommands.getOrDefault(day, List.of()));

        for (int seconds : warningSeconds) {
            if (seconds > remaining) {
                continue;
            }
            long delayTicks = (remaining - seconds) * 20;
            tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> broadcastWarning(seconds), delayTicks));
        }
        for (TimedCommand timed : due) {
            long delayTicks;
            if (timed.offsetSeconds() <= 0) {
                long beforeSeconds = -timed.offsetSeconds();
                if (beforeSeconds > remaining) {
                    continue;
                }
                delayTicks = (remaining - beforeSeconds) * 20;
            } else {
                delayTicks = (remaining + timed.offsetSeconds()) * 20;
            }
            tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> dispatch(timed.command()), delayTicks));
        }
        tasks.add(plugin.getServer().getScheduler().runTaskLater(plugin, this::attemptRestart, remaining * 20));
    }

    private void attemptRestart() {
        if (shouldDelayForPlayers()) {
            applyAutoDelay();
            return;
        }
        restartNowAndAdvance();
    }

    private void restartNowAndAdvance() {
        dispatch(restartCommand);
        ZonedDateTime from = Instant.ofEpochMilli(nextRestartAtMillis).atZone(ZoneId.systemDefault());
        autoDelayedSecondsSoFar = 0;
        nextRestartAtMillis = nextRestart(schedules, from)
            .map(scheduled -> scheduled.toInstant().toEpochMilli()).orElse(0L);
        scheduleUpcoming();
    }

    private boolean shouldDelayForPlayers() {
        if (!delayEnabled || autoDelayedSecondsSoFar >= delayMaxSeconds) {
            return false;
        }
        return plugin.getServer().getOnlinePlayers().size() >= delayThreshold;
    }

    private void applyAutoDelay() {
        long increment = Math.min(delayCheckIntervalSeconds, delayMaxSeconds - autoDelayedSecondsSoFar);
        if (increment <= 0) {
            restartNowAndAdvance();
            return;
        }
        autoDelayedSecondsSoFar += increment;
        nextRestartAtMillis += increment * 1_000;
        long remaining = LaggModule.secondsUntil(nextRestartAtMillis, System.currentTimeMillis());
        plugin.messageActions().run(plugin.getServer().getOnlinePlayers(), plugin.getServer().getOnlinePlayers(),
            settings, "messages.delayed-for-players", "broadcast",
            "<white>The restart has been delayed because players are online. "
                + "Next attempt in <#f72a4c>%time%</#f72a4c>.</white>",
            Placeholder.unparsed("time", LaggModule.formatDuration(remaining)));
        scheduleUpcoming();
    }

    private void broadcastWarning(long seconds) {
        plugin.messageActions().run(plugin.getServer().getOnlinePlayers(), plugin.getServer().getOnlinePlayers(),
            settings, "messages.warning", "broadcast",
            "<white>Restarting in <#f72a4c>%time%</#f72a4c>.</white>",
            Placeholder.unparsed("seconds", Long.toString(seconds)),
            Placeholder.unparsed("time", LaggModule.formatDuration(seconds)),
            Placeholder.unparsed("plural", seconds == 1 ? "" : "s"));
    }

    private void dispatch(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), normalized);
    }

    private void cancelTasks() {
        tasks.forEach(BukkitTask::cancel);
        tasks.clear();
    }

    static Optional<ScheduleEntry> parseSchedule(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String[] parts = value.split(";");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String dayPart = parts[0].trim();
        DayOfWeek day = null;
        if (!dayPart.equalsIgnoreCase("daily")) {
            try {
                day = DayOfWeek.valueOf(dayPart.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
        int hour;
        int minute;
        try {
            hour = Integer.parseInt(parts[1].trim());
            minute = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return Optional.empty();
        }
        return Optional.of(new ScheduleEntry(day, hour, minute));
    }

    static List<ScheduleEntry> parseSchedules(List<String> configured) {
        return configured.stream().map(RestartModule::parseSchedule)
            .filter(Optional::isPresent).map(Optional::get).toList();
    }

    static ZonedDateTime nextOccurrence(ScheduleEntry entry, ZonedDateTime from) {
        ZonedDateTime candidate = from.withHour(entry.hour()).withMinute(entry.minute())
            .withSecond(0).withNano(0);
        while (!candidate.isAfter(from) || (entry.day() != null && candidate.getDayOfWeek() != entry.day())) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    static Optional<ZonedDateTime> nextRestart(List<ScheduleEntry> schedules, ZonedDateTime from) {
        return schedules.stream().map(entry -> nextOccurrence(entry, from)).min(Comparator.naturalOrder());
    }

    static List<Integer> warningSeconds(List<Integer> configured) {
        return configured.stream().filter(seconds -> seconds != null && seconds >= 0)
            .distinct().sorted(Comparator.reverseOrder()).toList();
    }

    static List<TimedCommand> parseCommands(List<Map<?, ?>> configured) {
        List<TimedCommand> parsed = new ArrayList<>();
        for (Map<?, ?> entry : configured) {
            if (entry.get("offset-seconds") instanceof Number offset
                && entry.get("command") instanceof String command && !command.isBlank()) {
                parsed.add(new TimedCommand(offset.longValue(), command));
            }
        }
        return List.copyOf(parsed);
    }

    static Map<DayOfWeek, List<TimedCommand>> parseDayCommands(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<DayOfWeek, List<TimedCommand>> result = new EnumMap<>(DayOfWeek.class);
        for (String key : section.getKeys(false)) {
            try {
                DayOfWeek day = DayOfWeek.valueOf(key.trim().toUpperCase(Locale.ROOT));
                result.put(day, parseCommands(section.getMapList(key)));
            } catch (IllegalArgumentException exception) {
                // Unrecognised day names are skipped rather than failing the whole reload.
            }
        }
        return Map.copyOf(result);
    }

    record ScheduleEntry(DayOfWeek day, int hour, int minute) {
    }

    record TimedCommand(long offsetSeconds, String command) {
    }
}
