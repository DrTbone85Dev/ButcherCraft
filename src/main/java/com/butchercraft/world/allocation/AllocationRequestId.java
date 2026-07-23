package com.butchercraft.world.allocation;

public record AllocationRequestId(String value) implements Comparable<AllocationRequestId> {
    public AllocationRequestId {
        value = AllocationValidation.id(value, "allocationRequestId");
    }

    public static AllocationRequestId of(String value) {
        return new AllocationRequestId(value);
    }

    @Override
    public int compareTo(AllocationRequestId other) {
        return value.compareTo(other.value);
    }
}
