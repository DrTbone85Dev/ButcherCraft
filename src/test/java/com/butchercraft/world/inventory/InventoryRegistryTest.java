package com.butchercraft.world.inventory;

import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.inventory.InventoryTestFixtures.BEEF_INVENTORY;
import static com.butchercraft.world.inventory.InventoryTestFixtures.COOLER;
import static com.butchercraft.world.inventory.InventoryTestFixtures.DISTRIBUTION_CENTER;
import static com.butchercraft.world.inventory.InventoryTestFixtures.GRAIN_INVENTORY;
import static com.butchercraft.world.inventory.InventoryTestFixtures.WAREHOUSE;
import static com.butchercraft.world.inventory.InventoryTestFixtures.WAREHOUSE_ACTOR;
import static com.butchercraft.world.inventory.InventoryTestFixtures.actors;
import static com.butchercraft.world.inventory.InventoryTestFixtures.containers;
import static com.butchercraft.world.inventory.InventoryTestFixtures.registry;
import static com.butchercraft.world.inventory.InventoryTestFixtures.storageNodes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryRegistryTest {
    @Test
    void registryProvidesDeterministicContainerStorageAndHierarchyQueries() {
        InventoryRegistry registry = registry();

        assertEquals(List.of(BEEF_INVENTORY, GRAIN_INVENTORY), registry.stream()
                .map(InventoryContainer::id)
                .toList());
        assertEquals(List.of(COOLER, DISTRIBUTION_CENTER, WAREHOUSE), registry.storageNodeStream()
                .map(StorageNode::id)
                .toList());
        assertEquals(2, registry.size());
        assertEquals(3, registry.storageNodeCount());
        assertTrue(registry.contains(GRAIN_INVENTORY));
        assertFalse(registry.contains(InventoryId.of("test:missing")));
        assertEquals(2, registry.findByOwner(WAREHOUSE_ACTOR).size());
        assertEquals(1, registry.findByStorageNode(COOLER).size());
        assertEquals(1, registry.findByType(InventoryType.RETAIL).size());
        assertEquals(List.of(COOLER, WAREHOUSE), registry.descendantsOf(DISTRIBUTION_CENTER).stream()
                .map(StorageNode::id)
                .toList());
        assertEquals(List.of(COOLER, DISTRIBUTION_CENTER), registry.ancestorsInclusive(COOLER));
    }

    @Test
    void inputOrderingDoesNotAffectRegistryOrdering() {
        EconomicActorRegistry actors = actors();
        InventoryRegistry first = InventoryRegistry.of(
                containers(), storageNodes(), actors.goodRegistry(), actors
        );
        InventoryRegistry second = InventoryRegistry.of(
                containers().reversed(), storageNodes().reversed(), actors.goodRegistry(), actors
        );

        assertEquals(first.containers(), second.containers());
        assertEquals(first.storageNodes(), second.storageNodes());
    }

    @Test
    void builderAndRegistryRejectDuplicateInventoryAndStorageIds() {
        EconomicActorRegistry actors = actors();
        InventoryContainer container = containers().getFirst();
        StorageNode node = storageNodes().getFirst();

        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.builder(
                actors.goodRegistry(), actors
        ).registerStorageNodes(storageNodes()).registerContainer(container).registerContainer(container));
        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.builder(
                actors.goodRegistry(), actors
        ).registerStorageNode(node).registerStorageNode(node));
        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.of(
                List.of(container, container), storageNodes(), actors.goodRegistry(), actors
        ));
        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.of(
                containers(), List.of(node, node), actors.goodRegistry(), actors
        ));
    }

    @Test
    void registryRejectsUnknownActorsStorageNodesAndParents() {
        EconomicActorRegistry actors = actors();
        InventoryContainer unknownActor = InventoryContainer.builder()
                .id("test:unknown_actor_inventory")
                .displayName("Unknown Actor")
                .ownerActorId(ActorId.of("test:missing"))
                .storageNodeId(COOLER)
                .inventoryType(InventoryType.OTHER)
                .build();
        InventoryContainer unknownNode = InventoryContainer.builder()
                .id("test:unknown_node_inventory")
                .displayName("Unknown Node")
                .ownerActorId(WAREHOUSE_ACTOR)
                .storageNodeId(StorageNodeId.of("test:missing"))
                .inventoryType(InventoryType.OTHER)
                .build();
        StorageNode orphan = StorageNode.builder()
                .id("test:orphan")
                .displayName("Orphan")
                .storageRequirement(StorageRequirement.AMBIENT)
                .parentNodeId(StorageNodeId.of("test:missing"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.of(
                List.of(unknownActor), storageNodes(), actors.goodRegistry(), actors
        ));
        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.of(
                List.of(unknownNode), storageNodes(), actors.goodRegistry(), actors
        ));
        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.of(
                List.of(), List.of(orphan), actors.goodRegistry(), actors
        ));
    }

    @Test
    void registryRejectsCircularStorageHierarchyAndOversizedChildren() {
        EconomicActorRegistry actors = actors();
        StorageNode first = storageNode("test:first", "test:second", StorageCapacity.unlimited());
        StorageNode second = storageNode("test:second", "test:first", StorageCapacity.unlimited());
        StorageNode parent = StorageNode.builder()
                .id("test:parent")
                .displayName("Parent")
                .storageRequirement(StorageRequirement.AMBIENT)
                .capacity(StorageCapacity.builder().maximumWeight(10, UnitOfMeasure.KILOGRAM).build())
                .build();
        StorageNode oversizedChild = storageNode(
                "test:child",
                "test:parent",
                StorageCapacity.builder().maximumWeight(30, UnitOfMeasure.POUND).build()
        );

        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.of(
                List.of(), List.of(first, second), actors.goodRegistry(), actors
        ));
        assertThrows(IllegalArgumentException.class, () -> InventoryRegistry.of(
                List.of(), List.of(parent, oversizedChild), actors.goodRegistry(), actors
        ));
    }

    private static StorageNode storageNode(String id, String parentId, StorageCapacity capacity) {
        return StorageNode.builder()
                .id(id)
                .displayName(id)
                .storageRequirement(StorageRequirement.AMBIENT)
                .capacity(capacity)
                .parentNodeId(StorageNodeId.of(parentId))
                .build();
    }
}
