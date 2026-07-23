package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationObservationConflictTest {
    @Test
    void duplicateResourceAcrossProvidersMakesBundleUnusable() {
        var first = conflictingProvider("a", "shared", "capacity_a");
        var second = conflictingProvider("b", "shared", "capacity_b");
        AllocationObservationBundle bundle = observe(first, second);

        assertEquals(AllocationObservationBundleStatus.UNUSABLE, bundle.status());
        assertFalse(bundle.usableForAllocationCycle());
        assertTrue(bundle.failures().stream().anyMatch(failure ->
                failure.scope() == AllocationProviderFailureScope.BUNDLE
                        && failure.code()
                        == AllocationProviderFailureCode.CONFLICTING_OBSERVATION
                        && failure.subject().startsWith("test:resource_shared|")));
        assertEquals(2, bundle.providerResults().size());
        assertTrue(bundle.providerResults().stream()
                .allMatch(AllocationObservationResult::successful));
    }

    @Test
    void duplicateCapacityIdAcrossDisjointResourcesMakesBundleUnusable() {
        var first = capacityConflictProvider("a", "resource_a", "shared_capacity");
        var second = capacityConflictProvider("b", "resource_b", "shared_capacity");
        AllocationObservationBundle bundle = observe(first, second);

        assertEquals(AllocationObservationBundleStatus.UNUSABLE, bundle.status());
        assertTrue(bundle.failures().stream().anyMatch(failure ->
                failure.scope() == AllocationProviderFailureScope.BUNDLE
                        && failure.code()
                        == AllocationProviderFailureCode.CONFLICTING_OBSERVATION
                        && failure.subject().startsWith(
                                "test:capacity_shared_capacity|"
                        )));
    }

    @Test
    void duplicateCapacityKeyWithDifferentIdsIsNotImplicitlyMerged() {
        var first = conflictingProvider("a", "shared", "capacity_a");
        var second = conflictingProvider("b", "shared", "capacity_b");
        AllocationObservationBundle bundle = observe(first, second);

        assertTrue(bundle.failures().stream().anyMatch(failure ->
                failure.scope() == AllocationProviderFailureScope.BUNDLE
                        && failure.subject().startsWith("test:capacity_")));
        assertEquals(2, bundle.capacities().size());
    }

    @Test
    void duplicateWithinOneProviderInvalidatesOnlyThatProviderResult() {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "local_duplicate",
                "test:owner_local_duplicate",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var duplicate = AllocationProviderFixtures.provider(descriptor, context -> {
            ObservedResourceSnapshot resource = AllocationProviderFixtures.resource(
                    descriptor,
                    "local_duplicate",
                    ResourceCategories.PRODUCTION,
                    "test:owner_local_duplicate",
                    context.simulationTick(),
                    ResourceExclusivityMode.SHARED
            );
            return AllocationObservationResult.success(
                    descriptor.providerId(),
                    context.simulationTick(),
                    List.of(resource, resource),
                    List.of()
            );
        });
        var valid = AllocationProviderFixtures.provider(
                "valid",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );

        AllocationObservationBundle bundle = observe(duplicate, valid);

        assertEquals(AllocationObservationBundleStatus.INCOMPLETE, bundle.status());
        assertEquals(1, bundle.resources().size());
        assertTrue(bundle.failures().stream().anyMatch(failure ->
                failure.code() == AllocationProviderFailureCode.DUPLICATE_RESOURCE));
    }

    @Test
    void unknownCapacityResourceInvalidatesProviderWithoutLeakingCapacity() {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "unknown_resource",
                "test:owner_unknown_resource",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var provider = AllocationProviderFixtures.provider(descriptor, context ->
                AllocationObservationResult.success(
                        descriptor.providerId(),
                        context.simulationTick(),
                        List.of(),
                        List.of(AllocationProviderFixtures.capacity(
                                "orphan",
                                ResourceId.of("test:missing"),
                                AllocationProviderFixtures.MACHINE_TIME,
                                CapacityUnits.MACHINE_TIME,
                                "test:owner_unknown_resource",
                                context.simulationTick(),
                                "1"
                        ))
                ));

        AllocationObservationBundle bundle = observe(provider);

        assertEquals(AllocationObservationBundleStatus.INCOMPLETE, bundle.status());
        assertEquals(List.of(), bundle.capacities());
        assertEquals(AllocationProviderFailureCode.UNKNOWN_REFERENCE,
                bundle.failures().getFirst().code());
    }

    private static AllocationProviderFixtures.TestProvider conflictingProvider(
            String providerSuffix,
            String resourceSuffix,
            String capacitySuffix
    ) {
        String owner = "test:owner_" + providerSuffix;
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                providerSuffix,
                owner,
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        return AllocationProviderFixtures.provider(descriptor, context -> {
            ObservedResourceSnapshot resource = AllocationProviderFixtures.resource(
                    descriptor,
                    resourceSuffix,
                    ResourceCategories.PRODUCTION,
                    owner,
                    context.simulationTick(),
                    ResourceExclusivityMode.SHARED
            );
            ObservedCapacitySnapshot capacity = AllocationProviderFixtures.capacity(
                    capacitySuffix,
                    resource.resourceId(),
                    AllocationProviderFixtures.MACHINE_TIME,
                    CapacityUnits.MACHINE_TIME,
                    owner,
                    context.simulationTick(),
                    "1"
            );
            return AllocationObservationResult.success(
                    descriptor.providerId(),
                    context.simulationTick(),
                    List.of(resource),
                    List.of(capacity)
            );
        });
    }

    private static AllocationProviderFixtures.TestProvider capacityConflictProvider(
            String providerSuffix,
            String resourceSuffix,
            String capacitySuffix
    ) {
        return conflictingProvider(providerSuffix, resourceSuffix, capacitySuffix);
    }

    private static AllocationObservationBundle observe(
            AllocationResourceProvider... providers
    ) {
        AllocationProviderRegistryBuilder builder = AllocationProviderRegistry.builder();
        for (AllocationResourceProvider provider : providers) {
            builder.register(provider);
        }
        return new AllocationObservationService(builder.build())
                .observe(AllocationProviderFixtures.request())
                .bundle()
                .orElseThrow();
    }
}
