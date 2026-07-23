package com.butchercraft.world.allocation;

public enum AllocationConflictType {
    CAPACITY,
    EXCLUSIVITY,
    DEPENDENCY,
    CHAIN,
    UNSUPPORTED;

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
