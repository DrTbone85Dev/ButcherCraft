package com.butchercraft.world.transaction.binding;

import java.util.List;
import java.util.Objects;

record ValidationConsumptionResult(
        boolean consumed,
        List<TransactionBindingFailure> failures
) {
    ValidationConsumptionResult {
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
    }

    boolean successful() {
        return consumed && failures.isEmpty();
    }

    static ValidationConsumptionResult failure(TransactionBindingFailure failure) {
        return new ValidationConsumptionResult(false, List.of(failure));
    }
}
