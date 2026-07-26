package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.inventory.freshness.InventoryFreshnessIdentity;

import java.util.Objects;

public record ResultingInventoryFreshnessEvidence(
        String resultIdentity,
        InventoryFreshnessIdentity inventoryFreshnessIdentity
) implements Comparable<ResultingInventoryFreshnessEvidence> {
    public ResultingInventoryFreshnessEvidence {
        resultIdentity = TransactionBindingValidation.id(resultIdentity, "resultIdentity");
        inventoryFreshnessIdentity = Objects.requireNonNull(
                inventoryFreshnessIdentity,
                "inventoryFreshnessIdentity"
        );
    }

    @Override
    public int compareTo(ResultingInventoryFreshnessEvidence other) {
        Objects.requireNonNull(other, "other");
        int resultComparison = resultIdentity.compareTo(other.resultIdentity);
        if (resultComparison != 0) {
            return resultComparison;
        }
        return inventoryFreshnessIdentity.identityDigest().compareTo(
                other.inventoryFreshnessIdentity.identityDigest()
        );
    }
}
