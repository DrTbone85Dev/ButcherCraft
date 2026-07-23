package com.butchercraft.world.transaction;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TransactionResult(
        boolean success,
        Optional<TransactionFailureCode> failureCode,
        List<String> validationMessages,
        List<TransactionAppliedChange> appliedChanges,
        long executionTick
) {
    public TransactionResult {
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        validationMessages = copyMessages(validationMessages);
        appliedChanges = List.copyOf(Objects.requireNonNull(appliedChanges, "appliedChanges"));
        appliedChanges.forEach(change -> Objects.requireNonNull(change, "appliedChange"));
        if (executionTick < 0L) {
            throw new IllegalArgumentException("Transaction execution tick must not be negative: " + executionTick);
        }
        if (success && failureCode.isPresent()) {
            throw new IllegalArgumentException("Successful transaction result cannot contain a failure code");
        }
        if (!success && failureCode.isEmpty()) {
            throw new IllegalArgumentException("Failed transaction result requires a failure code");
        }
        if (!success && !appliedChanges.isEmpty()) {
            throw new IllegalArgumentException("Failed transaction result cannot contain applied changes");
        }
    }

    public static TransactionResult applied(List<TransactionAppliedChange> changes, long executionTick) {
        return new TransactionResult(true, Optional.empty(), List.of(), changes, executionTick);
    }

    public static TransactionResult rejected(
            TransactionFailureCode code,
            List<String> messages,
            long executionTick
    ) {
        return new TransactionResult(false, Optional.of(code), messages, List.of(), executionTick);
    }

    private static List<String> copyMessages(List<String> messages) {
        return Objects.requireNonNull(messages, "validationMessages").stream()
                .map(message -> {
                    String normalized = Objects.requireNonNull(message, "validationMessage").strip();
                    if (normalized.isEmpty()) {
                        throw new IllegalArgumentException("Transaction validation message cannot be blank");
                    }
                    return normalized;
                })
                .toList();
    }
}
