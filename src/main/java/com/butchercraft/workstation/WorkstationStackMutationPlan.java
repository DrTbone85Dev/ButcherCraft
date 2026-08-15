package com.butchercraft.workstation;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Immutable Workstation-owned candidate for one exact slot mutation. */
public record WorkstationStackMutationPlan(
        ItemStack preStack,
        ItemStack transferStack,
        ItemStack postStack,
        int requestedQuantity,
        int effectiveCapacity
) {
    public WorkstationStackMutationPlan {
        preStack = Objects.requireNonNull(preStack, "preStack").copy();
        transferStack = Objects.requireNonNull(transferStack, "transferStack").copy();
        postStack = Objects.requireNonNull(postStack, "postStack").copy();
        if (requestedQuantity <= 0) throw new IllegalArgumentException("Requested quantity must be positive");
        if (effectiveCapacity <= 0) throw new IllegalArgumentException("Effective capacity must be positive");
    }

    public static WorkstationStackMutationPlan withdrawal(ItemStack source, int quantity, int slotCapacity) {
        Objects.requireNonNull(source, "source");
        if (source.isEmpty()) throw new IllegalArgumentException("Source stack must not be empty");
        if (quantity <= 0) throw new IllegalArgumentException("Requested quantity must be positive");
        if (quantity > source.getCount()) throw new IllegalArgumentException("Source quantity is insufficient");
        int capacity = effectiveCapacity(source, slotCapacity);
        if (source.getCount() > capacity) throw new IllegalArgumentException("Source exceeds effective slot capacity");

        ItemStack transfer = source.copyWithCount(quantity);
        ItemStack remainder = quantity == source.getCount()
                ? ItemStack.EMPTY
                : source.copyWithCount(source.getCount() - quantity);
        return new WorkstationStackMutationPlan(source, transfer, remainder, quantity, capacity);
    }

    public static WorkstationStackMutationPlan merge(
            ItemStack destination,
            ItemStack payload,
            int slotCapacity
    ) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(payload, "payload");
        if (payload.isEmpty()) throw new IllegalArgumentException("Merge payload must not be empty");
        int capacity = effectiveCapacity(payload, slotCapacity);
        if (!destination.isEmpty() && !ItemStack.isSameItemSameComponents(destination, payload)) {
            throw new IllegalArgumentException("Destination and payload stacks are incompatible");
        }
        int destinationCount = destination.isEmpty() ? 0 : destination.getCount();
        int mergedCount = Math.addExact(destinationCount, payload.getCount());
        if (mergedCount > capacity) throw new IllegalArgumentException("Destination capacity is insufficient");

        ItemStack merged = payload.copyWithCount(mergedCount);
        return new WorkstationStackMutationPlan(destination, payload, merged, payload.getCount(), capacity);
    }

    public static int effectiveCapacity(ItemStack stack, int workstationCapacity) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) throw new IllegalArgumentException("Capacity requires a non-empty stack");
        if (workstationCapacity <= 0) throw new IllegalArgumentException("Workstation capacity must be positive");
        return Math.min(stack.getMaxStackSize(), workstationCapacity);
    }
}
