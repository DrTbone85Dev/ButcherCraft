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
    private final List<ItemStack> inputSnapshot;
    private final List<ItemStack> outputSnapshot;
    private final List<ItemStack> postInputs;
    private final List<ItemStack> postOutputs;

    public WorkstationInventoryCommitPlan(
            WorkstationInventory inventory,
            List<Integer> consumedInputSlots,
            List<ItemStack> outputStacks
    ) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.consumedInputSlots = validateConsumedInputSlots(inventory, consumedInputSlots);
        List<ItemStack> copiedOutputs = outputStacks.stream().map(ItemStack::copy).toList();
        if (copiedOutputs.size() > inventory.outputSlotCount()) {
            throw new IllegalArgumentException("Commit plan has more outputs than workstation output slots");
        }
        this.inputSnapshot = inventory.inputs().stream().map(ItemStack::copy).toList();
        this.outputSnapshot = inventory.outputs().stream().map(ItemStack::copy).toList();
        this.postInputs = inputSnapshot.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toList());
        for (int slot : this.consumedInputSlots) {
            ItemStack source = inputSnapshot.get(slot - inventory.firstInputSlot());
            WorkstationStackMutationPlan decrement = WorkstationStackMutationPlan.withdrawal(
                    source,
                    1,
                    inventory.slotCapacityPolicy().capacity(slot)
            );
            postInputs.set(slot - inventory.firstInputSlot(), decrement.postStack());
        }
        this.postOutputs = outputSnapshot.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toList());
        for (int outputIndex = 0; outputIndex < copiedOutputs.size(); outputIndex++) {
            ItemStack payload = copiedOutputs.get(outputIndex);
            if (payload.isEmpty()) {
                throw new IllegalArgumentException("Commit plan output payload must not be empty");
            }
            int slot = inventory.firstOutputSlot() + outputIndex;
            WorkstationStackMutationPlan merge = WorkstationStackMutationPlan.merge(
                    outputSnapshot.get(outputIndex),
                    payload,
                    inventory.slotCapacityPolicy().capacity(slot)
            );
            postOutputs.set(outputIndex, merge.postStack());
        }
    }

    public void commit() {
        if (consumedInputSlots.stream().anyMatch(inventory::isTransferLocked)
                || outputSlots().stream().anyMatch(inventory::isTransferLocked)) {
            throw new IllegalStateException("Workstation operation cannot mutate a transfer-locked endpoint slot");
        }
        if (!exactSnapshots(inventory.inputs(), inputSnapshot)
                || !exactSnapshots(inventory.outputs(), outputSnapshot)) {
            throw new IllegalStateException("Workstation inventory changed after commit-plan validation");
        }
        try {
            inventory.publishInventoryCandidateInternal(postInputs, postOutputs);
        } catch (RuntimeException exception) {
            rollback();
            throw exception;
        }
    }

    public void rollback() {
        inventory.publishInventoryCandidateInternal(inputSnapshot, outputSnapshot);
    }

    public List<ItemStack> postInputs() {
        return postInputs.stream().map(ItemStack::copy).toList();
    }

    public List<ItemStack> postOutputs() {
        return postOutputs.stream().map(ItemStack::copy).toList();
    }

    private List<Integer> outputSlots() {
        return java.util.stream.IntStream.range(0, postOutputs.size())
                .map(index -> inventory.firstOutputSlot() + index)
                .boxed()
                .toList();
    }

    private static boolean exactSnapshots(List<ItemStack> current, List<ItemStack> expected) {
        if (current.size() != expected.size()) return false;
        for (int index = 0; index < current.size(); index++) {
            ItemStack left = current.get(index);
            ItemStack right = expected.get(index);
            if (left.getCount() != right.getCount() || !ItemStack.isSameItemSameComponents(left, right)) {
                return false;
            }
        }
        return true;
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
