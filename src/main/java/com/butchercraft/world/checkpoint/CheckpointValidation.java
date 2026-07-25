package com.butchercraft.world.checkpoint;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class CheckpointValidation {
    private static final Pattern CANONICAL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final String ZERO_DIGEST = "sha256:" + "0".repeat(64);

    private CheckpointValidation() {
    }

    static String id(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (!isCanonicalId(normalized) || !normalized.equals(value)) {
            throw new IllegalArgumentException(field + " must be a canonical namespaced id: " + value);
        }
        return normalized;
    }

    static boolean isCanonicalId(String value) {
        return value != null && CANONICAL_ID.matcher(value).matches();
    }

    static String text(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    static String digest(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (!DIGEST.matcher(normalized).matches() || !normalized.equals(value)) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 content identity: " + value);
        }
        return normalized;
    }

    static Optional<String> optionalDigest(Optional<String> value, String field) {
        Optional<String> digest = Objects.requireNonNull(value, field)
                .map(String::strip)
                .filter(candidate -> !candidate.isEmpty());
        digest.ifPresent(candidate -> digest(candidate, field));
        return digest;
    }

    static long nonNegative(long value, String field) {
        if (value < 0L) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    static long positive(long value, String field) {
        if (value <= 0L) {
            throw new IllegalArgumentException(field + " must be positive: " + value);
        }
        return value;
    }

    static int positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive: " + value);
        }
        return value;
    }

    static String zeroDigest() {
        return ZERO_DIGEST;
    }
}
