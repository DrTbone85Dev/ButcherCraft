package com.butchercraft.workstation.reservation;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class WorkstationReservationValidation {
    private static final Pattern IDENTITY = Pattern.compile("[a-z0-9_:/.-]+");
    private static final Pattern TOKEN = Pattern.compile("[a-z][a-z0-9_]*");

    private WorkstationReservationValidation() {
    }

    static int requireSchema(int schemaVersion, String label) {
        if (schemaVersion != WorkstationReservationSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported " + label + " schema version: " + schemaVersion);
        }
        return schemaVersion;
    }

    static String requireIdentity(String value, String label) {
        String normalized = requireText(value, label, 512).toLowerCase(Locale.ROOT);
        if (!IDENTITY.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be canonical: " + value);
        }
        return normalized;
    }

    static String requireToken(String value, String label) {
        String normalized = requireText(value, label, 128).toLowerCase(Locale.ROOT);
        if (!TOKEN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be lowercase snake case: " + value);
        }
        return normalized;
    }

    static String requireText(String value, String label, int maximumLength) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " must contain 1 to " + maximumLength + " characters");
        }
        return normalized;
    }
}
