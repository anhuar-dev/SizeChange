package dev.anhuar.util;

public final class DurationUtil {

    public static final long INVALID = -1;

    private static final long SECOND_MS = 1000L;
    private static final long MINUTE_MS = 60L * SECOND_MS;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;

    private DurationUtil() {
    }

    /**
     * Parses a duration such as "30s", "5m", "2h" or "1d" into milliseconds.
     * Returns {@link #INVALID} for anything that is not a strictly positive,
     * representable duration.
     */
    public static long parse(String duration) {
        if (duration == null || duration.length() < 2) {
            return INVALID;
        }

        char unit = Character.toLowerCase(duration.charAt(duration.length() - 1));
        long unitMillis = switch (unit) {
            case 's' -> SECOND_MS;
            case 'm' -> MINUTE_MS;
            case 'h' -> HOUR_MS;
            case 'd' -> DAY_MS;
            default -> INVALID;
        };

        if (unitMillis == INVALID) {
            return INVALID;
        }

        long amount;
        try {
            amount = Long.parseLong(duration.substring(0, duration.length() - 1));
        } catch (NumberFormatException e) {
            return INVALID;
        }

        if (amount <= 0) {
            return INVALID;
        }

        // IMPORTANT: multiplyExact rather than a plain multiplication. A value like
        // "999999999999d" parses fine as a long but overflows once scaled to
        // milliseconds, wrapping to a negative or bogus positive number. A bogus
        // positive would reach Bukkit's scheduler as an absurd tick delay instead of
        // being rejected as invalid input.
        try {
            return Math.multiplyExact(amount, unitMillis);
        } catch (ArithmeticException e) {
            return INVALID;
        }
    }

    /**
     * Formats a millisecond duration into a short human-readable string,
     * showing at most the two most significant units.
     */
    public static String format(long millis) {
        if (millis < 0) {
            millis = 0;
        }

        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}
