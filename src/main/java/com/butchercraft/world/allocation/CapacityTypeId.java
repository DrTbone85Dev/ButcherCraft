package com.butchercraft.world.allocation;

public record CapacityTypeId(String value) implements Comparable<CapacityTypeId> {
    public CapacityTypeId {
        value = AllocationValidation.id(value, "capacityTypeId");
    }

    public static CapacityTypeId of(String value) {
        return new CapacityTypeId(value);
    }

    @Override
    public int compareTo(CapacityTypeId other) {
        return value.compareTo(other.value);
    }
}
