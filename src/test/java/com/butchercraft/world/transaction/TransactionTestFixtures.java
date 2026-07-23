package com.butchercraft.world.transaction;

import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryTestFixtures;

final class TransactionTestFixtures {
    private TransactionTestFixtures() {
    }

    static InventoryManager manager() {
        return InventoryTestFixtures.manager();
    }

    static InventoryManager emptyManager() {
        return new InventoryManager(InventoryTestFixtures.registry());
    }

    static EconomicTransaction beefTransaction(
            String id,
            TransactionType type,
            long quantity,
            long simulationTick
    ) {
        EconomicTransaction.Builder builder = EconomicTransaction.builder()
                .id(TransactionId.of(id))
                .type(type)
                .goodId(InventoryTestFixtures.BEEF)
                .quantity(quantity)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .simulationTick(simulationTick);
        switch (type) {
            case INVENTORY_ADD -> builder
                    .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                    .destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY);
            case INVENTORY_REMOVE -> builder
                    .sourceActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                    .sourceInventoryId(InventoryTestFixtures.BEEF_INVENTORY);
            case INVENTORY_TRANSFER -> builder
                    .sourceActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                    .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                    .sourceInventoryId(InventoryTestFixtures.BEEF_INVENTORY)
                    .destinationInventoryId(InventoryTestFixtures.GRAIN_INVENTORY);
            case INVENTORY_ADJUSTMENT -> builder.destinationInventoryId(InventoryTestFixtures.BEEF_INVENTORY);
            default -> {
            }
        }
        return builder.build();
    }
}
