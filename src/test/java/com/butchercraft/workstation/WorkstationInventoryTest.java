package com.butchercraft.workstation;

import com.butchercraft.registration.ModItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationInventoryTest {
    @Test
    void inputAcceptsFixtureProductAndRejectsInvalidItem() {
        WorkstationInventory inventory = new WorkstationInventory(() -> {});

        assertEquals(1, inventory.insertItem(WorkstationInventory.INPUT_SLOT, new ItemStack(ModItems.DEVELOPMENT_TEST_ITEM.get()), true).getCount());
        assertTrue(inventory.input().isEmpty());

        assertTrue(inventory.insertItem(WorkstationInventory.INPUT_SLOT, ModItems.BEEF_TRIM_TEST.get().getDefaultInstance(), false).isEmpty());
        assertEquals(1, inventory.getStackInSlot(WorkstationInventory.INPUT_SLOT).getCount());
        assertEquals(1, inventory.insertItem(WorkstationInventory.INPUT_SLOT, new ItemStack(ModItems.DEVELOPMENT_TEST_ITEM.get()), true).getCount());
    }

    @Test
    void outputRejectsInsertion() {
        WorkstationInventory inventory = new WorkstationInventory(() -> {});

        ItemStack remainder = inventory.insertItem(WorkstationInventory.OUTPUT_SLOT, ModItems.GROUND_BEEF_TEST.get().getDefaultInstance(), false);

        assertEquals(1, remainder.getCount());
        assertTrue(inventory.output().isEmpty());
    }

    @Test
    void inputCannotBeExtractedWhileLocked() {
        WorkstationInventory inventory = new WorkstationInventory(() -> {});
        inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        inventory.setInputLocked(() -> true);

        assertTrue(inventory.extractItem(WorkstationInventory.INPUT_SLOT, 1, false).isEmpty());
        assertEquals(1, inventory.input().getCount());
    }

    @Test
    void outputExtractionRequiresCompletionGate() {
        WorkstationInventory inventory = new WorkstationInventory(() -> {});
        inventory.setOutputInternal(ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());

        assertTrue(inventory.extractItem(WorkstationInventory.OUTPUT_SLOT, 1, false).isEmpty());
        inventory.setOutputExtractionAllowed(() -> true);
        assertEquals(1, inventory.extractItem(WorkstationInventory.OUTPUT_SLOT, 1, false).getCount());
    }

    @Test
    void slotLimitsRemainOneAndChangesNotifyOwner() {
        AtomicInteger changes = new AtomicInteger();
        WorkstationInventory inventory = new WorkstationInventory(changes::incrementAndGet);

        assertEquals(1, inventory.getSlotLimit(WorkstationInventory.INPUT_SLOT));
        inventory.setInputInternal(ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        assertEquals(1, changes.get());
    }
}
