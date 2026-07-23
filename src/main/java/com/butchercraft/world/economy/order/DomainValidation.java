package com.butchercraft.world.economy.order;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class DomainValidation {
    private static final String TOKEN = "[a-z][a-z0-9_]*";
    private static final Pattern ID_PATTERN = Pattern.compile(
            TOKEN + "(?::" + TOKEN + "(?:/" + TOKEN + ")*)?(?:/" + TOKEN + ")*"
    );

    private DomainValidation() {
    }

    static String requireId(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (!ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be lowercase words with an optional namespace: " + value);
        }
        return normalized;
    }

    static String requireText(String value, String label, int maximumLength) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(label + " exceeds " + maximumLength + " characters");
        }
        return normalized;
    }

    static Optional<String> optionalText(Optional<String> value, String label, int maximumLength) {
        return Objects.requireNonNull(value, label).map(text -> requireText(text, label, maximumLength));
    }

    static long requireTick(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " must not be negative: " + value);
        }
        return value;
    }

    static int requireSchema(int schemaVersion, String label) {
        if (schemaVersion != OrderContractSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported " + label + " schema version: " + schemaVersion);
        }
        return schemaVersion;
    }
}
