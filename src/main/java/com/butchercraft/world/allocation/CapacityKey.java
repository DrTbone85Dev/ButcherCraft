package com.butchercraft.world.allocation;

import java.util.Comparator;

public record CapacityKey(
        ResourceId resourceId,
        CapacityTypeId capacityTypeId,
        CapacityUnitId capacityUnitId
) implements Comparable<CapacityKey> {
    private static final Comparator<CapacityKey> ORDER = Comparator
            .comparing(CapacityKey::resourceId)
            .thenComparing(CapacityKey::capacityTypeId)
            .thenComparing(CapacityKey::capacityUnitId);

    public CapacityKey {
        resourceId = AllocationValidation.required(resourceId, "resourceId");
        capacityTypeId = AllocationValidation.required(capacityTypeId, "capacityTypeId");
        capacityUnitId = AllocationValidation.required(capacityUnitId, "capacityUnitId");
    }

    @Override
    public int compareTo(CapacityKey other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
