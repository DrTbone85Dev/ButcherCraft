package com.butchercraft.world.allocation;

import java.util.Map;
import java.util.TreeMap;

public final class AllocationProviderRegistryBuilder {
    private final Map<AllocationProviderId, AllocationResourceProvider> providers =
            new TreeMap<>();

    public AllocationProviderRegistryBuilder register(
            AllocationResourceProvider provider
    ) {
        AllocationResourceProvider value = AllocationValidation.required(
                provider,
                "provider"
        );
        AllocationProviderDescriptor descriptor;
        try {
            descriptor = AllocationValidation.required(
                    value.descriptor(),
                    "descriptor"
            );
        } catch (AllocationProviderValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.INVALID_PROVIDER_DESCRIPTOR,
                    AllocationProviderFailureScope.REGISTRY,
                    "provider",
                    "Provider descriptor could not be captured deterministically"
            );
        }
        if (providers.containsKey(descriptor.providerId())) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.DUPLICATE_PROVIDER,
                    AllocationProviderFailureScope.REGISTRY,
                    descriptor.providerId().value(),
                    "Duplicate Allocation provider identity"
            );
        }
        if (providers.size() >= AllocationSchema.MAXIMUM_PROVIDERS) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.REGISTRY_LIMIT_EXCEEDED,
                    AllocationProviderFailureScope.REGISTRY,
                    descriptor.providerId().value(),
                    "Provider registry exceeds " + AllocationSchema.MAXIMUM_PROVIDERS
            );
        }
        providers.put(descriptor.providerId(), value);
        return this;
    }

    public AllocationProviderRegistryBuilder registerAll(
            AllocationProviderRegistry registry
    ) {
        AllocationValidation.required(registry, "registry")
                .providers()
                .forEach(this::register);
        return this;
    }

    public AllocationProviderRegistry build() {
        return new AllocationProviderRegistry(providers.values());
    }
}
