package com.butchercraft.workstation.endpoint;

import java.util.Objects;
import java.util.regex.Pattern;

final class WorkstationEndpointValidation {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private WorkstationEndpointValidation() {
    }

    static int schema(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static long nonNegative(long value, String field) {
        if (value < 0L) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    static long positive(long value, String field) {
        if (value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static int positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    static String id(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be canonical: " + value);
        }
        return value;
    }

    static String digest(String value, String field) {
        Objects.requireNonNull(value, field);
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 digest");
        }
        return value;
    }

    static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
