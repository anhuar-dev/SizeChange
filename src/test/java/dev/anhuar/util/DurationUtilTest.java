package dev.anhuar.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DurationUtilTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("converts each supported unit to milliseconds")
        void convertsEachUnit() {
            assertEquals(30_000L, DurationUtil.parse("30s"));
            assertEquals(300_000L, DurationUtil.parse("5m"));
            assertEquals(7_200_000L, DurationUtil.parse("2h"));
            assertEquals(86_400_000L, DurationUtil.parse("1d"));
        }

        @Test
        @DisplayName("accepts uppercase units")
        void acceptsUppercaseUnits() {
            assertEquals(30_000L, DurationUtil.parse("30S"));
            assertEquals(86_400_000L, DurationUtil.parse("1D"));
        }

        @Test
        @DisplayName("rejects an unknown unit")
        void rejectsUnknownUnit() {
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("10x"));
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("10y"));
        }

        @Test
        @DisplayName("rejects a non-numeric amount")
        void rejectsNonNumericAmount() {
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("abcs"));
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("1.5m"));
        }

        @Test
        @DisplayName("rejects null, empty and unit-only input")
        void rejectsMalformedInput() {
            assertEquals(DurationUtil.INVALID, DurationUtil.parse(null));
            assertEquals(DurationUtil.INVALID, DurationUtil.parse(""));
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("s"));
        }

        @Test
        @DisplayName("rejects zero and negative amounts")
        void rejectsNonPositiveAmounts() {
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("0s"));
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("-5m"));
        }

        @Test
        @DisplayName("rejects amounts that overflow into a negative duration")
        void rejectsOverflowIntoNegative() {
            // These parse fine as longs but overflow once scaled to milliseconds,
            // wrapping to a negative value.
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("999999999999d"));
            assertEquals(DurationUtil.INVALID, DurationUtil.parse(Long.MAX_VALUE + "d"));
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("9223372036854776s"));
        }

        @Test
        @DisplayName("rejects an amount that overflows into a plausible positive duration")
        void rejectsOverflowIntoPositive() {
            // Regression test for the real hazard. 18446744073709552 * 1000 wraps to
            // exactly 384, so before the overflow guard this input silently produced
            // a 384-millisecond temporary size instead of being rejected — a caller
            // sanity-checking only for "duration <= 0" would never have caught it.
            assertEquals(DurationUtil.INVALID, DurationUtil.parse("18446744073709552s"));
        }
    }

    @Nested
    @DisplayName("format")
    class Format {

        @Test
        @DisplayName("shows seconds only under a minute")
        void showsSecondsOnly() {
            assertEquals("0s", DurationUtil.format(0));
            assertEquals("45s", DurationUtil.format(45_000));
        }

        @Test
        @DisplayName("shows minutes and seconds under an hour")
        void showsMinutesAndSeconds() {
            assertEquals("1m 0s", DurationUtil.format(60_000));
            assertEquals("5m 30s", DurationUtil.format(330_000));
        }

        @Test
        @DisplayName("shows hours and minutes under a day")
        void showsHoursAndMinutes() {
            assertEquals("1h 0m", DurationUtil.format(3_600_000));
            assertEquals("2h 30m", DurationUtil.format(9_000_000));
        }

        @Test
        @DisplayName("shows days and hours beyond a day")
        void showsDaysAndHours() {
            assertEquals("1d 0h", DurationUtil.format(86_400_000));
            assertEquals("2d 3h", DurationUtil.format(183_600_000));
        }

        @Test
        @DisplayName("clamps negative input to zero")
        void clampsNegativeInput() {
            assertEquals("0s", DurationUtil.format(-1));
            assertEquals("0s", DurationUtil.format(-500_000));
        }

        @Test
        @DisplayName("round-trips a parsed duration")
        void roundTripsParsedDuration() {
            assertEquals("5m 0s", DurationUtil.format(DurationUtil.parse("5m")));
            assertEquals("2h 0m", DurationUtil.format(DurationUtil.parse("2h")));
        }
    }
}
