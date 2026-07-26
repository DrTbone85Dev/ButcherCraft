package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.TransactionId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TransactionValidationBindingCandidate(
        int schemaVersion,
        Optional<TransactionId> transactionId,
        Optional<TransactionProposalIdentity> proposalIdentity,
        Optional<InventoryFreshnessIdentity> inventoryFreshnessIdentity,
        Optional<TransactionValidationPlanIdentity> validationPlanIdentity,
        List<ValidationInputIdentity> validationInputIdentities
) {
    public TransactionValidationBindingCandidate {
        schemaVersion = TransactionBindingValidation.positive(schemaVersion, "schemaVersion");
        transactionId = Objects.requireNonNull(transactionId, "transactionId");
        proposalIdentity = Objects.requireNonNull(proposalIdentity, "proposalIdentity");
        inventoryFreshnessIdentity = Objects.requireNonNull(
                inventoryFreshnessIdentity,
                "inventoryFreshnessIdentity"
        );
        validationPlanIdentity = Objects.requireNonNull(validationPlanIdentity, "validationPlanIdentity");
        validationInputIdentities = Objects.requireNonNull(
                validationInputIdentities,
                "validationInputIdentities"
        ).stream()
                .map(input -> Objects.requireNonNull(input, "validationInputIdentity"))
                .sorted()
                .toList();
    }

    public static TransactionValidationBindingCandidate from(TransactionValidationBinding binding) {
        Objects.requireNonNull(binding, "binding");
        return new TransactionValidationBindingCandidate(
                binding.schemaVersion(),
                Optional.of(binding.transactionId()),
                Optional.of(binding.proposalIdentity()),
                Optional.of(binding.inventoryFreshnessIdentity()),
                Optional.of(binding.validationPlanIdentity()),
                binding.validationInputIdentities()
        );
    }
}
