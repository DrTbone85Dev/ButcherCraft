package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationProviderIdentityRegistryTest {
    @Test
    void providerIdentityIsCanonicalOrderedAndLocaleIndependent() {
        Locale original = Locale.getDefault();
        AllocationProviderId first;
        AllocationProviderId second;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            first = AllocationProviderId.of("example:provider");
            Locale.setDefault(Locale.JAPAN);
            second = AllocationProviderId.of("example:provider");
        } finally {
            Locale.setDefault(original);
        }

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals("AllocationProviderId[value=example:provider]", first.toString());
        assertTrue(first.compareTo(AllocationProviderId.of("example:z")) < 0);
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_NAMESPACE,
                () -> AllocationProviderId.of("missing_namespace")
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.MALFORMED_IDENTIFIER,
                () -> AllocationProviderId.of("Example:UPPER")
        );
    }

    @Test
    void descriptorCanonicalizesCapabilitiesAndDefendsCallerCollections() {
        List<String> owners = new ArrayList<>(List.of("test:z", "test:a"));
        List<ResourceCategory> categories = new ArrayList<>(List.of(
                ResourceCategories.PRODUCTION,
                ResourceCategories.WORKFORCE
        ));
        AllocationProviderDescriptor descriptor = new AllocationProviderDescriptor(
                AllocationProviderId.of("test:provider"),
                owners,
                categories,
                List.of(
                        AllocationProviderFixtures.WORKFORCE_SLOT,
                        AllocationProviderFixtures.MACHINE_TIME
                ),
                List.of(CapacityUnits.WORKER_SLOT, CapacityUnits.MACHINE_TIME),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        owners.clear();
        categories.clear();

        assertEquals(List.of("test:a", "test:z"), descriptor.authoritativeSubsystemIds());
        assertEquals(
                List.of(ResourceCategories.PRODUCTION, ResourceCategories.WORKFORCE)
                        .stream().sorted().toList(),
                descriptor.resourceCategories()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> descriptor.capacityTypeIds().add(
                        CapacityTypeId.of("test:another")
                )
        );
        assertEquals(64, descriptor.canonicalDigest().length());
    }

    @Test
    void registryIsCanonicalImmutableAndIndependentOfInsertionOrder() {
        var providerB = AllocationProviderFixtures.provider(
                "b",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var providerA = AllocationProviderFixtures.provider(
                "a",
                ResourceCategories.WORKFORCE,
                AllocationProviderFixtures.WORKFORCE_SLOT,
                CapacityUnits.WORKER_SLOT
        );
        AllocationProviderRegistry first = AllocationProviderRegistry.builder()
                .register(providerB)
                .register(providerA)
                .build();
        AllocationProviderRegistry second = AllocationProviderRegistry.builder()
                .register(providerA)
                .register(providerB)
                .build();

        assertEquals(
                List.of("test:provider_a", "test:provider_b"),
                first.descriptors().stream()
                        .map(descriptor -> descriptor.providerId().value())
                        .toList()
        );
        assertEquals(first.descriptors(), second.descriptors());
        assertEquals(first.canonicalDigest(), second.canonicalDigest());
        assertTrue(first.contains(providerA.id()));
        assertTrue(first.find(providerB.id()).isPresent());
        assertEquals(2L, first.stream().count());
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.providers().clear()
        );
    }

    @Test
    void registryRejectsDuplicateIdsAndBuilderDetachesAfterBuild() {
        var provider = AllocationProviderFixtures.provider(
                "duplicate",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        AllocationProviderRegistryBuilder builder = AllocationProviderRegistry.builder()
                .register(provider);
        AllocationProviderRegistry registry = builder.build();

        AllocationProviderValidationException duplicate = assertThrows(
                AllocationProviderValidationException.class,
                () -> builder.register(provider)
        );
        assertEquals(
                AllocationProviderFailureCode.DUPLICATE_PROVIDER,
                duplicate.failures().getFirst().code()
        );
        assertEquals(1, registry.size());
        assertFalse(AllocationProviderRegistry.empty().contains(provider.id()));
        assertEquals(0, AllocationProviderRegistry.empty().size());
    }

    @Test
    void registryBoundIsExplicitAndDoesNotSilentlyTruncate() {
        AllocationProviderRegistryBuilder builder = AllocationProviderRegistry.builder();
        for (int index = 0; index < AllocationSchema.MAXIMUM_PROVIDERS; index++) {
            builder.register(emptyProvider(index));
        }
        assertEquals(AllocationSchema.MAXIMUM_PROVIDERS, builder.build().size());

        AllocationProviderValidationException exception = assertThrows(
                AllocationProviderValidationException.class,
                () -> builder.register(emptyProvider(AllocationSchema.MAXIMUM_PROVIDERS))
        );
        assertEquals(
                AllocationProviderFailureCode.REGISTRY_LIMIT_EXCEEDED,
                exception.failures().getFirst().code()
        );
    }

    private static AllocationProviderFixtures.TestProvider emptyProvider(int index) {
        String suffix = "bound_" + index;
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                suffix,
                "test:owner_" + suffix,
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        return AllocationProviderFixtures.provider(
                descriptor,
                context -> AllocationObservationResult.success(
                        descriptor.providerId(),
                        context.simulationTick(),
                        List.of(),
                        List.of()
                )
        );
    }
}
