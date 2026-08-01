package com.butchercraft.world.workforce.employee;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class EmployeeValidation {
    private static final Pattern CANONICAL_ID = Pattern.compile("[a-z0-9_:/.-]+");
    private static final Pattern SHIFT_ID = Pattern.compile("^[a-z0-9_]+$");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private EmployeeValidation() {
    }

    static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Employee " + label + " must not be blank");
        }
        return normalized;
    }

    static String requireIdentity(String value, String label) {
        String normalized = requireText(value, label);
        if (!CANONICAL_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Employee " + label + " must be canonical: " + normalized);
        }
        return normalized;
    }

    static String requireDigest(String value, String label) {
        String normalized = requireText(value, label);
        if (!DIGEST.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Employee " + label + " must be a lowercase SHA-256 digest");
        }
        return normalized;
    }

    static String requireShiftId(String value, String label) {
        String normalized = requireText(value, label).toLowerCase(Locale.ROOT);
        if (!SHIFT_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Employee " + label + " must use lowercase snake case: " + normalized);
        }
        return normalized;
    }
}
