package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Optional;

public record AllocationCycleOperationResult<T>(
        boolean accepted,
        Optional<T> value,
        List<AllocationCycleFailure> failures
) {
    public AllocationCycleOperationResult {
        value = AllocationValidation.required(value, "value");
        failures = AllocationValidation.required(failures, "failures").stream()
                .map(failure -> AllocationValidation.required(failure, "failure"))
                .sorted()
                .distinct()
                .toList();
        if (accepted != value.isPresent() || accepted != failures.isEmpty()) {
            throw new IllegalArgumentException(
                    "Allocation cycle operation result shape is inconsistent"
            );
        }
    }

    public static <T> AllocationCycleOperationResult<T> accepted(T value) {
        return new AllocationCycleOperationResult<>(
                true,
                Optional.of(AllocationValidation.required(value, "value")),
                List.of()
        );
    }

    public static <T> AllocationCycleOperationResult<T> rejected(
            List<AllocationCycleFailure> failures
    ) {
        if (AllocationValidation.required(failures, "failures").isEmpty()) {
            throw new IllegalArgumentException(
                    "Rejected Allocation cycle operation requires a failure"
            );
        }
        return new AllocationCycleOperationResult<>(
                false,
                Optional.empty(),
                failures
        );
    }
}
