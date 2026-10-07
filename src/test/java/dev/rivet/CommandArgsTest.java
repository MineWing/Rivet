package dev.rivet;

import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CommandArgsTest {
    @Test
    public void hasFlagIsCaseInsensitiveAndOrderIndependent() {
        assertTrue(CommandArgs.hasFlag(new String[]{"player", "-S"}, "-s"));
        assertTrue(CommandArgs.hasFlag(new String[]{"-s", "player"}, "-s"));
        assertFalse(CommandArgs.hasFlag(new String[]{"player", "reason"}, "-s"));
        assertFalse(CommandArgs.hasFlag(new String[0], "-s"));
    }

    @Test
    public void withoutFlagStripsEveryMatchAndKeepsOrder() {
        assertEquals(List.of("player", "reason"),
            CommandArgs.withoutFlag(new String[]{"player", "-s", "reason", "-S"}, "-s"));
        assertEquals(List.of("player"), CommandArgs.withoutFlag(new String[]{"player"}, "-s"));
    }

    @Test
    public void parsesCombinedDurationSegments() {
        assertEquals(Optional.of(Duration.ofDays(1).plusHours(12)), CommandArgs.parseDuration("1d12h"));
        assertEquals(Optional.of(Duration.ofMinutes(45)), CommandArgs.parseDuration("45m"));
        assertEquals(Optional.of(Duration.ofDays(14)), CommandArgs.parseDuration("2w"));
        assertEquals(Optional.of(Duration.ofSeconds(30)), CommandArgs.parseDuration("30s"));
    }

    @Test
    public void rejectsInvalidDurations() {
        assertEquals(Optional.empty(), CommandArgs.parseDuration(""));
        assertEquals(Optional.empty(), CommandArgs.parseDuration(null));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("0m"));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("abc"));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("1d x2h"));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("1x"));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("999999999999999999999d"));
    }

    @Test
    public void capsDurationsSoMillisecondExpiriesCannotOverflow() {
        assertEquals(Optional.of(CommandArgs.MAX_DURATION), CommandArgs.parseDuration("36525d"));
        assertEquals(Optional.of(CommandArgs.MAX_DURATION),
            CommandArgs.parseDuration(CommandArgs.MAX_DURATION.getSeconds() + "s"));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("36525d1s"));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("999999999999d"));
        assertEquals(Optional.empty(), CommandArgs.parseDuration("9999999999999w"));
        // The largest accepted value still converts to milliseconds and an epoch expiry safely.
        long expiry = Math.addExact(System.currentTimeMillis(), CommandArgs.MAX_DURATION.toMillis());
        assertTrue(expiry > System.currentTimeMillis());
    }
}
