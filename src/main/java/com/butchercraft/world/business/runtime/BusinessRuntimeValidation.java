package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessDayOfWeek;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class BusinessRuntimeValidation {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_:/.-]+$");
    private static final Pattern VALID_SHIFT_ID = Pattern.compile("^[a-z0-9_]+$");

    private BusinessRuntimeValidation() {
    }

    static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    static String requireExternalIdentity(String value, String fieldName) {
        String normalized = requireText(value, fieldName).toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must be a stable lowercase identity: " + value);
        }
        return normalized;
    }

    static String requireShiftId(String value) {
        String normalized = requireText(value, "Business shift id").toLowerCase(Locale.ROOT);
        if (!VALID_SHIFT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Business shift id must use lowercase snake case: " + value);
        }
        return normalized;
    }

    static BusinessDayOfWeek dayFromConfig(String value) {
        String normalized = requireText(value, "Business weekday")
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return BusinessDayOfWeek.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown business weekday: " + value, exception);
        }
    }
}
