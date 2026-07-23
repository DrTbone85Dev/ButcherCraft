package com.butchercraft.world.economy.order;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ContractOperationResult(
        boolean success,
        Optional<ContractFailureCode> failureCode,
        List<String> messages
) {
    public ContractOperationResult {
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        if (success == failureCode.isPresent()) {
            throw new IllegalArgumentException("Contract operation success and failure code are inconsistent");
        }
    }

    public static ContractOperationResult accepted() {
        return new ContractOperationResult(true, Optional.empty(), List.of());
    }

    public static ContractOperationResult rejected(ContractFailureCode code, String message) {
        return new ContractOperationResult(false, Optional.of(code), List.of(message));
    }
}
