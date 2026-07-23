package com.butchercraft.world.allocation;

public record CapacityUnitId(String value) implements Comparable<CapacityUnitId> {
    public CapacityUnitId {
        value = AllocationValidation.id(value, "capacityUnitId");
    }

    public static CapacityUnitId of(String value) {
        return new CapacityUnitId(value);
    }

    @Override
    public int compareTo(CapacityUnitId other) {
        return value.compareTo(other.value);
    }
}
