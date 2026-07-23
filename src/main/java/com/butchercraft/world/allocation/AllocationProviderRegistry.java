package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public final class AllocationProviderRegistry {
    private static final AllocationProviderRegistry EMPTY =
            new AllocationProviderRegistry(List.of());

    private final List<AllocationResourceProvider> providers;
    private final List<AllocationProviderDescriptor> descriptors;
    private final Map<AllocationProviderId, AllocationResourceProvider> providerById;
    private final Map<AllocationProviderId, AllocationProviderDescriptor> descriptorById;
    private final String canonicalDigest;

    AllocationProviderRegistry(Collection<AllocationResourceProvider> providers) {
        Collection<AllocationResourceProvider> source = AllocationValidation.required(
                providers,
                "providers"
        );
        if (source.size() > AllocationSchema.MAXIMUM_PROVIDERS) {
            throw AllocationProviderValidation.failure(
                    AllocationProviderFailureCode.REGISTRY_LIMIT_EXCEEDED,
                    AllocationProviderFailureScope.REGISTRY,
                    "providers",
                    "Provider registry exceeds " + AllocationSchema.MAXIMUM_PROVIDERS
            );
        }

        List<Registration> registrations = new ArrayList<>(source.size());
        for (AllocationResourceProvider provider : source) {
            AllocationResourceProvider value = AllocationValidation.required(
                    provider,
                    "provider"
            );
            AllocationProviderDescriptor descriptor = captureDescriptor(value);
            registrations.add(new Registration(descriptor.providerId(), value, descriptor));
        }
        registrations.sort(Registration::compareTo);

        Map<AllocationProviderId, AllocationResourceProvider> providerIndex =
                new LinkedHashMap<>();
        Map<AllocationProviderId, AllocationProviderDescriptor> descriptorIndex =
                new LinkedHashMap<>();
        List<AllocationResourceProvider> orderedProviders =
                new ArrayList<>(registrations.size());
        List<AllocationProviderDescriptor> orderedDescriptors =
                new ArrayList<>(registrations.size());
        for (Registration registration : registrations) {
            if (providerIndex.putIfAbsent(
                    registration.providerId(),
                    registration.provider()
            ) != null) {
                throw AllocationProviderValidation.failure(
                        AllocationProviderFailureCode.DUPLICATE_PROVIDER,
                        AllocationProviderFailureScope.REGISTRY,
                        registration.providerId().value(),
                        "Duplicate Allocation provider identity"
                );
            }
            descriptorIndex.put(registration.providerId(), registration.descriptor());
            orderedProviders.add(registration.provider());
            orderedDescriptors.add(registration.descriptor());
        }
        this.providers = List.copyOf(orderedProviders);
        this.descriptors = List.copyOf(orderedDescriptors);
        providerById = Collections.unmodifiableMap(providerIndex);
        descriptorById = Collections.unmodifiableMap(descriptorIndex);
        canonicalDigest = AllocationProviderDigestSupport.registry(this.descriptors);
    }

    public static AllocationProviderRegistry empty() {
        return EMPTY;
    }

    public static AllocationProviderRegistryBuilder builder() {
        return new AllocationProviderRegistryBuilder();
    }

    public AllocationProviderRegistryBuilder toBuilder() {
        return new AllocationProviderRegistryBuilder().registerAll(this);
    }

    public int size() {
        return providers.size();
    }

    public boolean contains(AllocationProviderId providerId) {
        return providerById.containsKey(
                AllocationValidation.required(providerId, "providerId")
        );
    }

    public Optional<AllocationResourceProvider> find(AllocationProviderId providerId) {
        return Optional.ofNullable(providerById.get(
                AllocationValidation.required(providerId, "providerId")
        ));
    }

    public Optional<AllocationProviderDescriptor> findDescriptor(
            AllocationProviderId providerId
    ) {
        return Optional.ofNullable(descriptorById.get(
                AllocationValidation.required(providerId, "providerId")
        ));
    }

    public List<AllocationResourceProvider> providers() {
        return providers;
    }

    public List<AllocationProviderDescriptor> descriptors() {
        return descriptors;
    }

    public Stream<AllocationResourceProvider> stream() {
        return providers.stream();
    }

    public String canonicalDigest() {
        return canonicalDigest;
    }

    private static AllocationProviderDescriptor captureDescriptor(
            AllocationResourceProvider provider
    ) {
        try {
            AllocationProviderDescriptor descriptor = provider.descriptor();
            if (descriptor == null) {
                throw new IllegalStateException("Provider descriptor is null");
            }
            return descriptor;
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
    }

    private record Registration(
            AllocationProviderId providerId,
            AllocationResourceProvider provider,
            AllocationProviderDescriptor descriptor
    ) implements Comparable<Registration> {
        @Override
        public int compareTo(Registration other) {
            return providerId.compareTo(other.providerId);
        }
    }
}
