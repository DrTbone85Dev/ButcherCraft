package com.butchercraft.world.inventory;

import java.util.Objects;

public record InventoryChange(
        InventoryId inventoryId,
        InventoryChangeType type,
        InventoryEntry entry
) {
    public InventoryChange {
        inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        type = Objects.requireNonNull(type, "type");
        entry = Objects.requireNonNull(entry, "entry");
    }

    public static InventoryChange add(InventoryId inventoryId, InventoryEntry entry) {
        return new InventoryChange(inventoryId, InventoryChangeType.ADD, entry);
    }

    public static InventoryChange remove(InventoryId inventoryId, InventoryEntry entry) {
        return new InventoryChange(inventoryId, InventoryChangeType.REMOVE, entry);
    }
}
