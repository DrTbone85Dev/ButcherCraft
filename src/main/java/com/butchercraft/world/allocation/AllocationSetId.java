package com.butchercraft.world.allocation;

public record AllocationSetId(String value) implements Comparable<AllocationSetId> {
    public AllocationSetId {
        value = AllocationValidation.id(value, "allocationSetId");
    }

    public static AllocationSetId of(String value) {
        return new AllocationSetId(value);
    }

    @Override
    public int compareTo(AllocationSetId other) {
        return value.compareTo(other.value);
    }
}
