package com.butchercraft.world.transaction.binding;

import java.util.List;
import java.util.Objects;

public record TransactionBindingValidationResult(List<TransactionBindingFailure> failures) {
    public TransactionBindingValidationResult {
        failures = Objects.requireNonNull(failures, "failures").stream()
                .map(failure -> Objects.requireNonNull(failure, "failure"))
                .toList();
    }

    public boolean successful() {
        return failures.isEmpty();
    }

    public static TransactionBindingValidationResult successfulResult() {
        return new TransactionBindingValidationResult(List.of());
    }
}
