package com.butchercraft.world.inventory;

import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.butchercraft.world.inventory.InventoryTestFixtures.BEEF;
import static com.butchercraft.world.inventory.InventoryTestFixtures.BEEF_INVENTORY;
import static com.butchercraft.world.inventory.InventoryTestFixtures.COOLER;
import static com.butchercraft.world.inventory.InventoryTestFixtures.FARM_ACTOR;
import static com.butchercraft.world.inventory.InventoryTestFixtures.GRAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryDefinitionTest {
    @Test
    void containerAndStorageNodeBuildersCreateImmutableDefinitions() {
        StorageNode node = StorageNode.builder()
                .id(COOLER)
                .displayName(" Retail Cooler ")
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .capacity(StorageCapacity.builder().maximumWeight(100, UnitOfMeasure.POUND).build())
                .build();
        InventoryContainer container = InventoryContainer.builder()
                .id(BEEF_INVENTORY)
                .displayName(" Beef Inventory ")
                .ownerActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .storageNodeId(COOLER)
                .inventoryType(InventoryType.RETAIL)
                .capacity(StorageCapacity.builder().maximumWeight(50, UnitOfMeasure.POUND).build())
                .build();

        assertEquals("Retail Cooler", node.displayName());
        assertEquals("Beef Inventory", container.displayName());
        assertEquals(InventorySchema.CURRENT_VERSION, node.schemaVersion());
        assertEquals(InventorySchema.CURRENT_VERSION, container.schemaVersion());
        assertEquals(InventoryType.RETAIL, container.inventoryType());
        assertTrue(node.parentNodeId().isEmpty());
    }

    @Test
    void definitionsRejectInvalidIdsNamesSelfParentsAndSchemas() {
        assertThrows(IllegalArgumentException.class, () -> InventoryId.of("Invalid Inventory"));
        assertThrows(IllegalArgumentException.class, () -> StorageNodeId.of("Invalid Node"));
        assertThrows(IllegalArgumentException.class, () -> StorageNode.builder()
                .id(COOLER)
                .displayName(" ")
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .build());
        assertThrows(IllegalArgumentException.class, () -> StorageNode.builder()
                .id(COOLER)
                .displayName("Cooler")
                .storageRequirement(StorageRequirement.REFRIGERATED)
                .parentNodeId(COOLER)
                .build());
        assertThrows(IllegalArgumentException.class, () -> InventoryContainer.builder()
                .id(BEEF_INVENTORY)
                .displayName("Beef")
                .ownerActorId(InventoryTestFixtures.WAREHOUSE_ACTOR)
                .storageNodeId(COOLER)
                .inventoryType(InventoryType.RETAIL)
                .schemaVersion(99)
                .build());
    }

    @Test
    void capacityValidatesWeightVolumeUnitsAndDistinctGoodsExactly() {
        StorageCapacity weight = StorageCapacity.builder()
                .maximumWeight(1, UnitOfMeasure.KILOGRAM)
                .build();
        weight.validateEntries(List.of(new InventoryEntry(BEEF, 2, UnitOfMeasure.POUND)), "weight test");
        assertThrows(IllegalArgumentException.class, () -> weight.validateEntries(
                List.of(new InventoryEntry(BEEF, 3, UnitOfMeasure.POUND)),
                "weight test"
        ));

        StorageCapacity volume = StorageCapacity.builder()
                .maximumVolume(1, UnitOfMeasure.GALLON)
                .build();
        volume.validateEntries(List.of(new InventoryEntry(GRAIN, 3, UnitOfMeasure.LITER)), "volume test");
        assertThrows(IllegalArgumentException.class, () -> volume.validateEntries(
                List.of(new InventoryEntry(GRAIN, 4, UnitOfMeasure.LITER)),
                "volume test"
        ));

        StorageCapacity discrete = StorageCapacity.builder()
                .maximumUnits(2)
                .maximumDistinctGoods(1)
                .build();
        discrete.validateEntries(List.of(new InventoryEntry(InventoryTestFixtures.BOX, 2, UnitOfMeasure.EACH)), "units");
        assertThrows(IllegalArgumentException.class, () -> discrete.validateEntries(
                List.of(new InventoryEntry(InventoryTestFixtures.BOX, 3, UnitOfMeasure.EACH)),
                "units"
        ));
    }

    @Test
    void capacityRejectsInvalidLimitUnitsAndNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> StorageCapacity.builder()
                .maximumWeight(10, UnitOfMeasure.LITER)
                .build());
        assertThrows(IllegalArgumentException.class, () -> StorageCapacity.builder()
                .maximumVolume(10, UnitOfMeasure.POUND)
                .build());
        assertThrows(IllegalArgumentException.class, () -> StorageCapacity.builder().maximumUnits(-1).build());
        assertThrows(IllegalArgumentException.class, () -> StorageCapacity.builder().maximumDistinctGoods(-1).build());
    }

    @Test
    void entryMetadataIsTypedImmutableAndValidated() {
        InventoryEntryMetadata metadata = new InventoryEntryMetadata(
                Optional.of(" LOT-42 "),
                OptionalLong.of(100L),
                OptionalInt.of(9_000),
                Optional.of(FARM_ACTOR)
        );
        InventoryEntry entry = new InventoryEntry(GRAIN, 5, UnitOfMeasure.BUSHEL, metadata);

        assertEquals("LOT-42", entry.metadata().lotNumber().orElseThrow());
        assertEquals(100L, entry.metadata().expirationSimulationTick().orElseThrow());
        assertEquals(9_000, entry.metadata().qualityBasisPoints().orElseThrow());
        assertEquals(FARM_ACTOR, entry.metadata().originActorId().orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> new InventoryEntry(GRAIN, -1, UnitOfMeasure.BUSHEL));
        assertThrows(IllegalArgumentException.class, () -> new InventoryEntryMetadata(
                Optional.of(" "), OptionalLong.empty(), OptionalInt.empty(), Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new InventoryEntryMetadata(
                Optional.empty(), OptionalLong.of(-1), OptionalInt.empty(), Optional.empty()
        ));
        assertThrows(IllegalArgumentException.class, () -> new InventoryEntryMetadata(
                Optional.empty(), OptionalLong.empty(), OptionalInt.of(10_001), Optional.empty()
        ));
    }

    @Test
    void serializedEnumsRejectUnknownNames() {
        assertEquals(InventoryType.PLAYER, InventoryType.fromSerializedName("player"));
        assertEquals(InventoryStatus.IN_TRANSIT, InventoryStatus.fromSerializedName("in_transit"));
        assertThrows(IllegalArgumentException.class, () -> InventoryType.fromSerializedName("unknown"));
        assertThrows(IllegalArgumentException.class, () -> InventoryStatus.fromSerializedName("unknown"));
        assertFalse(InventoryStatus.LOCKED.canReceive());
    }
}
