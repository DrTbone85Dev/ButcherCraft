package com.butchercraft.world.inventory;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;

import java.util.Objects;

public record InventoryEntry(
        GoodId goodId,
        long quantity,
        UnitOfMeasure unitOfMeasure,
        InventoryEntryMetadata metadata
) implements Comparable<InventoryEntry> {
    public InventoryEntry {
        goodId = Objects.requireNonNull(goodId, "goodId");
        if (quantity < 0L) {
            throw new IllegalArgumentException("Inventory quantity must not be negative: " + quantity);
        }
        unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        metadata = Objects.requireNonNull(metadata, "metadata");
    }

    public InventoryEntry(GoodId goodId, long quantity, UnitOfMeasure unitOfMeasure) {
        this(goodId, quantity, unitOfMeasure, InventoryEntryMetadata.empty());
    }

    public InventoryEntry withQuantity(long nextQuantity) {
        return new InventoryEntry(goodId, nextQuantity, unitOfMeasure, metadata);
    }

    public boolean sameStoredGood(InventoryEntry other) {
        Objects.requireNonNull(other, "other");
        return goodId.equals(other.goodId)
                && unitOfMeasure == other.unitOfMeasure
                && metadata.equals(other.metadata);
    }

    @Override
    public int compareTo(InventoryEntry other) {
        Objects.requireNonNull(other, "other");
        int goodComparison = goodId.compareTo(other.goodId);
        if (goodComparison != 0) {
            return goodComparison;
        }
        int unitComparison = unitOfMeasure.serializedName().compareTo(other.unitOfMeasure.serializedName());
        if (unitComparison != 0) {
            return unitComparison;
        }
        return metadata.compareTo(other.metadata);
    }
}
