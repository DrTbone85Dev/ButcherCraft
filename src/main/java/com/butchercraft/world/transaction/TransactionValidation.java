package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.binding.TransactionProposalIdentity;
import com.butchercraft.world.transaction.binding.TransactionValidationBinding;
import com.butchercraft.world.transaction.binding.TransactionValidationPlan;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TransactionValidation(
        TransactionId transactionId,
        boolean accepted,
        Optional<TransactionFailureCode> failureCode,
        List<String> messages,
        List<InventoryChange> inventoryChanges,
        Optional<TransactionProposalIdentity> proposalIdentity,
        Optional<InventoryFreshnessIdentity> inventoryFreshnessIdentity,
        Optional<TransactionValidationPlan> validationPlan,
        Optional<TransactionValidationBinding> binding
) {
    public TransactionValidation {
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages"));
        inventoryChanges = List.copyOf(Objects.requireNonNull(inventoryChanges, "inventoryChanges"));
        proposalIdentity = Objects.requireNonNull(proposalIdentity, "proposalIdentity");
        inventoryFreshnessIdentity = Objects.requireNonNull(inventoryFreshnessIdentity, "inventoryFreshnessIdentity");
        validationPlan = Objects.requireNonNull(validationPlan, "validationPlan");
        binding = Objects.requireNonNull(binding, "binding");
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
        if (binding.isPresent()) {
            TransactionValidationBinding validationBinding = binding.orElseThrow();
            if (!validationBinding.transactionId().equals(transactionId)) {
                throw new IllegalArgumentException("Validation binding Transaction identity does not match validation");
            }
            if (proposalIdentity.isEmpty()
                    || inventoryFreshnessIdentity.isEmpty()
                    || validationPlan.isEmpty()) {
                throw new IllegalArgumentException("Validation binding requires all identity components");
            }
            if (!validationBinding.proposalIdentity().equals(proposalIdentity.orElseThrow())) {
                throw new IllegalArgumentException("Validation binding proposal identity does not match validation");
            }
            if (!validationBinding.inventoryFreshnessIdentity().equals(inventoryFreshnessIdentity.orElseThrow())) {
                throw new IllegalArgumentException(
                        "Validation binding Inventory Freshness Identity does not match validation"
                );
            }
            if (!validationBinding.validationPlanIdentity().equals(validationPlan.orElseThrow().identity())) {
                throw new IllegalArgumentException("Validation binding plan identity does not match validation");
            }
        }
    }

    public static TransactionValidation accepted(TransactionId id, List<InventoryChange> changes) {
        return new TransactionValidation(
                id,
                true,
                Optional.empty(),
                List.of(),
                changes,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static TransactionValidation acceptedBound(
            TransactionId id,
            List<InventoryChange> changes,
            TransactionProposalIdentity proposalIdentity,
            InventoryFreshnessIdentity inventoryFreshnessIdentity,
            TransactionValidationPlan validationPlan,
            TransactionValidationBinding binding
    ) {
        return new TransactionValidation(
                id,
                true,
                Optional.empty(),
                List.of(),
                changes,
                Optional.of(proposalIdentity),
                Optional.of(inventoryFreshnessIdentity),
                Optional.of(validationPlan),
                Optional.of(binding)
        );
    }

    public static TransactionValidation rejectedBound(
            TransactionId id,
            TransactionFailureCode code,
            String message,
            TransactionProposalIdentity proposalIdentity,
            InventoryFreshnessIdentity inventoryFreshnessIdentity,
            TransactionValidationPlan validationPlan,
            TransactionValidationBinding binding
    ) {
        return new TransactionValidation(
                id,
                false,
                Optional.of(code),
                List.of(message),
                List.of(),
                Optional.of(proposalIdentity),
                Optional.of(inventoryFreshnessIdentity),
                Optional.of(validationPlan),
                Optional.of(binding)
        );
    }

    public static TransactionValidation rejected(
            TransactionId id,
            TransactionFailureCode code,
            String message
    ) {
        return new TransactionValidation(
                id,
                false,
                Optional.of(code),
                List.of(message),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
