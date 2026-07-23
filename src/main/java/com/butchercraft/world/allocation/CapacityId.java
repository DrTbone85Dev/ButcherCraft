package com.butchercraft.world.allocation;

public record CapacityId(String value) implements Comparable<CapacityId> {
    public CapacityId {
        value = AllocationValidation.id(value, "capacityId");
    }

    public static CapacityId of(String value) {
        return new CapacityId(value);
    }

    @Override
    public int compareTo(CapacityId other) {
        return value.compareTo(other.value);
    }
}
