package com.butchercraft.world.allocation;

import java.util.List;

public record AllocationObservationContext(
        long simulationTick,
        List<ResourceCategory> requestedResourceCategories,
        List<CapacityTypeId> requestedCapacityTypeIds,
        AllocationMetadata scopeMetadata,
        int maximumResourcesPerProvider,
        int maximumCapacitiesPerProvider,
        int schemaVersion
) {
    public AllocationObservationContext {
        simulationTick = AllocationValidation.tick(simulationTick, "simulationTick");
        requestedResourceCategories = AllocationProviderValidation.canonical(
                requestedResourceCategories,
                AllocationSchema.MAXIMUM_PROVIDER_CAPABILITY_DECLARATIONS,
                "requestedResourceCategories"
        );
        requestedCapacityTypeIds = AllocationProviderValidation.canonical(
                requestedCapacityTypeIds,
                AllocationSchema.MAXIMUM_PROVIDER_CAPABILITY_DECLARATIONS,
                "requestedCapacityTypeIds"
        );
        scopeMetadata = AllocationValidation.required(scopeMetadata, "scopeMetadata");
        maximumResourcesPerProvider = AllocationProviderValidation.limit(
                maximumResourcesPerProvider,
                AllocationSchema.MAXIMUM_PROVIDER_RESOURCES,
                "maximumResourcesPerProvider"
        );
        maximumCapacitiesPerProvider = AllocationProviderValidation.limit(
                maximumCapacitiesPerProvider,
                AllocationSchema.MAXIMUM_PROVIDER_CAPACITIES,
                "maximumCapacitiesPerProvider"
        );
        schemaVersion = AllocationValidation.schema(schemaVersion);
    }

    public static AllocationObservationContext allAtTick(long simulationTick) {
        return new AllocationObservationContext(
                simulationTick,
                List.of(),
                List.of(),
                AllocationMetadata.empty(),
                AllocationSchema.MAXIMUM_PROVIDER_RESOURCES,
                AllocationSchema.MAXIMUM_PROVIDER_CAPACITIES,
                AllocationSchema.CURRENT_VERSION
        );
    }

    public boolean requests(ResourceCategory category) {
        ResourceCategory value = AllocationValidation.required(category, "category");
        return requestedResourceCategories.isEmpty()
                || requestedResourceCategories.contains(value);
    }

    public boolean requests(CapacityTypeId capacityTypeId) {
        CapacityTypeId value = AllocationValidation.required(
                capacityTypeId,
                "capacityTypeId"
        );
        return requestedCapacityTypeIds.isEmpty()
                || requestedCapacityTypeIds.contains(value);
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.context(this);
    }
}
