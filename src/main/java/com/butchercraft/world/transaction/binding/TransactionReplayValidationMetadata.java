package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;

import java.util.List;
import java.util.Objects;

public record TransactionReplayValidationMetadata(
        TransactionProposalIdentity proposalIdentity,
        InventoryFreshnessIdentity inventoryFreshnessIdentity,
        TransactionValidationPlanIdentity validationPlanIdentity,
        List<ValidationInputIdentity> validationInputIdentities,
        AuthoritativeTransactionResultEvidence resultEvidence
) {
    public TransactionReplayValidationMetadata {
        proposalIdentity = Objects.requireNonNull(proposalIdentity, "proposalIdentity");
        inventoryFreshnessIdentity = Objects.requireNonNull(inventoryFreshnessIdentity, "inventoryFreshnessIdentity");
        validationPlanIdentity = Objects.requireNonNull(validationPlanIdentity, "validationPlanIdentity");
        validationInputIdentities = Objects.requireNonNull(validationInputIdentities, "validationInputIdentities")
                .stream()
                .map(input -> Objects.requireNonNull(input, "validationInputIdentity"))
                .sorted()
                .toList();
        resultEvidence = Objects.requireNonNull(resultEvidence, "resultEvidence");
    }
}
