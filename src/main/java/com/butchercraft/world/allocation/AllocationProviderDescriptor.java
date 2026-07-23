package com.butchercraft.world.allocation;

import java.util.List;

public record AllocationProviderDescriptor(
        AllocationProviderId providerId,
        List<String> authoritativeSubsystemIds,
        List<ResourceCategory> resourceCategories,
        List<CapacityTypeId> capacityTypeIds,
        List<CapacityUnitId> capacityUnitIds,
        AllocationMetadata metadata,
        int schemaVersion
) implements Comparable<AllocationProviderDescriptor> {
    public AllocationProviderDescriptor {
        providerId = AllocationValidation.required(providerId, "providerId");
        authoritativeSubsystemIds = canonicalSubsystems(authoritativeSubsystemIds);
        resourceCategories = AllocationProviderValidation.canonical(
                resourceCategories,
                AllocationSchema.MAXIMUM_PROVIDER_CAPABILITY_DECLARATIONS,
                "resourceCategories"
        );
        capacityTypeIds = AllocationProviderValidation.canonical(
                capacityTypeIds,
                AllocationSchema.MAXIMUM_PROVIDER_CAPABILITY_DECLARATIONS,
                "capacityTypeIds"
        );
        capacityUnitIds = AllocationProviderValidation.canonical(
                capacityUnitIds,
                AllocationSchema.MAXIMUM_PROVIDER_CAPABILITY_DECLARATIONS,
                "capacityUnitIds"
        );
        metadata = AllocationValidation.required(metadata, "metadata");
        schemaVersion = AllocationValidation.schema(schemaVersion);
        if (authoritativeSubsystemIds.isEmpty()) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                    AllocationProviderFailureScope.REGISTRY,
                    providerId.value(),
                    "Provider descriptor must declare at least one authoritative subsystem"
            );
        }
        if (resourceCategories.isEmpty()) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                    AllocationProviderFailureScope.REGISTRY,
                    providerId.value(),
                    "Provider descriptor must declare at least one Resource category"
            );
        }
        if (capacityTypeIds.isEmpty() != capacityUnitIds.isEmpty()) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                    AllocationProviderFailureScope.REGISTRY,
                    providerId.value(),
                    "Capacity type and unit declarations must both be empty or both be present"
            );
        }
    }

    public boolean authorizesOwner(String authoritativeSubsystemId) {
        return authoritativeSubsystemIds.contains(
                AllocationValidation.id(
                        authoritativeSubsystemId,
                        "authoritativeSubsystemId"
                )
        );
    }

    public boolean supports(ResourceCategory category) {
        return resourceCategories.contains(AllocationValidation.required(category, "category"));
    }

    public boolean supports(CapacityTypeId capacityTypeId) {
        return capacityTypeIds.contains(
                AllocationValidation.required(capacityTypeId, "capacityTypeId")
        );
    }

    public boolean supports(CapacityUnitId capacityUnitId) {
        return capacityUnitIds.contains(
                AllocationValidation.required(capacityUnitId, "capacityUnitId")
        );
    }

    public String canonicalDigest() {
        return AllocationProviderDigestSupport.descriptor(this);
    }

    @Override
    public int compareTo(AllocationProviderDescriptor other) {
        return providerId.compareTo(
                AllocationValidation.required(other, "other").providerId
        );
    }

    private static List<String> canonicalSubsystems(List<String> source) {
        List<String> input = AllocationValidation.required(
                source,
                "authoritativeSubsystemIds"
        );
        if (input.size() > AllocationSchema.MAXIMUM_PROVIDER_OWNER_SUBSYSTEMS) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                    AllocationProviderFailureScope.REGISTRY,
                    "authoritativeSubsystemIds",
                    "Provider owner declarations exceed "
                            + AllocationSchema.MAXIMUM_PROVIDER_OWNER_SUBSYSTEMS
            );
        }
        List<String> values = input.stream()
                .map(value -> AllocationValidation.id(value, "authoritativeSubsystemId"))
                .sorted()
                .toList();
        for (int index = 1; index < values.size(); index++) {
            if (values.get(index - 1).equals(values.get(index))) {
                throw AllocationProviderValidation.failure(
                        AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                        AllocationProviderFailureScope.REGISTRY,
                        "authoritativeSubsystemIds",
                        "Provider descriptor contains a duplicate authoritative subsystem"
                );
            }
        }
        return values;
    }
}
