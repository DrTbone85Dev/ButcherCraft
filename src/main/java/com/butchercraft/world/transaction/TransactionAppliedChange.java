package com.butchercraft.world.transaction;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryChangeType;
import com.butchercraft.world.inventory.InventoryId;

import java.util.Objects;

public record TransactionAppliedChange(
        InventoryId inventoryId,
        InventoryChangeType changeType,
        GoodId goodId,
        long quantity,
        UnitOfMeasure unitOfMeasure
) {
    public TransactionAppliedChange {
        inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        changeType = Objects.requireNonNull(changeType, "changeType");
        goodId = Objects.requireNonNull(goodId, "goodId");
        if (quantity <= 0L) {
            throw new IllegalArgumentException("Applied transaction quantity must be positive: " + quantity);
        }
        unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    }

    public static TransactionAppliedChange from(InventoryChange change) {
        Objects.requireNonNull(change, "change");
        return new TransactionAppliedChange(
                change.inventoryId(),
                change.type(),
                change.entry().goodId(),
                change.entry().quantity(),
                change.entry().unitOfMeasure()
        );
    }
}
