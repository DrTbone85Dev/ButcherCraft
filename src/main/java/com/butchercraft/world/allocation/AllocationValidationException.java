package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Objects;

public final class AllocationValidationException extends IllegalArgumentException {
    private final List<AllocationValidationFailure> failures;

    public AllocationValidationException(List<AllocationValidationFailure> failures) {
        super(message(failures));
        List<AllocationValidationFailure> ordered = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .sorted()
                .distinct()
                .toList();
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException("Allocation validation exception requires a failure");
        }
        this.failures = ordered;
    }

    public List<AllocationValidationFailure> failures() {
        return failures;
    }

    private static String message(List<AllocationValidationFailure> failures) {
        Objects.requireNonNull(failures, "failures");
        if (failures.isEmpty()) {
            return "Allocation validation failed";
        }
        return failures.stream()
                .filter(Objects::nonNull)
                .sorted()
                .map(failure -> failure.code() + "[" + failure.field() + "]: " + failure.message())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Allocation validation failed");
    }
}
