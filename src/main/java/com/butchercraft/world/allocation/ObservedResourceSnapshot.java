package com.butchercraft.world.allocation;

import java.util.Comparator;

public record ObservedResourceSnapshot(
        ResourceId resourceId,
        ResourceCategory resourceCategory,
        AllocationProviderId authoritativeProviderId,
        ExternalReference authoritativeExternalReference,
        ResourceAvailability availability,
        ResourceExclusivityMode exclusivityMode,
        long observationSimulationTick,
        AllocationMetadata metadata,
        int schemaVersion
) implements Comparable<ObservedResourceSnapshot> {
    private static final String INDIVIDUAL_WORKER_REFERENCE_TYPE =
            "butchercraft:individual_worker";

    private static final Comparator<ObservedResourceSnapshot> ORDER = Comparator
            .comparingLong(ObservedResourceSnapshot::observationSimulationTick)
            .thenComparing(ObservedResourceSnapshot::authoritativeProviderId)
            .thenComparing(ObservedResourceSnapshot::resourceCategory)
            .thenComparing(ObservedResourceSnapshot::resourceId);

    public ObservedResourceSnapshot {
        resourceId = AllocationValidation.required(resourceId, "resourceId");
        resourceCategory = AllocationValidation.required(resourceCategory, "resourceCategory");
        authoritativeProviderId = AllocationValidation.required(
                authoritativeProviderId,
                "authoritativeProviderId"
        );
        authoritativeExternalReference = AllocationValidation.required(
                authoritativeExternalReference,
                "authoritativeExternalReference"
        );
        if (ResourceCategories.WORKFORCE.equals(resourceCategory)
                && INDIVIDUAL_WORKER_REFERENCE_TYPE.equals(
                authoritativeExternalReference.referenceTypeId()
        )) {
            throw AllocationValidation.failure(
                    AllocationValidationFailureCode.UNSUPPORTED_SCHEMA_CONCEPT,
                    "authoritativeExternalReference.referenceTypeId",
                    "Allocation schema 1 supports aggregate workforce resources only"
            );
        }
        availability = AllocationValidation.required(availability, "availability");
        exclusivityMode = AllocationValidation.required(exclusivityMode, "exclusivityMode");
        observationSimulationTick = AllocationValidation.tick(
                observationSimulationTick,
                "observationSimulationTick"
        );
        metadata = AllocationValidation.required(metadata, "metadata");
        schemaVersion = AllocationValidation.schema(schemaVersion);
    }

    @Override
    public int compareTo(ObservedResourceSnapshot other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.resource(this);
    }
}
