package com.butchercraft.world.evidence;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class EvidenceValidation {
    private static final Pattern CANONICAL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private EvidenceValidation() {
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

    static long nonNegative(long value, String field) {
        if (value < 0L) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    static int nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative: " + value);
        }
        return value;
    }

    static int positive(int value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive: " + value);
        }
        return value;
    }

    static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.strip());
    }

    static Optional<String> optionalContentIdentity(Optional<String> value, String field) {
        Optional<String> contentIdentity = Objects.requireNonNull(value, field)
                .map(String::strip)
                .filter(candidate -> !candidate.isEmpty());
        contentIdentity.ifPresent(candidate -> id(candidate, field));
        return contentIdentity;
    }
}
