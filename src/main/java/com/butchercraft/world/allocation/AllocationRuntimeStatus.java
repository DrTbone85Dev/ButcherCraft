package com.butchercraft.world.allocation;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum AllocationRuntimeStatus {
    REQUESTED,
    WAITING,
    ALLOCATED,
    ACTIVE,
    RELEASED,
    FAILED,
    EXPIRED;

    public String serializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isTerminal() {
        return this == RELEASED || this == FAILED || this == EXPIRED;
    }

    public Set<AllocationRuntimeStatus> allowedNextStatuses() {
        return switch (this) {
            case REQUESTED -> ordered(WAITING, ALLOCATED, FAILED, EXPIRED);
            case WAITING -> ordered(ALLOCATED, FAILED, EXPIRED);
            case ALLOCATED -> ordered(ACTIVE, RELEASED, FAILED, EXPIRED);
            case ACTIVE -> ordered(RELEASED, FAILED, EXPIRED);
            case RELEASED, FAILED, EXPIRED -> Collections.emptySet();
        };
    }

    private static Set<AllocationRuntimeStatus> ordered(
            AllocationRuntimeStatus first,
            AllocationRuntimeStatus... remaining
    ) {
        return Collections.unmodifiableSet(EnumSet.of(first, remaining));
    }
}
