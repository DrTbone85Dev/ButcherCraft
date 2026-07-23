package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.Objects;

public record AllocationValidationFailure(
        AllocationValidationFailureCode code,
        String field,
        String message
) implements Comparable<AllocationValidationFailure> {
    private static final Comparator<AllocationValidationFailure> ORDER = Comparator
            .comparing(AllocationValidationFailure::code)
            .thenComparing(AllocationValidationFailure::field)
            .thenComparing(AllocationValidationFailure::message);

    public AllocationValidationFailure {
        Objects.requireNonNull(code, "code");
        field = requireText(field, "field");
        message = requireText(message, "message");
    }

    @Override
    public int compareTo(AllocationValidationFailure other) {
        return ORDER.compare(this, Objects.requireNonNull(other, "other"));
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return normalized;
    }
}
