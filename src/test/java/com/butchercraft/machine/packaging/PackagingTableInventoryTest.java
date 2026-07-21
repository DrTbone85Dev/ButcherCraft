package com.butchercraft.machine.packaging;

import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationInventory;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingTableInventoryTest {
    @Test
    void packagingTableCapabilityDefinesThreeInputsAndOneResultSlot() {
        WorkstationInventory inventory = new WorkstationInventory(PackagingTableWorkstation.capability(), () -> {});

        assertEquals(3, inventory.inputSlotCount());
        assertEquals(1, inventory.outputSlotCount());
        assertEquals(4, inventory.totalSlotCount());
        assertEquals(3, inventory.firstOutputSlot());
        assertEquals(List.of(3), inventory.outputSlotRange());
        assertTrue(inventory.isInputSlot(0));
        assertTrue(inventory.isInputSlot(1));
        assertTrue(inventory.isInputSlot(2));
        assertTrue(inventory.isOutputSlot(3));
    }

    @Test
    void packagingInputSlotsAcceptItemsAndResultSlotRejectsInsertion() {
        WorkstationInventory inventory = new WorkstationInventory(PackagingTableWorkstation.capability(), () -> {});
        inventory.setInputSlotValidator((slot, stack) -> slot == 0
                ? stack.getItem() == ModItems.GROUND_BEEF_TEST.get()
                : PackagingSupplyItemMappings.isKnownSupplyItem(stack));
        ItemStack meat = ModItems.GROUND_BEEF_TEST.get().getDefaultInstance();
        ItemStack tray = new ItemStack(ModItems.FOAM_TRAY.get());
        ItemStack wrap = new ItemStack(ModItems.PLASTIC_WRAP_ROLL.get());

        assertTrue(inventory.insertItem(0, meat, false).isEmpty());
        assertTrue(inventory.insertItem(1, tray, false).isEmpty());
        assertTrue(inventory.insertItem(2, wrap, false).isEmpty());
        assertEquals(1, inventory.inputs().get(0).getCount());
        assertEquals(1, inventory.inputs().get(1).getCount());
        assertEquals(1, inventory.inputs().get(2).getCount());

        ItemStack rejectedResult = inventory.insertItem(3, new ItemStack(ModItems.FOAM_TRAY.get()), false);

        assertEquals(1, rejectedResult.getCount());
        assertTrue(inventory.output().isEmpty());
    }

    @Test
    void packagingInventorySerializationPreservesAllSlots() {
        WorkstationInventory source = new WorkstationInventory(PackagingTableWorkstation.capability(), () -> {});
        source.setInputValidator(stack -> !stack.isEmpty());
        source.setInputInternal(0, ModItems.BEEF_TRIM_TEST.get().getDefaultInstance());
        source.setInputInternal(1, new ItemStack(ModItems.FOAM_TRAY.get()));
        source.setInputInternal(2, new ItemStack(ModItems.PLASTIC_WRAP_ROLL.get()));
        source.setOutputInternal(ModItems.RETAIL_GROUND_BEEF_TEST.get().getDefaultInstance());

        CompoundTag saved = source.serializeNBT(RegistryAccess.EMPTY);
        WorkstationInventory restored = new WorkstationInventory(PackagingTableWorkstation.capability(), () -> {});
        restored.deserializeNBT(RegistryAccess.EMPTY, saved);

        assertFalse(restored.getStackInSlot(0).isEmpty());
        assertFalse(restored.getStackInSlot(1).isEmpty());
        assertFalse(restored.getStackInSlot(2).isEmpty());
        assertFalse(restored.getStackInSlot(3).isEmpty());
    }

    @Test
    void multiInputInventoryNotifiesOwnerWhenCleared() {
        AtomicInteger changes = new AtomicInteger();
        WorkstationInventory inventory = new WorkstationInventory(PackagingTableWorkstation.capability(), changes::incrementAndGet);
        inventory.setInputInternal(0, ModItems.GROUND_BEEF_TEST.get().getDefaultInstance());
        inventory.setInputInternal(1, new ItemStack(ModItems.FOAM_TRAY.get()));
        inventory.setInputInternal(2, new ItemStack(ModItems.PLASTIC_WRAP_ROLL.get()));

        inventory.clearInputsInternal();

        assertTrue(inventory.inputs().stream().allMatch(ItemStack::isEmpty));
        assertEquals(4, changes.get());
    }
}
