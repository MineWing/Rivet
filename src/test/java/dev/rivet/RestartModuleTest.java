package dev.rivet;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public final class RestartModuleTest {
    @Test
    public void packagesSafeDefaultsAndConfigurableMessages() {
        var resource = getClass().getResourceAsStream("/settings/restart.yml");
        assertNotNull(resource);
        YamlConfiguration settings = YamlConfiguration.loadConfiguration(
            new InputStreamReader(resource, StandardCharsets.UTF_8));

        assertEquals(List.of(), settings.getStringList("schedules"));
        assertEquals("stop", settings.getString("restart-command"));
        assertEquals(true, settings.getBoolean("delay.enabled"));
        assertEquals(1, settings.getInt("delay.player-threshold"));
        assertEquals(60, settings.getLong("delay.check-interval-seconds"));
        assertEquals(1800, settings.getLong("delay.max-delay-seconds"));
        assertEquals(List.of(1800, 600, 300, 60, 30, 10, 5, 0), settings.getIntegerList("warning-seconds"));
        assertEquals(-300, settings.getMapList("commands").getFirst().get("offset-seconds"));
        assertEquals("save-all", settings.getMapList("commands").getFirst().get("command"));
        List.of("messages.warning.actions", "messages.status.actions", "messages.none-scheduled.actions",
            "messages.delayed-for-players.actions", "messages.delayed.actions", "messages.cancelled.actions",
            "messages.usage.actions", "messages.delay-usage.actions", "messages.invalid-duration.actions",
            "messages.no-permission.actions")
            .forEach(path -> assertEquals(path, true, settings.isList(path)));
    }

    @Test
    public void parsesValidScheduleEntriesAndRejectsMalformedOnes() {
        assertEquals(Optional.of(new RestartModule.ScheduleEntry(null, 4, 0)),
            RestartModule.parseSchedule("Daily;04;00"));
        assertEquals(Optional.of(new RestartModule.ScheduleEntry(DayOfWeek.MONDAY, 23, 0)),
            RestartModule.parseSchedule("Monday;23;00"));
        assertEquals(Optional.of(new RestartModule.ScheduleEntry(DayOfWeek.FRIDAY, 2, 0)),
            RestartModule.parseSchedule(" friday ; 2 ; 0 "));
        assertEquals(Optional.empty(), RestartModule.parseSchedule("Someday;04;00"));
        assertEquals(Optional.empty(), RestartModule.parseSchedule("Daily;24;00"));
        assertEquals(Optional.empty(), RestartModule.parseSchedule("Daily;04;60"));
        assertEquals(Optional.empty(), RestartModule.parseSchedule("Daily;04"));
        assertEquals(Optional.empty(), RestartModule.parseSchedule(null));
        assertEquals(List.of(new RestartModule.ScheduleEntry(null, 4, 0)),
            RestartModule.parseSchedules(List.of("Daily;04;00", "Invalid;01;00")));
    }

    @Test
    public void computesTheNextOccurrenceForDailyAndWeeklySchedules() {
        ZonedDateTime monday10 = ZonedDateTime.of(2026, 8, 24, 10, 0, 0, 0, ZoneOffset.UTC);
        assertEquals(ZonedDateTime.of(2026, 8, 25, 9, 0, 0, 0, ZoneOffset.UTC),
            RestartModule.nextOccurrence(new RestartModule.ScheduleEntry(null, 9, 0), monday10));
        assertEquals(ZonedDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneOffset.UTC),
            RestartModule.nextOccurrence(new RestartModule.ScheduleEntry(null, 12, 0), monday10));
        assertEquals(ZonedDateTime.of(2026, 8, 28, 2, 0, 0, 0, ZoneOffset.UTC),
            RestartModule.nextOccurrence(new RestartModule.ScheduleEntry(DayOfWeek.FRIDAY, 2, 0), monday10));
        assertEquals(ZonedDateTime.of(2026, 8, 31, 9, 0, 0, 0, ZoneOffset.UTC),
            RestartModule.nextOccurrence(new RestartModule.ScheduleEntry(DayOfWeek.MONDAY, 9, 0), monday10));

        assertEquals(Optional.of(ZonedDateTime.of(2026, 8, 24, 12, 0, 0, 0, ZoneOffset.UTC)),
            RestartModule.nextRestart(List.of(new RestartModule.ScheduleEntry(null, 12, 0),
                new RestartModule.ScheduleEntry(DayOfWeek.FRIDAY, 2, 0)), monday10));
        assertEquals(Optional.empty(), RestartModule.nextRestart(List.of(), monday10));
    }

    @Test
    public void schedulesOnlyValidDistinctDescendingWarnings() {
        assertEquals(List.of(60, 30, 10, 0), RestartModule.warningSeconds(List.of(10, 60, 0, 30, 60, -5)));
        assertEquals(List.of(), RestartModule.warningSeconds(List.of(-5, -1)));
    }

    @Test
    public void parsesTimedCommandsFromMapListsAndSkipsMalformedEntries() {
        YamlConfiguration settings = new YamlConfiguration();
        settings.set("commands", List.of(
            Map.of("offset-seconds", -300, "command", "save-all"),
            Map.of("offset-seconds", 10, "command", "say back up"),
            Map.of("command", "missing-offset"),
            Map.of("offset-seconds", 5)));
        assertEquals(List.of(new RestartModule.TimedCommand(-300, "save-all"),
                new RestartModule.TimedCommand(10, "say back up")),
            RestartModule.parseCommands(settings.getMapList("commands")));

        settings.set("day-commands.Friday", List.of(Map.of("offset-seconds", -600, "command", "say weekly")));
        settings.set("day-commands.NotADay", List.of(Map.of("offset-seconds", 0, "command", "ignored")));
        Map<DayOfWeek, List<RestartModule.TimedCommand>> dayCommands =
            RestartModule.parseDayCommands(settings.getConfigurationSection("day-commands"));
        assertEquals(List.of(new RestartModule.TimedCommand(-600, "say weekly")), dayCommands.get(DayOfWeek.FRIDAY));
        assertEquals(1, dayCommands.size());
        assertFalse(RestartModule.parseDayCommands(null).containsKey(DayOfWeek.FRIDAY));
    }
}
