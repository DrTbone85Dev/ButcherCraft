package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;
import java.util.regex.Pattern;

final class SchedulerValidation {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private SchedulerValidation() { }

    static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be a canonical namespaced id: " + normalized);
        }
        return normalized;
    }

    static String requireText(String value, String label, int maximumLength) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " must contain 1-" + maximumLength + " characters");
        }
        return normalized;
    }

    static long requireTick(long value, String label) {
        if (value < 0L) throw new IllegalArgumentException(label + " must not be negative");
        return value;
    }

    static int requireSchema(int value, String label) {
        if (value != SchedulerSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported " + label + " schema version: " + value);
        }
        return value;
    }
}
