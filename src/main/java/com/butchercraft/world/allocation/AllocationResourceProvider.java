package com.butchercraft.world.allocation;

public interface AllocationResourceProvider {
    AllocationProviderDescriptor descriptor();

    AllocationObservationResult observe(AllocationObservationContext context);

    default AllocationProviderId id() {
        return descriptor().providerId();
    }
}
