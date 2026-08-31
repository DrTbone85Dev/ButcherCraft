package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationEndpointStackPayload;

import java.util.Objects;
import java.util.Optional;

public record WorkstationProjectionSlot(
        int slotIndex,
        int configuredCapacity,
        int effectiveCapacity,
        Optional<WorkstationEndpointStackPayload> exactStack
) implements Comparable<WorkstationProjectionSlot> {
    public WorkstationProjectionSlot {
        if (slotIndex < 0) throw new IllegalArgumentException("Projection slot index must not be negative");
        if (configuredCapacity <= 0 || effectiveCapacity <= 0) {
            throw new IllegalArgumentException("Projection slot capacities must be positive");
        }
        if (effectiveCapacity > configuredCapacity) {
            throw new IllegalArgumentException("Effective projection slot capacity exceeds configured capacity");
        }
        exactStack = Objects.requireNonNull(exactStack, "exactStack");
        exactStack.ifPresent(stack -> {
            if (stack.count() > effectiveCapacity) {
                throw new IllegalArgumentException("Projection stack exceeds effective slot capacity");
            }
        });
    }

    @Override
    public int compareTo(WorkstationProjectionSlot other) {
        return Integer.compare(slotIndex, other.slotIndex);
    }
}
