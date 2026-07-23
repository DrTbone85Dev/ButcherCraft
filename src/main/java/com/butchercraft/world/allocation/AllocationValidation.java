package com.butchercraft.world.allocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.regex.Pattern;

final class AllocationValidation {
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    private AllocationValidation() {
    }

    static String id(String value, String field) {
        if (value == null) {
            throw failure(AllocationValidationFailureCode.NULL_VALUE, field, field + " is required");
        }
        String normalized = value.strip();
        if (!normalized.contains(":")) {
            throw failure(
                    AllocationValidationFailureCode.INVALID_NAMESPACE,
                    field,
                    field + " must contain a namespace"
            );
        }
        if (!IDENTIFIER.matcher(normalized).matches() || !normalized.equals(value)) {
            throw failure(
                    AllocationValidationFailureCode.MALFORMED_IDENTIFIER,
                    field,
                    field + " must be a canonical namespaced identifier: " + value
            );
        }
        return normalized;
    }

    static String text(String value, String field, int maximumLength) {
        if (value == null) {
            throw failure(AllocationValidationFailureCode.NULL_VALUE, field, field + " is required");
        }
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw failure(
                    AllocationValidationFailureCode.INVALID_METADATA,
                    field,
                    field + " must contain 1 to " + maximumLength + " characters"
            );
        }
        return normalized;
    }

    static long tick(long value, String field) {
        if (value < 0L) {
            throw failure(
                    AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                    field,
                    field + " must not be negative"
            );
        }
        return value;
    }

    static OptionalLong optionalTick(OptionalLong value, String field) {
        if (value == null) {
            throw failure(AllocationValidationFailureCode.NULL_VALUE, field, field + " is required");
        }
        value.ifPresent(tick -> tick(tick, field));
        return value;
    }

    static int schema(int value) {
        if (value != AllocationSchema.CURRENT_VERSION) {
            throw failure(
                    AllocationValidationFailureCode.INVALID_SCHEMA_VERSION,
                    "schemaVersion",
                    "Unsupported Allocation schema version: " + value
            );
        }
        return value;
    }

    static int precedence(int value, String field) {
        if (value < 0 || value > AllocationSchema.MAXIMUM_ORDERING_PRECEDENCE) {
            throw failure(
                    AllocationValidationFailureCode.INVALID_ORDERING_CONTEXT,
                    field,
                    field + " must be between 0 and " + AllocationSchema.MAXIMUM_ORDERING_PRECEDENCE
            );
        }
        return value;
    }

    static long sequence(long value) {
        if (value < 0L) {
            throw failure(
                    AllocationValidationFailureCode.INVALID_ORDERING_CONTEXT,
                    "stableRequestSequence",
                    "Stable request sequence must not be negative"
            );
        }
        return value;
    }

    static String derivedId(String type, String... components) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Objects.requireNonNull(type, "type").getBytes(StandardCharsets.UTF_8));
            for (String component : components) {
                digest.update((byte) 0);
                digest.update(Objects.requireNonNull(component, "identity component")
                        .getBytes(StandardCharsets.UTF_8));
            }
            return "butchercraft:" + type + "/id_"
                    + HexFormat.of().formatHex(digest.digest(), 0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static AllocationValidationException failure(
            AllocationValidationFailureCode code,
            String field,
            String message
    ) {
        return new AllocationValidationException(List.of(new AllocationValidationFailure(code, field, message)));
    }

    static void throwIfAny(List<AllocationValidationFailure> failures) {
        if (!failures.isEmpty()) {
            throw new AllocationValidationException(failures);
        }
    }

    static void add(
            List<AllocationValidationFailure> failures,
            AllocationValidationFailureCode code,
            String field,
            String message
    ) {
        failures.add(new AllocationValidationFailure(code, field, message));
    }

    static <T> T required(T value, String field, List<AllocationValidationFailure> failures) {
        if (value == null) {
            add(failures, AllocationValidationFailureCode.NULL_VALUE, field, field + " is required");
        }
        return value;
    }

    static <T> T required(T value, String field) {
        if (value == null) {
            throw failure(AllocationValidationFailureCode.NULL_VALUE, field, field + " is required");
        }
        return value;
    }

    static List<AllocationValidationFailure> failures() {
        return new ArrayList<>();
    }
}
