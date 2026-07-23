package com.butchercraft.world.inventory;

import com.butchercraft.world.goods.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.butchercraft.world.inventory.InventoryTestFixtures.BEEF;
import static com.butchercraft.world.inventory.InventoryTestFixtures.BEEF_INVENTORY;
import static com.butchercraft.world.inventory.InventoryTestFixtures.BOX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InventoryRuntimeTest {
    @Test
    void runtimeAddsMergesRemovesSortsAndTracksStatus() {
        InventoryRuntime runtime = InventoryRuntime.empty(BEEF_INVENTORY);
        InventoryEntry beef = new InventoryEntry(BEEF, 10, UnitOfMeasure.POUND);

        runtime.addEntry(new InventoryEntry(BOX, 2, UnitOfMeasure.EACH), 1L);
        runtime.addEntry(beef, 2L);
        runtime.addEntry(new InventoryEntry(BEEF, 5, UnitOfMeasure.POUND), 3L);

        assertEquals(List.of(BEEF, BOX), runtime.entries().stream().map(InventoryEntry::goodId).toList());
        assertEquals(15L, runtime.quantityOf(BEEF));
        runtime.removeEntry(new InventoryEntry(BEEF, 6, UnitOfMeasure.POUND), 4L);
        assertEquals(9L, runtime.quantityOf(BEEF));
        runtime.transitionTo(InventoryStatus.LOCKED, 5L);
        assertEquals(InventoryStatus.LOCKED, runtime.status());
        assertEquals(5L, runtime.lastSimulationTick());
    }

    @Test
    void runtimeRejectsDuplicateEntriesOverRemovalAndBackwardTicks() {
        InventoryEntry beef = new InventoryEntry(BEEF, 10, UnitOfMeasure.POUND);
        assertThrows(IllegalArgumentException.class, () -> new InventoryRuntime(
                BEEF_INVENTORY,
                InventoryStatus.ACTIVE,
                List.of(beef, beef.withQuantity(5)),
                0L,
                InventorySchema.CURRENT_VERSION
        ));

        InventoryRuntime runtime = new InventoryRuntime(
                BEEF_INVENTORY,
                InventoryStatus.ACTIVE,
                List.of(beef),
                10L,
                InventorySchema.CURRENT_VERSION
        );
        assertThrows(IllegalArgumentException.class, () -> runtime.removeEntry(
                new InventoryEntry(BEEF, 11, UnitOfMeasure.POUND),
                11L
        ));
        assertThrows(IllegalArgumentException.class, () -> runtime.transitionTo(InventoryStatus.ACTIVE, 9L));
        assertThrows(IllegalArgumentException.class, () -> new InventoryRuntime(
                BEEF_INVENTORY,
                InventoryStatus.ACTIVE,
                List.of(),
                -1L,
                InventorySchema.CURRENT_VERSION
        ));
    }
}
