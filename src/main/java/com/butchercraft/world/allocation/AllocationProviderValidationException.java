package com.butchercraft.world.allocation;

import java.util.List;

public final class AllocationProviderValidationException extends IllegalArgumentException {
    private final List<AllocationProviderFailure> failures;

    public AllocationProviderValidationException(List<AllocationProviderFailure> failures) {
        super(message(failures));
        this.failures = AllocationValidation.required(failures, "failures").stream()
                .map(failure -> AllocationValidation.required(failure, "failure"))
                .sorted()
                .distinct()
                .toList();
        if (this.failures.isEmpty()) {
            throw new IllegalArgumentException(
                    "Provider validation exception requires at least one failure"
            );
        }
    }

    public List<AllocationProviderFailure> failures() {
        return failures;
    }

    private static String message(List<AllocationProviderFailure> failures) {
        if (failures == null || failures.isEmpty()) {
            return "Allocation provider validation failed";
        }
        return failures.stream()
                .sorted()
                .map(failure -> failure.code() + "[" + failure.subject() + "]: "
                        + failure.message())
                .reduce((left, right) -> left + "; " + right)
                .orElse("Allocation provider validation failed");
    }
}
