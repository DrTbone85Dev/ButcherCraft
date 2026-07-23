package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public record AllocationOperationResult<T>(
        boolean accepted,
        Optional<T> value,
        List<AllocationValidationFailure> failures
) {
    public AllocationOperationResult {
        value = Objects.requireNonNull(value, "value");
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .sorted()
                .distinct()
                .toList();
        if (accepted != value.isPresent() || accepted != failures.isEmpty()) {
            throw new IllegalArgumentException("Allocation operation result shape is inconsistent");
        }
    }

    public static <T> AllocationOperationResult<T> accepted(T value) {
        return new AllocationOperationResult<>(
                true,
                Optional.of(Objects.requireNonNull(value, "value")),
                List.of()
        );
    }

    public static <T> AllocationOperationResult<T> rejected(List<AllocationValidationFailure> failures) {
        if (Objects.requireNonNull(failures, "failures").isEmpty()) {
            throw new IllegalArgumentException("Rejected allocation operation requires a failure");
        }
        return new AllocationOperationResult<>(false, Optional.empty(), failures);
    }

    public static <T> AllocationOperationResult<T> validate(Supplier<T> construction) {
        try {
            return accepted(Objects.requireNonNull(construction, "construction").get());
        } catch (AllocationValidationException exception) {
            return rejected(exception.failures());
        }
    }
}
