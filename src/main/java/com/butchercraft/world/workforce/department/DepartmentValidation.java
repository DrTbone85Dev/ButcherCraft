package com.butchercraft.world.workforce.department;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

final class DepartmentValidation {
    private static final Pattern ID = Pattern.compile("^[a-z0-9_]+$");
    private static final Pattern IDENTITY = Pattern.compile("[a-z0-9_:/.-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");

    private DepartmentValidation() {
    }

    static String requireId(String value, String label) {
        String normalized = requireText(value, label).toLowerCase(Locale.ROOT);
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Department " + label + " must use lowercase snake case: " + normalized);
        }
        return normalized;
    }

    static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Department " + label + " must not be blank");
        }
        return normalized;
    }

    static String requireIdentity(String value, String label) {
        String normalized = requireText(value, label);
        if (!IDENTITY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Department " + label + " must be canonical: " + normalized);
        }
        return normalized;
    }

    static String requireDigest(String value, String label) {
        String normalized = requireText(value, label);
        if (!DIGEST.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Department " + label + " must be a lowercase SHA-256 digest");
        }
        return normalized;
    }
}
