package com.butchercraft.world.allocation;

public enum AllocationCycleFailureScope {
    CYCLE,
    ALLOCATION_SET,
    REQUIREMENT,
    PUBLICATION;

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
