package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryChange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TransactionValidation(
        TransactionId transactionId,
        boolean accepted,
        Optional<TransactionFailureCode> failureCode,
        List<String> messages,
        List<InventoryChange> inventoryChanges
) {
    public TransactionValidation {
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        inventoryChanges = List.copyOf(Objects.requireNonNull(inventoryChanges, "inventoryChanges"));
        messages.forEach(message -> Objects.requireNonNull(message, "message"));
        inventoryChanges.forEach(change -> Objects.requireNonNull(change, "inventoryChange"));
        if (accepted && failureCode.isPresent()) {
            throw new IllegalArgumentException("Accepted transaction validation cannot contain a failure code");
        }
        if (!accepted && failureCode.isEmpty()) {
            throw new IllegalArgumentException("Rejected transaction validation requires a failure code");
        }
        if (!accepted && !inventoryChanges.isEmpty()) {
            throw new IllegalArgumentException("Rejected transaction validation cannot contain inventory changes");
        }
    }

    public static TransactionValidation accepted(TransactionId id, List<InventoryChange> changes) {
        return new TransactionValidation(id, true, Optional.empty(), List.of(), changes);
    }

    public static TransactionValidation rejected(
            TransactionId id,
            TransactionFailureCode code,
            String message
    ) {
        return new TransactionValidation(id, false, Optional.of(code), List.of(message), List.of());
    }
}
