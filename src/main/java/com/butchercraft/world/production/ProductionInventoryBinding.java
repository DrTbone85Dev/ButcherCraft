package com.butchercraft.world.production;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryId;

import java.util.Objects;

public record ProductionInventoryBinding(
        ProductionLineId lineId,
        ProductionBindingDirection direction,
        InventoryId inventoryId,
        GoodId expectedGoodId,
        UnitOfMeasure expectedUnit
) implements Comparable<ProductionInventoryBinding> {
    public ProductionInventoryBinding {
        lineId = Objects.requireNonNull(lineId, "lineId");
        direction = Objects.requireNonNull(direction, "direction");
        inventoryId = Objects.requireNonNull(inventoryId, "inventoryId");
        expectedGoodId = Objects.requireNonNull(expectedGoodId, "expectedGoodId");
        expectedUnit = Objects.requireNonNull(expectedUnit, "expectedUnit");
    }

    @Override
    public int compareTo(ProductionInventoryBinding other) {
        int directionOrder = direction.compareTo(Objects.requireNonNull(other, "other").direction);
        return directionOrder != 0 ? directionOrder : lineId.compareTo(other.lineId);
    }
}
