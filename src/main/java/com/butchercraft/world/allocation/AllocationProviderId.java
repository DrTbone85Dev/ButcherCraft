package com.butchercraft.world.allocation;

public record AllocationProviderId(String value) implements Comparable<AllocationProviderId> {
    public AllocationProviderId {
        value = AllocationValidation.id(value, "allocationProviderId");
    }

    public static AllocationProviderId of(String value) {
        return new AllocationProviderId(value);
    }

    @Override
    public int compareTo(AllocationProviderId other) {
        return value.compareTo(other.value);
    }
}
