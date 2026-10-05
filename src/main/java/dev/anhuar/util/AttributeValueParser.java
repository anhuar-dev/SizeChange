package dev.anhuar.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

// Parses the single amount token a staff member types for
// /sizechange attribute ... set <player> <attribute> <amount>. Kept free of any
// Bukkit import (like DurationUtil) so it can be unit-tested directly.
//
// Accepted forms — deliberately no operation enum for the user to learn:
//   "4"     "-2.5"   -> flat add           (AttributeModifier.Operation.ADD_NUMBER)
//   "20%"            -> +20% of the base   (AttributeModifier.Operation.ADD_SCALAR)
//   "1.5x"          -> multiply total x1.5 (AttributeModifier.Operation.MULTIPLY_SCALAR_1)
//
// The returned amount is already normalised for its operation:
//   "20%"  -> amount 0.20 with ADD_SCALAR
//   "1.5x" -> amount 0.50 with MULTIPLY_SCALAR_1 (final = base * (1 + 0.5))
public final class AttributeValueParser {

    public static final String OP_ADD_NUMBER = "ADD_NUMBER";
    public static final String OP_ADD_SCALAR = "ADD_SCALAR";
    public static final String OP_MULTIPLY = "MULTIPLY_SCALAR_1";

    public record Result(double amount, String operation) {
    }

    private AttributeValueParser() {
    }

    // Returns null for anything that is not one of the accepted forms.
    public static Result parse(String token) {
        if (token == null) {
            return null;
        }

        String trimmed = token.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }

        String operation = OP_ADD_NUMBER;
        String number = trimmed;
        double factor = 1.0;

        if (trimmed.endsWith("%")) {
            operation = OP_ADD_SCALAR;
            number = trimmed.substring(0, trimmed.length() - 1);
            factor = 0.01;
        } else if (trimmed.endsWith("x")) {
            operation = OP_MULTIPLY;
            number = trimmed.substring(0, trimmed.length() - 1);
        }

        double value;
        try {
            value = Double.parseDouble(number);
        } catch (NumberFormatException e) {
            return null;
        }

        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }

        if (operation.equals(OP_MULTIPLY)) {
            // "1.5x" means multiply the total by 1.5. MULTIPLY_SCALAR_1 computes
            // value * (1 + amount), so store the delta. "0x" (multiply by zero)
            // is a legitimate way to zero an attribute -> amount -1.
            value = value - 1.0;
        } else {
            value = value * factor;
        }

        return new Result(value, operation);
    }

    // Human-readable inverse, for confirmation messages and the view command.
    public static String format(double amount, String operation) {
        return switch (operation) {
            case OP_ADD_SCALAR -> trimZeros(amount * 100.0) + "%";
            case OP_MULTIPLY -> "x" + trimZeros(amount + 1.0);
            default -> (amount >= 0 ? "+" : "") + trimZeros(amount);
        };
    }

    private static String trimZeros(double value) {
        if (Math.abs(value - Math.rint(value)) < 1e-6) {
            return String.valueOf(Math.round(value));
        }
        return BigDecimal.valueOf(value)
                .setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
