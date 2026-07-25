package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;
import com.butchercraft.world.transaction.TransactionId;

import java.util.List;
import java.util.Objects;

public record TransactionValidationBinding(
        int schemaVersion,
        TransactionId transactionId,
        TransactionProposalIdentity proposalIdentity,
        InventoryFreshnessIdentity inventoryFreshnessIdentity,
        TransactionValidationPlanIdentity validationPlanIdentity,
        List<ValidationInputIdentity> validationInputIdentities
) {
    public TransactionValidationBinding {
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

    public static TransactionValidationBinding of(
            TransactionId transactionId,
            TransactionProposalIdentity proposalIdentity,
            InventoryFreshnessIdentity inventoryFreshnessIdentity,
            TransactionValidationPlanIdentity validationPlanIdentity,
            List<ValidationInputIdentity> validationInputIdentities
    ) {
        return new TransactionValidationBinding(
                TransactionBindingSchema.CURRENT_VERSION,
                transactionId,
                proposalIdentity,
                inventoryFreshnessIdentity,
                validationPlanIdentity,
                validationInputIdentities
        );
    }
}
