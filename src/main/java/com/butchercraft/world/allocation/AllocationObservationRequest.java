package com.butchercraft.world.allocation;

import java.util.List;

public record AllocationObservationRequest(
        AllocationObservationContext context,
        List<AllocationProviderId> selectedProviderIds,
        int maximumTotalResources,
        int maximumTotalCapacities,
        int schemaVersion
) {
    public AllocationObservationRequest {
        context = AllocationValidation.required(context, "context");
        selectedProviderIds = AllocationProviderValidation.canonical(
                selectedProviderIds,
                AllocationSchema.MAXIMUM_PROVIDERS,
                "selectedProviderIds"
        );
        maximumTotalResources = AllocationProviderValidation.limit(
                maximumTotalResources,
                AllocationSchema.MAXIMUM_OBSERVATION_RESOURCES,
                "maximumTotalResources"
        );
        maximumTotalCapacities = AllocationProviderValidation.limit(
                maximumTotalCapacities,
                AllocationSchema.MAXIMUM_OBSERVATION_CAPACITIES,
                "maximumTotalCapacities"
        );
        schemaVersion = AllocationValidation.schema(schemaVersion);
        if (context.schemaVersion() != schemaVersion) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.SCHEMA_MISMATCH,
                    AllocationProviderFailureScope.REQUEST,
                    "schemaVersion",
                    "Observation request and context schema versions must match"
            );
        }
    }

    public static AllocationObservationRequest all(
            AllocationObservationContext context
    ) {
        return new AllocationObservationRequest(
                context,
                List.of(),
                AllocationSchema.MAXIMUM_OBSERVATION_RESOURCES,
                AllocationSchema.MAXIMUM_OBSERVATION_CAPACITIES,
                AllocationSchema.CURRENT_VERSION
        );
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.request(this);
    }
}
