package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public record AllocationRuntimeOperationResult<T>(
        boolean accepted,
        Optional<T> value,
        List<AllocationRuntimeFailure> failures
) {
    public AllocationRuntimeOperationResult {
        value = AllocationValidation.required(value, "value");
        failures = AllocationValidation.required(failures, "failures").stream()
                .map(failure -> AllocationValidation.required(failure, "failure"))
                .sorted()
                .distinct()
                .toList();
        if (accepted != value.isPresent() || accepted != failures.isEmpty()) {
            throw new IllegalArgumentException(
                    "Allocation runtime operation result shape is inconsistent"
            );
        }
    }

    public static <T> AllocationRuntimeOperationResult<T> accepted(T value) {
        return new AllocationRuntimeOperationResult<>(
                true,
                Optional.of(AllocationValidation.required(value, "value")),
                List.of()
        );
    }

    public static <T> AllocationRuntimeOperationResult<T> rejected(
            AllocationRuntimeFailureCode code,
            String subject,
            String message
    ) {
        return rejected(List.of(new AllocationRuntimeFailure(code, subject, message)));
    }

    public static <T> AllocationRuntimeOperationResult<T> rejected(
            List<AllocationRuntimeFailure> failures
    ) {
        if (AllocationValidation.required(failures, "failures").isEmpty()) {
            throw new IllegalArgumentException(
                    "Rejected Allocation runtime operation requires a failure"
            );
        }
        return new AllocationRuntimeOperationResult<>(
                false,
                Optional.empty(),
                failures
        );
    }

    static <T> AllocationRuntimeOperationResult<T> validate(Supplier<T> operation) {
        try {
            return accepted(AllocationValidation.required(operation, "operation").get());
        } catch (AllocationRuntimeValidationException exception) {
            return rejected(exception.failures());
        } catch (AllocationValidationException exception) {
            return rejected(
                    AllocationRuntimeFailureCode.STRUCTURAL_VALIDATION_FAILED,
                    "allocation",
                    exception.getMessage()
            );
        }
    }
}
