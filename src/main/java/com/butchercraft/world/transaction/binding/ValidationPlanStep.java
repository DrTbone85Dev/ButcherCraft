package com.butchercraft.world.transaction.binding;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryChangeType;
import com.butchercraft.world.inventory.InventoryEntryMetadata;
import com.butchercraft.world.inventory.InventoryId;

import java.util.Objects;

public record ValidationPlanStep(
        int operationOrder,
        InventoryId inventoryId,
        InventoryChangeType changeType,
        GoodId goodId,
        long quantity,
        UnitOfMeasure unitOfMeasure,
        InventoryEntryMetadata metadata
) implements Comparable<ValidationPlanStep> {
    public ValidationPlanStep {
        if (operationOrder < 0) {
            throw new IllegalArgumentException("operationOrder must not be negative: " + operationOrder);
        }
        inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        changeType = Objects.requireNonNull(changeType, "changeType");
        goodId = Objects.requireNonNull(goodId, "goodId");
        quantity = TransactionBindingValidation.positive(quantity, "quantity");
        unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public static ValidationPlanStep from(int operationOrder, InventoryChange change) {
        Objects.requireNonNull(change, "change");
        return new ValidationPlanStep(
                operationOrder,
                change.inventoryId(),
                change.type(),
                change.entry().goodId(),
                change.entry().quantity(),
                change.entry().unitOfMeasure(),
                change.entry().metadata()
        );
    }

    @Override
    public int compareTo(ValidationPlanStep other) {
        Objects.requireNonNull(other, "other");
        int orderComparison = Integer.compare(operationOrder, other.operationOrder);
        if (orderComparison != 0) {
            return orderComparison;
        }
        int inventoryComparison = inventoryId.compareTo(other.inventoryId);
        if (inventoryComparison != 0) {
            return inventoryComparison;
        }
        int typeComparison = changeType.name().compareTo(other.changeType.name());
        if (typeComparison != 0) {
            return typeComparison;
        }
        int goodComparison = goodId.compareTo(other.goodId);
        if (goodComparison != 0) {
            return goodComparison;
        }
        return Long.compare(quantity, other.quantity);
    }
}
