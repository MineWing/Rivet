package dev.rivet;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, shared command-argument parsing helpers used across several command handlers. */
final class CommandArgs {
    private static final Pattern DURATION_SEGMENT = Pattern.compile("(\\d+)([wdhms])", Pattern.CASE_INSENSITIVE);
    /**
     * Longest accepted duration (100 years). Callers convert to milliseconds and add the result to
     * {@link System#currentTimeMillis()}, so this cap keeps both steps far from {@code long} overflow.
     */
    static final Duration MAX_DURATION = Duration.ofDays(36_525);

    private CommandArgs() {
    }

    static boolean hasFlag(String[] args, String flag) {
        for (String argument : args) {
            if (argument.equalsIgnoreCase(flag)) {
                return true;
            }
        }
        return false;
    }

    static List<String> withoutFlag(String[] args, String flag) {
        List<String> values = new ArrayList<>();
        for (String argument : args) {
            if (!argument.equalsIgnoreCase(flag)) {
                values.add(argument);
            }
        }
        return values;
    }

    /**
     * Parses combinable durations such as {@code 1d12h}, {@code 45m}, or {@code 2w}. Durations longer
     * than {@link #MAX_DURATION} are rejected.
     */
    static Optional<Duration> parseDuration(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DURATION_SEGMENT.matcher(value);
        long totalSeconds = 0;
        int consumed = 0;
        try {
            while (matcher.find(consumed)) {
                if (matcher.start() != consumed) {
                    return Optional.empty();
                }
                totalSeconds = Math.addExact(totalSeconds,
                    Math.multiplyExact(Long.parseLong(matcher.group(1)), unitSeconds(matcher.group(2))));
                consumed = matcher.end();
            }
        } catch (NumberFormatException | ArithmeticException overflow) {
            return Optional.empty();
        }
        if (consumed != value.length() || totalSeconds <= 0
            || totalSeconds > MAX_DURATION.getSeconds()) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(totalSeconds));
    }

    private static long unitSeconds(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "w" -> 604_800L;
            case "d" -> 86_400L;
            case "h" -> 3_600L;
            case "m" -> 60L;
            case "s" -> 1L;
            default -> throw new IllegalStateException("Unexpected duration unit: " + unit);
        };
    }
}
