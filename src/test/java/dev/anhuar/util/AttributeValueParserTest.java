package dev.anhuar.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttributeValueParserTest {

    @Nested
    @DisplayName("parse")
    class Parse {

        @Test
        @DisplayName("a bare number is a flat add")
        void bareNumberIsFlat() {
            AttributeValueParser.Result result = AttributeValueParser.parse("4");
            assertEquals(AttributeValueParser.OP_ADD_NUMBER, result.operation());
            assertEquals(4.0, result.amount());
        }

        @Test
        @DisplayName("a negative bare number is kept negative")
        void negativeBareNumber() {
            AttributeValueParser.Result result = AttributeValueParser.parse("-2.5");
            assertEquals(AttributeValueParser.OP_ADD_NUMBER, result.operation());
            assertEquals(-2.5, result.amount());
        }

        @Test
        @DisplayName("a percentage is a fraction of the base")
        void percentageIsScalar() {
            AttributeValueParser.Result result = AttributeValueParser.parse("20%");
            assertEquals(AttributeValueParser.OP_ADD_SCALAR, result.operation());
            assertEquals(0.20, result.amount(), 1e-9);
        }

        @Test
        @DisplayName("a multiplier stores the delta over 1")
        void multiplierStoresDelta() {
            AttributeValueParser.Result result = AttributeValueParser.parse("1.5x");
            assertEquals(AttributeValueParser.OP_MULTIPLY, result.operation());
            // MULTIPLY_SCALAR_1 computes value * (1 + amount) -> x1.5 means amount 0.5.
            assertEquals(0.5, result.amount(), 1e-9);
        }

        @Test
        @DisplayName("multiplying by zero is a valid way to zero an attribute")
        void multiplyByZero() {
            AttributeValueParser.Result result = AttributeValueParser.parse("0x");
            assertEquals(AttributeValueParser.OP_MULTIPLY, result.operation());
            assertEquals(-1.0, result.amount(), 1e-9);
        }

        @Test
        @DisplayName("is case-insensitive about the suffix")
        void caseInsensitiveSuffix() {
            assertEquals(AttributeValueParser.OP_MULTIPLY, AttributeValueParser.parse("2X").operation());
        }

        @Test
        @DisplayName("rejects null, blank, non-numeric and suffix-only input")
        void rejectsMalformed() {
            assertNull(AttributeValueParser.parse(null));
            assertNull(AttributeValueParser.parse(""));
            assertNull(AttributeValueParser.parse("   "));
            assertNull(AttributeValueParser.parse("abc"));
            assertNull(AttributeValueParser.parse("%"));
            assertNull(AttributeValueParser.parse("x"));
            assertNull(AttributeValueParser.parse("10%%"));
        }

        @Test
        @DisplayName("rejects NaN and infinity")
        void rejectsNonFinite() {
            assertNull(AttributeValueParser.parse("NaN"));
            assertNull(AttributeValueParser.parse("Infinity"));
        }
    }

    @Nested
    @DisplayName("format")
    class Format {

        @Test
        @DisplayName("round-trips each form")
        void roundTrips() {
            assertEquals("+4", AttributeValueParser.format(4.0, AttributeValueParser.OP_ADD_NUMBER));
            assertEquals("-2", AttributeValueParser.format(-2.0, AttributeValueParser.OP_ADD_NUMBER));
            assertEquals("20%", AttributeValueParser.format(0.20, AttributeValueParser.OP_ADD_SCALAR));
            assertEquals("x1.5", AttributeValueParser.format(0.5, AttributeValueParser.OP_MULTIPLY));
        }
    }
}
