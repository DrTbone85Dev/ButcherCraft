package com.butchercraft.world.allocation;

import java.util.List;

public final class AllocationCycleValidationException extends IllegalArgumentException {
    private final List<AllocationCycleFailure> failures;

    public AllocationCycleValidationException(List<AllocationCycleFailure> failures) {
        super(message(failures));
        List<AllocationCycleFailure> values = AllocationValidation.required(
                failures,
                "failures"
        ).stream().sorted().distinct().toList();
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Allocation cycle validation requires at least one failure"
            );
        }
        this.failures = values;
    }

    public List<AllocationCycleFailure> failures() {
        return failures;
    }

    private static String message(List<AllocationCycleFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return "Allocation cycle validation failed";
        }
        return failures.stream()
                .sorted()
                .map(failure -> failure.code() + ": " + failure.message())
                .collect(java.util.stream.Collectors.joining("; "));
    }
}
