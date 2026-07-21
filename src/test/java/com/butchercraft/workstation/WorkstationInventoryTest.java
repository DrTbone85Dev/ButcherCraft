package com.butchercraft.workstation;

import com.butchercraft.machine.bandsaw.BandsawWorkstation;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.machine.packaging.PackagingTableWorkstation;
import com.butchercraft.registration.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationInventoryTest {
    @Test
    void configuredLayoutsMatchMachineCapabilities() {
        WorkstationInventory development = new WorkstationInventory(DevelopmentWorkstationFixtures.capability(), () -> {});
        WorkstationInventory grinder = new WorkstationInventory(GrinderWorkstation.capability(), () -> {});
        WorkstationInventory bandsaw = new WorkstationInventory(BandsawWorkstation.capability(), () -> {});
        WorkstationInventory packagingTable = new WorkstationInventory(PackagingTableWorkstation.capability(), () -> {});

        assertEquals(2, development.totalSlotCount());
        assertEquals(2, development.getSlots());
        assertEquals(List.of(1), development.outputSlotRange());

        assertEquals(2, grinder.totalSlotCount());
        assertEquals(2, grinder.getSlots());
        assertEquals(List.of(1), grinder.outputSlotRange());

        assertEquals(9, bandsaw.totalSlotCount());
        assertEquals(9, bandsaw.getSlots());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8), bandsaw.outputSlotRange());

        assertEquals(4, packagingTable.totalSlotCount());
        assertEquals(4, packagingTable.getSlots());
        assertEquals(3, packagingTable.inputSlotCount());
        assertEquals(List.of(3), packagingTable.outputSlotRange());
    }

    @Test
    void slotClassificationUsesConfiguredRanges() {
        WorkstationInventory grinder = new WorkstationInventory(GrinderWorkstation.capability(), () -> {});
        WorkstationInventory bandsaw = new WorkstationInventory(BandsawWorkstation.capability(), () -> {});
        WorkstationInventory packagingTable = new WorkstationInventory(PackagingTableWorkstation.capability(), () -> {});

        assertTrue(grinder.isInputSlot(0));
        assertTrue(grinder.isOutputSlot(1));
        assertFalse(grinder.isOutputSlot(2));

        assertTrue(bandsaw.isInputSlot(0));
        for (int slot = 1; slot <= 8; slot++) {
            assertTrue(bandsaw.isOutputSlot(slot));
        }
        assertFalse(bandsaw.isOutputSlot(9));

        assertTrue(packagingTable.isInputSlot(0));
        assertTrue(packagingTable.isInputSlot(1));
        assertTrue(packagingTable.isInputSlot(2));
        assertTrue(packagingTable.isOutputSlot(3));
        assertFalse(packagingTable.isOutputSlot(4));
    }

    @Test
    void twoSlotInventoryOutputsNeverInspectSlotTwo() {
        WorkstationInventory inventory = new WorkstationInventory(GrinderWorkstation.capability(), () -> {});
        inventory.setOutputInternal(ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());

        List<ItemStack> outputs = inventory.outputs();

        assertEquals(1, outputs.size());
        assertEquals(2, inventory.getSlots());
        assertThrows(RuntimeException.class, () -> inventory.getStackInSlot(2));
        assertThrows(UnsupportedOperationException.class, () -> outputs.add(ItemStack.EMPTY));
    }

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

    @Test
    void smallerInventoryIgnoresOutOfRangeSavedSlots() {
        WorkstationInventory inventory = new WorkstationInventory(GrinderWorkstation.capability(), () -> {});
        CompoundTag saved = new CompoundTag();
        saved.putInt("Size", 9);
        ListTag items = new ListTag();
        CompoundTag outOfRangeItem = (CompoundTag) ModItems.GROUND_BEEF_TEST.get()
                .getDefaultInstance()
                .save(RegistryAccess.EMPTY, new CompoundTag());
        outOfRangeItem.putInt("Slot", 2);
        items.add(outOfRangeItem);
        saved.put("Items", items);

        inventory.deserializeNBT(RegistryAccess.EMPTY, saved);

        assertEquals(2, inventory.getSlots());
        assertTrue(inventory.outputsEmpty());
        assertThrows(RuntimeException.class, () -> inventory.getStackInSlot(2));
    }

    @Test
    void outputCountIsBoundedByConfiguredLayout() {
        WorkstationInventory grinder = new WorkstationInventory(GrinderWorkstation.capability(), () -> {});
        WorkstationInventory bandsaw = new WorkstationInventory(BandsawWorkstation.capability(), () -> {});

        assertThrows(IllegalArgumentException.class, () -> grinder.setOutputsInternal(List.of(
                ModItems.GROUND_BEEF_TEST.get().getDefaultInstance(),
                ModItems.GROUND_PORK_TEST.get().getDefaultInstance()
        )));
        bandsaw.setOutputsInternal(List.of(
                ModItems.BEEF_CHUCK_TEST.get().getDefaultInstance(),
                ModItems.BEEF_RIB_TEST.get().getDefaultInstance(),
                ModItems.BEEF_PACKER_BRISKET_TEST.get().getDefaultInstance(),
                ModItems.BEEF_PLATE_TEST.get().getDefaultInstance(),
                ModItems.BEEF_SHANK_TEST.get().getDefaultInstance(),
                ModItems.BEEF_TRIM_TEST.get().getDefaultInstance(),
                ModItems.BEEF_FAT_TEST.get().getDefaultInstance(),
                ModItems.BEEF_BONE_TEST.get().getDefaultInstance()
        ));

        assertEquals(8, bandsaw.outputs().stream().filter(stack -> !stack.isEmpty()).count());
    }
}
