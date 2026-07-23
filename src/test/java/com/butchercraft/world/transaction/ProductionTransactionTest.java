package com.butchercraft.world.transaction;

import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryTestFixtures;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionTransactionTest {
    @Test
    void productionChangePlanAppliesAndReplaysAsOneAtomicTransaction() {
        InventoryManager inventory = TransactionTestFixtures.manager();
        TransactionManager transactions = new TransactionManager(inventory);
        EconomicTransaction transaction = EconomicTransaction.builder()
                .id(TransactionId.of("test:production/one"))
                .type(TransactionType.PRODUCTION)
                .sourceActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .destinationActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .goodId(InventoryTestFixtures.BEEF)
                .quantity(1L)
                .unitOfMeasure(UnitOfMeasure.POUND)
                .simulationTick(25L)
                .inventoryChange(InventoryChange.remove(
                        InventoryTestFixtures.BEEF_INVENTORY,
                        new InventoryEntry(InventoryTestFixtures.BEEF, 2L, UnitOfMeasure.POUND)
                ))
                .inventoryChange(InventoryChange.add(
                        InventoryTestFixtures.BEEF_INVENTORY,
                        new InventoryEntry(InventoryTestFixtures.BEEF, 1L, UnitOfMeasure.POUND)
                ))
                .build();

        TransactionResult result = transactions.submit(transaction);
        assertTrue(result.success());
        assertEquals(2, result.appliedChanges().size());
        assertEquals(19L, inventory.quantityIn(
                InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));

        InventoryManager replayInventory = TransactionTestFixtures.manager();
        assertEquals(1, transactions.replayInto(replayInventory).size());
        assertEquals(19L, replayInventory.quantityIn(
                InventoryTestFixtures.BEEF_INVENTORY, InventoryTestFixtures.BEEF));

        TransactionStorage storage = new TransactionStorage(Path.of("unused.json"), inventory);
        TransactionManager roundTrip = storage.deserialize(storage.serialize(transactions));
        assertEquals(transactions.history(), roundTrip.history());
    }

    @Test
    void legacySchemaOneTransactionWithoutChangePlanStillLoads() {
        String legacy = """
                {
                  "schema_version": 1,
                  "transactions": [
                    {
                      "schema_version": 1,
                      "id": "test:legacy",
                      "type": "inventory_add",
                      "source_actor_id": null,
                      "destination_actor_id": "test:warehouse_actor",
                      "source_inventory_id": null,
                      "destination_inventory_id": "test:beef_inventory",
                      "good_id": "test:beef",
                      "quantity": 1,
                      "unit": "pound",
                      "simulation_tick": 25,
                      "status": "pending",
                      "metadata": {
                        "reason": null,
                        "reference_id": null,
                        "user": null,
                        "external_system": null,
                        "comments": null
                      }
                    }
                  ]
                }
                """;
        TransactionStorage storage = new TransactionStorage(
                Path.of("unused.json"), TransactionTestFixtures.manager());
        TransactionManager manager = storage.deserialize(legacy);

        assertEquals(1, manager.size());
        assertTrue(manager.history().getFirst().inventoryChangePlan().isEmpty());
    }
}
