package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.List;

public record AllocationObservationResult(
        AllocationProviderId providerId,
        long simulationTick,
        List<ObservedResourceSnapshot> resources,
        List<ObservedCapacitySnapshot> capacities,
        List<AllocationProviderFailure> failures,
        List<AllocationProviderWarning> warnings,
        int schemaVersion
) implements Comparable<AllocationObservationResult> {
    public AllocationObservationResult {
        providerId = AllocationValidation.required(providerId, "providerId");
        simulationTick = AllocationValidation.tick(simulationTick, "simulationTick");
        resources = AllocationProviderValidation.canonical(
                resources,
                ObservedResourceSnapshot::resourceId,
                Comparator.naturalOrder(),
                AllocationSchema.MAXIMUM_PROVIDER_RESOURCES,
                "resources"
        );
        capacities = AllocationProviderValidation.canonical(
                capacities,
                ObservedCapacitySnapshot::capacityId,
                Comparator.naturalOrder(),
                AllocationSchema.MAXIMUM_PROVIDER_CAPACITIES,
                "capacities"
        );
        failures = AllocationProviderValidation.canonical(
                failures,
                AllocationSchema.MAXIMUM_PROVIDER_FAILURES,
                "failures"
        );
        warnings = AllocationProviderValidation.canonical(
                warnings,
                AllocationSchema.MAXIMUM_PROVIDER_WARNINGS,
                "warnings"
        );
        schemaVersion = AllocationValidation.schema(schemaVersion);
        if (!failures.isEmpty()
                && (!resources.isEmpty() || !capacities.isEmpty() || !warnings.isEmpty())) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                    AllocationProviderFailureScope.PROVIDER,
                    providerId.value(),
                    "A failed provider result cannot contain snapshots or warnings"
            );
        }
    }

    public static AllocationObservationResult success(
            AllocationProviderId providerId,
            long simulationTick,
            List<ObservedResourceSnapshot> resources,
            List<ObservedCapacitySnapshot> capacities
    ) {
        return success(
                providerId,
                simulationTick,
                resources,
                capacities,
                List.of()
        );
    }

    public static AllocationObservationResult success(
            AllocationProviderId providerId,
            long simulationTick,
            List<ObservedResourceSnapshot> resources,
            List<ObservedCapacitySnapshot> capacities,
            List<AllocationProviderWarning> warnings
    ) {
        return new AllocationObservationResult(
                providerId,
                simulationTick,
                resources,
                capacities,
                List.of(),
                warnings,
                AllocationSchema.CURRENT_VERSION
        );
    }

    public static AllocationObservationResult failure(
            AllocationProviderId providerId,
            long simulationTick,
            List<AllocationProviderFailure> failures
    ) {
        if (AllocationValidation.required(failures, "failures").isEmpty()) {
            throw new IllegalArgumentException("Failed provider result requires a failure");
        }
        return new AllocationObservationResult(
                providerId,
                simulationTick,
                List.of(),
                List.of(),
                failures,
                List.of(),
                AllocationSchema.CURRENT_VERSION
        );
    }

    public boolean successful() {
        return failures.isEmpty();
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.result(this);
    }

    @Override
    public int compareTo(AllocationObservationResult other) {
        return providerId.compareTo(
                AllocationValidation.required(other, "other").providerId
        );
    }
}
