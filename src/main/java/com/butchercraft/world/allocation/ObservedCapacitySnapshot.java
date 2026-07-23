package com.butchercraft.world.allocation;

import java.util.Comparator;

public record ObservedCapacitySnapshot(
        CapacityId capacityId,
        ResourceId resourceId,
        CapacityTypeId capacityTypeId,
        AllocationQuantity observedAmount,
        CapacityUnitId capacityUnitId,
        long observationSimulationTick,
        ExternalReference authoritativeExternalReference,
        AllocationMetadata metadata,
        int schemaVersion
) implements Comparable<ObservedCapacitySnapshot> {
    private static final Comparator<ObservedCapacitySnapshot> ORDER = Comparator
            .comparingLong(ObservedCapacitySnapshot::observationSimulationTick)
            .thenComparing(ObservedCapacitySnapshot::resourceId)
            .thenComparing(ObservedCapacitySnapshot::capacityTypeId)
            .thenComparing(ObservedCapacitySnapshot::capacityUnitId)
            .thenComparing(ObservedCapacitySnapshot::capacityId);

    public ObservedCapacitySnapshot {
        capacityId = AllocationValidation.required(capacityId, "capacityId");
        resourceId = AllocationValidation.required(resourceId, "resourceId");
        capacityTypeId = AllocationValidation.required(capacityTypeId, "capacityTypeId");
        observedAmount = AllocationValidation.required(observedAmount, "observedAmount");
        capacityUnitId = AllocationValidation.required(capacityUnitId, "capacityUnitId");
        if (!observedAmount.unitId().equals(capacityUnitId)) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.INCOMPATIBLE_UNIT,
                    "capacityUnitId",
                    "Observed capacity quantity unit does not match capacityUnitId"
            );
        }
        observationSimulationTick = AllocationValidation.tick(
                observationSimulationTick,
                "observationSimulationTick"
        );
        authoritativeExternalReference = AllocationValidation.required(
                authoritativeExternalReference,
                "authoritativeExternalReference"
        );
        metadata = AllocationValidation.required(metadata, "metadata");
        schemaVersion = AllocationValidation.schema(schemaVersion);
    }

    public CapacityKey capacityKey() {
        return new CapacityKey(resourceId, capacityTypeId, capacityUnitId);
    }

    @Override
    public int compareTo(ObservedCapacitySnapshot other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.capacity(this);
    }
}
