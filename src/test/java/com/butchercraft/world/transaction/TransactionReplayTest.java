package com.butchercraft.world.transaction;

import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionReplayTest {
    @Test
    void appliedHistoryReplaysInAuthoritativeSubmissionOrder() {
        InventoryManager originalInventory = TransactionTestFixtures.emptyManager();
        TransactionManager manager = new TransactionManager(originalInventory);
        List<EconomicTransaction> transactions = List.of(
                TransactionTestFixtures.beefTransaction("test:add", TransactionType.INVENTORY_ADD, 20L, 1L),
                TransactionTestFixtures.beefTransaction("test:transfer", TransactionType.INVENTORY_TRANSFER, 5L, 2L),
                EconomicTransaction.builder()
                        .id(TransactionId.of("test:remove"))
                        .type(TransactionType.INVENTORY_REMOVE)
                        .sourceActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                        .sourceInventoryId(InventoryTestFixtures.GRAIN_INVENTORY)
                        .goodId(InventoryTestFixtures.BEEF)
                        .quantity(3L)
                        .unitOfMeasure(com.butchercraft.world.goods.UnitOfMeasure.POUND)
                        .simulationTick(3L)
                        .build()
        );
        transactions.forEach(transaction -> assertTrue(manager.submit(transaction).success()));

        EconomicTransaction rejected = TransactionTestFixtures.beefTransaction(
                "test:rejected", TransactionType.INVENTORY_REMOVE, 100L, 4L
        );
        assertFalse(manager.submit(rejected).success());

        InventoryManager replayed = TransactionTestFixtures.emptyManager();
        List<TransactionResult> results = manager.replayInto(replayed);

        assertEquals(3, results.size());
        assertEquals(15L, replayed.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));
        assertEquals(2L, replayed.quantityIn(InventoryTestFixtures.GRAIN_INVENTORY, InventoryTestFixtures.BEEF));
        assertEquals(
                originalInventory.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF),
                replayed.quantityIn(InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF)
        );
        assertEquals(
                originalInventory.quantityIn(InventoryTestFixtures.GRAIN_INVENTORY, InventoryTestFixtures.BEEF),
                replayed.quantityIn(InventoryTestFixtures.GRAIN_INVENTORY, InventoryTestFixtures.BEEF)
        );
        assertEquals(List.of("test:add", "test:transfer", "test:remove", "test:rejected"),
                manager.history().stream().map(transaction -> transaction.id().value()).toList());
    }
}
