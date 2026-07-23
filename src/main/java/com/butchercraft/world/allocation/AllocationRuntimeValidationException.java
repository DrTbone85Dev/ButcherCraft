package com.butchercraft.world.allocation;

import java.util.List;

public final class AllocationRuntimeValidationException extends IllegalArgumentException {
    private final List<AllocationRuntimeFailure> failures;

    public AllocationRuntimeValidationException(List<AllocationRuntimeFailure> failures) {
        super(message(failures));
        List<AllocationRuntimeFailure> ordered = AllocationValidation.required(
                failures,
                "failures"
        ).stream()
                .map(failure -> AllocationValidation.required(failure, "failure"))
                .sorted()
                .distinct()
                .toList();
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException(
                    "Allocation runtime validation exception requires a failure"
            );
        }
        this.failures = ordered;
    }

    public List<AllocationRuntimeFailure> failures() {
        return failures;
    }

    private static String message(List<AllocationRuntimeFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return "Allocation runtime validation failed";
        }
        return failures.stream()
                .filter(java.util.Objects::nonNull)
                .sorted()
                .map(failure -> failure.code() + "[" + failure.subject() + "]: "
                        + failure.message())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Allocation runtime validation failed");
    }
}
