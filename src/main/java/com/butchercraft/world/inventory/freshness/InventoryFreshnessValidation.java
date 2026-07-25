package com.butchercraft.world.inventory.freshness;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class InventoryFreshnessValidation {
    private static final Pattern CANONICAL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern DIGEST = Pattern.compile("sha256:[0-9a-f]{64}");
    private static final String ZERO_DIGEST = "sha256:" + "0".repeat(64);

    private InventoryFreshnessValidation() {
    }

    static String id(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.strip();
        if (!CANONICAL_ID.matcher(normalized).matches() || !normalized.equals(value)) {
            throw new IllegalArgumentException(field + " must be a canonical namespaced id: " + value);
        }
        return normalized;
    }

    static String digest(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256 content identity: " + value);
        }
        return value;
    }

    static Optional<String> optionalDigest(Optional<String> value, String field) {
        Optional<String> digest = Objects.requireNonNull(value, field)
                .map(String::strip)
                .filter(candidate -> !candidate.isEmpty());
        digest.ifPresent(candidate -> digest(candidate, field));
        return digest;
    }

    static int positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive: " + value);
        }
        return value;
    }

    static long nonNegative(long value, String field) {
        if (value < 0L) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    static String zeroDigest() {
        return ZERO_DIGEST;
    }
}
