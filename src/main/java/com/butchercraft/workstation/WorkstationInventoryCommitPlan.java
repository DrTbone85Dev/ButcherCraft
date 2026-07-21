package com.butchercraft.workstation;

import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Minecraft-side inventory mutation plan that commits input consumption and output insertion atomically.
 */
public final class WorkstationInventoryCommitPlan {
    private final WorkstationInventory inventory;
    private final List<Integer> consumedInputSlots;
    private final List<ItemStack> outputStacks;
    private final List<ItemStack> inputSnapshot;
    private final List<ItemStack> outputSnapshot;

    public WorkstationInventoryCommitPlan(
            WorkstationInventory inventory,
            List<Integer> consumedInputSlots,
            List<ItemStack> outputStacks
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.consumedInputSlots = validateConsumedInputSlots(inventory, consumedInputSlots);
        this.outputStacks = List.copyOf(Objects.requireNonNull(outputStacks, "outputStacks"));
        if (this.outputStacks.size() > inventory.outputSlotCount()) {
            throw new IllegalArgumentException("Commit plan has more outputs than workstation output slots");
        }
        this.inputSnapshot = inventory.inputs().stream().map(ItemStack::copy).toList();
        this.outputSnapshot = inventory.outputs().stream().map(ItemStack::copy).toList();
    }

    public void commit() {
        try {
            inventory.clearInputSlotsInternal(consumedInputSlots);
            inventory.setOutputsInternal(outputStacks);
        } catch (RuntimeException exception) {
            rollback();
            throw exception;
        }
    }

    public void rollback() {
        inventory.setInputsInternal(inputSnapshot);
        inventory.setOutputsInternal(outputSnapshot);
    }

    private static List<Integer> validateConsumedInputSlots(WorkstationInventory inventory, List<Integer> slots) {
        Set<Integer> unique = new LinkedHashSet<>(Objects.requireNonNull(slots, "slots"));
        if (unique.size() != slots.size()) {
            throw new IllegalArgumentException("Commit plan cannot consume the same input slot more than once");
        }
        for (int slot : unique) {
            if (!inventory.isInputSlot(slot)) {
                throw new IllegalArgumentException("Commit plan can only consume input slots");
            }
        }
        return List.copyOf(unique);
    }
}
