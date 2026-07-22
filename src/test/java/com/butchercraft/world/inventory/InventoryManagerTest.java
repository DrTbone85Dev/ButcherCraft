package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.butchercraft.world.inventory.InventoryTestFixtures.BEEF;
import static com.butchercraft.world.inventory.InventoryTestFixtures.BEEF_INVENTORY;
import static com.butchercraft.world.inventory.InventoryTestFixtures.BOX;
import static com.butchercraft.world.inventory.InventoryTestFixtures.COOLER;
import static com.butchercraft.world.inventory.InventoryTestFixtures.DISTRIBUTION_CENTER;
import static com.butchercraft.world.inventory.InventoryTestFixtures.GRAIN;
import static com.butchercraft.world.inventory.InventoryTestFixtures.GRAIN_INVENTORY;
import static com.butchercraft.world.inventory.InventoryTestFixtures.WAREHOUSE_ACTOR;
import static com.butchercraft.world.inventory.InventoryTestFixtures.actors;
import static com.butchercraft.world.inventory.InventoryTestFixtures.manager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryManagerTest {
    @Test
    void managerSupportsQuantityOwnershipAndStorageQueries() {
        InventoryManager manager = manager();

        assertEquals(10L, manager.quantityIn(GRAIN_INVENTORY, GRAIN));
        assertEquals(10L, manager.quantityOwnedBy(WAREHOUSE_ACTOR, GRAIN));
        assertEquals(20L, manager.quantityAt(COOLER, BEEF, false));
        assertEquals(20L, manager.quantityAt(DISTRIBUTION_CENTER, BEEF, true));
        assertEquals(2, manager.inventoriesOwnedBy(WAREHOUSE_ACTOR).size());
        assertEquals(2, manager.inventoriesAt(DISTRIBUTION_CENTER, true).size());
        assertEquals(2L, manager.entryCount());
        manager.validate();
    }

    @Test
    void managerUpdatesQuantitiesWithoutExceedingCapacity() {
        InventoryManager manager = manager();

        manager.addEntry(GRAIN_INVENTORY, new InventoryEntry(GRAIN, 40, UnitOfMeasure.BUSHEL), 26L);
        assertEquals(50L, manager.quantityIn(GRAIN_INVENTORY, GRAIN));
        assertThrows(IllegalArgumentException.class, () -> manager.addEntry(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.BUSHEL),
                27L
        ));
        assertEquals(50L, manager.quantityIn(GRAIN_INVENTORY, GRAIN));

        manager.removeEntry(GRAIN_INVENTORY, new InventoryEntry(GRAIN, 5, UnitOfMeasure.BUSHEL), 27L);
        assertEquals(45L, manager.quantityIn(GRAIN_INVENTORY, GRAIN));
    }

    @Test
    void managerRejectsUnknownGoodsInvalidUnitsOriginsAndUnavailableInventories() {
        InventoryManager manager = manager();
        InventoryEntryMetadata unknownOrigin = new InventoryEntryMetadata(
                Optional.empty(),
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.of(ActorId.of("test:missing"))
        );

        assertThrows(IllegalArgumentException.class, () -> manager.addEntry(
                GRAIN_INVENTORY,
                new InventoryEntry(GoodId.of("test:missing"), 1, UnitOfMeasure.EACH),
                26L
        ));
        assertThrows(IllegalArgumentException.class, () -> manager.addEntry(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.POUND),
                26L
        ));
        assertThrows(IllegalArgumentException.class, () -> manager.addEntry(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.BUSHEL, unknownOrigin),
                26L
        ));

        manager.requireRuntime(GRAIN_INVENTORY).transitionTo(InventoryStatus.LOCKED, 26L);
        assertThrows(IllegalArgumentException.class, () -> manager.addEntry(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.BUSHEL),
                27L
        ));
        assertThrows(IllegalArgumentException.class, () -> manager.removeEntry(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.BUSHEL),
                27L
        ));
    }

    @Test
    void managerValidatesAggregateStorageNodeCapacity() {
        EconomicActorRegistry actors = actors();
        StorageNodeId nodeId = StorageNodeId.of("test:bounded_node");
        StorageNode node = StorageNode.builder()
                .id(nodeId)
                .displayName("Bounded Node")
                .storageRequirement(com.butchercraft.world.goods.StorageRequirement.REFRIGERATED)
                .capacity(StorageCapacity.builder().maximumWeight(100, UnitOfMeasure.POUND).build())
                .build();
        InventoryContainer first = container("test:first", nodeId);
        InventoryContainer second = container("test:second", nodeId);
        InventoryRegistry registry = InventoryRegistry.of(
                List.of(first, second), List.of(node), actors.goodRegistry(), actors
        );
        InventoryRuntime firstRuntime = runtime(first.id(), 60);
        InventoryRuntime secondRuntime = runtime(second.id(), 50);

        assertThrows(IllegalArgumentException.class, () -> new InventoryManager(
                registry,
                List.of(firstRuntime, secondRuntime)
        ));
    }

    @Test
    void movementValidationIsExplicitAtomicAndDoesNotMutateInventories() {
        InventoryManager manager = manager();
        InventoryEntry movement = manager.requireRuntime(GRAIN_INVENTORY).entries().getFirst().withQuantity(5);

        InventoryMovementValidation allowed = manager.validateMovement(
                GRAIN_INVENTORY,
                BEEF_INVENTORY,
                movement
        );

        assertTrue(allowed.isAllowed());
        assertEquals(10L, manager.quantityIn(GRAIN_INVENTORY, GRAIN));
        assertEquals(0L, manager.quantityIn(BEEF_INVENTORY, GRAIN));

        assertEquals(InventoryMovementCode.SAME_INVENTORY, manager.validateMovement(
                GRAIN_INVENTORY, GRAIN_INVENTORY, movement
        ).code());
        assertEquals(InventoryMovementCode.INSUFFICIENT_QUANTITY, manager.validateMovement(
                GRAIN_INVENTORY, BEEF_INVENTORY, movement.withQuantity(11)
        ).code());
        manager.requireRuntime(BEEF_INVENTORY).transitionTo(InventoryStatus.MAINTENANCE, 26L);
        InventoryMovementValidation unavailable = manager.validateMovement(
                GRAIN_INVENTORY, BEEF_INVENTORY, movement
        );
        assertFalse(unavailable.isAllowed());
        assertEquals(InventoryMovementCode.TARGET_UNAVAILABLE, unavailable.code());
    }

    @Test
    void movementValidationDetectsTargetCapacityWithoutChangingEitherSide() {
        EconomicActorRegistry actors = actors();
        StorageNode sourceNode = StorageNode.builder()
                .id("test:source_node")
                .displayName("Source")
                .storageRequirement(com.butchercraft.world.goods.StorageRequirement.REFRIGERATED)
                .build();
        StorageNode targetNode = StorageNode.builder()
                .id("test:target_node")
                .displayName("Target")
                .storageRequirement(com.butchercraft.world.goods.StorageRequirement.REFRIGERATED)
                .build();
        InventoryContainer source = InventoryContainer.builder()
                .id("test:source")
                .displayName("Source")
                .ownerActorId(WAREHOUSE_ACTOR)
                .storageNodeId(sourceNode.id())
                .inventoryType(InventoryType.WAREHOUSE)
                .build();
        InventoryContainer target = InventoryContainer.builder()
                .id("test:target")
                .displayName("Target")
                .ownerActorId(WAREHOUSE_ACTOR)
                .storageNodeId(targetNode.id())
                .inventoryType(InventoryType.WAREHOUSE)
                .capacity(StorageCapacity.builder().maximumWeight(10, UnitOfMeasure.POUND).build())
                .build();
        InventoryRegistry registry = InventoryRegistry.of(
                List.of(source, target), List.of(sourceNode, targetNode), actors.goodRegistry(), actors
        );
        InventoryManager manager = new InventoryManager(registry, List.of(runtime(source.id(), 20)));
        InventoryEntry movement = new InventoryEntry(BEEF, 11, UnitOfMeasure.POUND);

        InventoryMovementValidation validation = manager.validateMovement(source.id(), target.id(), movement);

        assertEquals(InventoryMovementCode.CAPACITY_EXCEEDED, validation.code());
        assertEquals(20L, manager.quantityIn(source.id(), BEEF));
        assertEquals(0L, manager.quantityIn(target.id(), BEEF));
    }

    private static InventoryContainer container(String id, StorageNodeId nodeId) {
        return InventoryContainer.builder()
                .id(id)
                .displayName(id)
                .ownerActorId(WAREHOUSE_ACTOR)
                .storageNodeId(nodeId)
                .inventoryType(InventoryType.WAREHOUSE)
                .build();
    }

    private static InventoryRuntime runtime(InventoryId inventoryId, long quantity) {
        return new InventoryRuntime(
                inventoryId,
                InventoryStatus.ACTIVE,
                List.of(new InventoryEntry(BEEF, quantity, UnitOfMeasure.POUND)),
                0L,
                InventorySchema.CURRENT_VERSION
        );
    }
}
