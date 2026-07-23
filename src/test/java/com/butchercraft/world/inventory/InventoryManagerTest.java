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
    void runtimeAccessReturnsDefensiveSnapshots() {
        InventoryManager manager = manager();
        InventoryRuntime snapshot = manager.requireRuntime(BEEF_INVENTORY);

        snapshot.addEntry(new InventoryEntry(BEEF, 5L, UnitOfMeasure.POUND), 26L);
        snapshot.transitionTo(InventoryStatus.LOCKED, 27L);

        assertEquals(20L, manager.quantityIn(BEEF_INVENTORY, BEEF));
        assertEquals(InventoryStatus.ACTIVE, manager.requireRuntime(BEEF_INVENTORY).status());
        assertEquals(25L, manager.requireRuntime(BEEF_INVENTORY).lastSimulationTick());
    }

    @Test
    void managerValidatesCandidateChangesWithoutMutatingRuntimeState() {
        InventoryManager manager = manager();

        assertTrue(manager.validateChanges(List.of(InventoryChange.add(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 40, UnitOfMeasure.BUSHEL)
        )), 26L).isAllowed());
        assertEquals(InventoryChangeCode.CAPACITY_EXCEEDED, manager.validateChanges(List.of(InventoryChange.add(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 41, UnitOfMeasure.BUSHEL)
        )), 26L).code());
        assertTrue(manager.validateChanges(List.of(InventoryChange.remove(
                GRAIN_INVENTORY,
                manager.requireRuntime(GRAIN_INVENTORY).entries().getFirst().withQuantity(5)
        )), 26L).isAllowed());
        assertEquals(10L, manager.quantityIn(GRAIN_INVENTORY, GRAIN));
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

        assertEquals(InventoryChangeCode.UNKNOWN_GOOD, manager.validateChanges(List.of(InventoryChange.add(
                GRAIN_INVENTORY,
                new InventoryEntry(GoodId.of("test:missing"), 1, UnitOfMeasure.EACH)
        )), 26L).code());
        assertEquals(InventoryChangeCode.INVALID_UNIT, manager.validateChanges(List.of(InventoryChange.add(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.POUND)
        )), 26L).code());
        assertEquals(InventoryChangeCode.INVALID_METADATA, manager.validateChanges(List.of(InventoryChange.add(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.BUSHEL, unknownOrigin)
        )), 26L).code());

        InventoryManager lockedManager = managerWithStatus(GRAIN_INVENTORY, InventoryStatus.LOCKED);
        assertEquals(InventoryChangeCode.INVENTORY_UNAVAILABLE, lockedManager.validateChanges(List.of(
                InventoryChange.add(
                GRAIN_INVENTORY,
                new InventoryEntry(GRAIN, 1, UnitOfMeasure.BUSHEL)
        )), 27L).code());
        assertEquals(InventoryChangeCode.INVENTORY_UNAVAILABLE, lockedManager.validateChanges(List.of(
                InventoryChange.remove(
                GRAIN_INVENTORY,
                lockedManager.requireRuntime(GRAIN_INVENTORY).entries().getFirst().withQuantity(1)
        )), 27L).code());
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
        InventoryManager unavailableManager = managerWithStatus(BEEF_INVENTORY, InventoryStatus.MAINTENANCE);
        InventoryEntry unavailableMovement = unavailableManager.requireRuntime(GRAIN_INVENTORY)
                .entries().getFirst().withQuantity(5);
        InventoryMovementValidation unavailable = unavailableManager.validateMovement(
                GRAIN_INVENTORY, BEEF_INVENTORY, unavailableMovement
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

    private static InventoryManager managerWithStatus(InventoryId inventoryId, InventoryStatus status) {
        InventoryManager source = manager();
        List<InventoryRuntime> runtimes = source.runtimes().stream()
                .map(runtime -> runtime.inventoryId().equals(inventoryId)
                        ? new InventoryRuntime(
                                runtime.inventoryId(),
                                status,
                                runtime.entries(),
                                runtime.lastSimulationTick(),
                                runtime.schemaVersion()
                        )
                        : runtime)
                .toList();
        return new InventoryManager(source.registry(), runtimes);
    }
}
