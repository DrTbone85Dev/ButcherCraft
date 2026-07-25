package com.butchercraft.world.execution;

import java.util.Objects;
import java.util.regex.Pattern;

final class ExecutionValidation {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final String ZERO_DIGEST = "sha256:" + "0".repeat(64);

    private ExecutionValidation() {
    }

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

    static String requireDigest(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (!DIGEST.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be a canonical SHA-256 digest");
        }
        return normalized;
    }

    static String zeroDigest() {
        return ZERO_DIGEST;
    }

    static String digestIdSuffix(String digest) {
        return requireDigest(digest, "digest").substring("sha256:".length());
    }

    static long requireTick(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return value;
    }

    static int requireSchema(int value, String label) {
        if (value != ExecutionSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported " + label + " schema version: " + value);
        }
        return value;
    }

    static int requirePositive(int value, String label) {
        if (value <= 0) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }
}
