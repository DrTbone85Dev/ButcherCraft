package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AllocationProviderStressTest {
    private static final int PROVIDER_COUNT = 20_000;
    private static final int OBSERVATION_PROVIDER_COUNT = 10;
    private static final int OBSERVATIONS_PER_PROVIDER = 10_000;
    private static final int OBSERVATION_COUNT =
            OBSERVATION_PROVIDER_COUNT * OBSERVATIONS_PER_PROVIDER;
    private static final int CONFLICT_PROVIDER_COUNT = 1_000;

    @Test
    void twentyThousandProviderRegistryReplaysWithStableOrderingAndDigest() {
        RegistryStressDigest first = registryWorkload(false);
        RegistryStressDigest second = registryWorkload(true);

        assertEquals(first, second);
        assertEquals(PROVIDER_COUNT, first.providerCount());
    }

    @Test
    void oneHundredThousandResourcesAndCapacitiesReplayAsOneCompleteBundle() {
        ObservationStressDigest first = observationWorkload(false);
        ObservationStressDigest second = observationWorkload(true);

        assertEquals(first, second);
        assertEquals(OBSERVATION_COUNT, first.resourceCount());
        assertEquals(OBSERVATION_COUNT, first.capacityCount());
        assertEquals(AllocationObservationBundleStatus.COMPLETE, first.status());
    }

    @Test
    void highCrossProviderConflictInputIsDeterministicAndNeverUsable() {
        ConflictStressDigest first = conflictWorkload(false);
        ConflictStressDigest second = conflictWorkload(true);

        assertEquals(first, second);
        assertEquals(AllocationObservationBundleStatus.UNUSABLE, first.status());
        assertFalse(first.usable());
        assertEquals((CONFLICT_PROVIDER_COUNT - 1) * 2, first.failureCount());
    }

    private static RegistryStressDigest registryWorkload(boolean reverse) {
        AllocationProviderRegistryBuilder builder = AllocationProviderRegistry.builder();
        for (int position = 0; position < PROVIDER_COUNT; position++) {
            int index = reverse ? PROVIDER_COUNT - position - 1 : position;
            String suffix = "registry_stress_" + index;
            AllocationProviderDescriptor descriptor =
                    AllocationProviderFixtures.descriptor(
                            suffix,
                            "test:owner_" + suffix,
                            ResourceCategories.PRODUCTION,
                            AllocationProviderFixtures.MACHINE_TIME,
                            CapacityUnits.MACHINE_TIME
                    );
            builder.register(AllocationProviderFixtures.provider(
                    descriptor,
                    context -> AllocationObservationResult.success(
                            descriptor.providerId(),
                            context.simulationTick(),
                            List.of(),
                            List.of()
                    )
            ));
        }
        AllocationProviderRegistry registry = builder.build();
        return new RegistryStressDigest(
                registry.size(),
                registry.descriptors().getFirst().providerId().value(),
                registry.descriptors().getLast().providerId().value(),
                registry.canonicalDigest()
        );
    }

    private static ObservationStressDigest observationWorkload(boolean reverse) {
        AllocationProviderRegistryBuilder builder = AllocationProviderRegistry.builder();
        for (int position = 0; position < OBSERVATION_PROVIDER_COUNT; position++) {
            int providerIndex = reverse
                    ? OBSERVATION_PROVIDER_COUNT - position - 1
                    : position;
            builder.register(observationProvider(providerIndex, reverse));
        }
        AllocationObservationBundle bundle = new AllocationObservationService(
                builder.build()
        ).observe(AllocationProviderFixtures.request()).bundle().orElseThrow();
        return new ObservationStressDigest(
                bundle.providerIds().size(),
                bundle.resources().size(),
                bundle.capacities().size(),
                bundle.failures().size(),
                bundle.report().summary().deterministicOperationCount(),
                bundle.status(),
                bundle.canonicalDigest()
        );
    }

    private static AllocationResourceProvider observationProvider(
            int providerIndex,
            boolean reverse
    ) {
        String suffix = "observation_stress_" + providerIndex;
        String owner = "test:owner_" + suffix;
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                suffix,
                owner,
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        List<ObservedResourceSnapshot> resources =
                new ArrayList<>(OBSERVATIONS_PER_PROVIDER);
        List<ObservedCapacitySnapshot> capacities =
                new ArrayList<>(OBSERVATIONS_PER_PROVIDER);
        for (int position = 0; position < OBSERVATIONS_PER_PROVIDER; position++) {
            int localIndex = reverse
                    ? OBSERVATIONS_PER_PROVIDER - position - 1
                    : position;
            int index = providerIndex * OBSERVATIONS_PER_PROVIDER + localIndex;
            String observationSuffix = "stress_" + index;
            ObservedResourceSnapshot resource = AllocationProviderFixtures.resource(
                    descriptor,
                    observationSuffix,
                    ResourceCategories.PRODUCTION,
                    owner,
                    AllocationProviderFixtures.TICK,
                    ResourceExclusivityMode.SHARED
            );
            resources.add(resource);
            capacities.add(AllocationProviderFixtures.capacity(
                    observationSuffix,
                    resource.resourceId(),
                    AllocationProviderFixtures.MACHINE_TIME,
                    CapacityUnits.MACHINE_TIME,
                    owner,
                    AllocationProviderFixtures.TICK,
                    "1"
            ));
        }
        return AllocationProviderFixtures.provider(
                descriptor,
                context -> AllocationObservationResult.success(
                        descriptor.providerId(),
                        context.simulationTick(),
                        resources,
                        capacities
                )
        );
    }

    private static ConflictStressDigest conflictWorkload(boolean reverse) {
        AllocationProviderRegistryBuilder builder = AllocationProviderRegistry.builder();
        for (int position = 0; position < CONFLICT_PROVIDER_COUNT; position++) {
            int index = reverse ? CONFLICT_PROVIDER_COUNT - position - 1 : position;
            String suffix = "conflict_stress_" + index;
            String owner = "test:owner_" + suffix;
            AllocationProviderDescriptor descriptor =
                    AllocationProviderFixtures.descriptor(
                            suffix,
                            owner,
                            ResourceCategories.PRODUCTION,
                            AllocationProviderFixtures.MACHINE_TIME,
                            CapacityUnits.MACHINE_TIME
                    );
            builder.register(AllocationProviderFixtures.provider(descriptor, context -> {
                ObservedResourceSnapshot resource =
                        AllocationProviderFixtures.resource(
                                descriptor,
                                "shared_conflict",
                                ResourceCategories.PRODUCTION,
                                owner,
                                context.simulationTick(),
                                ResourceExclusivityMode.SHARED
                        );
                ObservedCapacitySnapshot capacity =
                        AllocationProviderFixtures.capacity(
                                "shared_conflict",
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
            }));
        }
        AllocationObservationBundle bundle = new AllocationObservationService(
                builder.build()
        ).observe(AllocationProviderFixtures.request()).bundle().orElseThrow();
        return new ConflictStressDigest(
                bundle.providerIds().size(),
                bundle.failures().size(),
                bundle.status(),
                bundle.usableForAllocationCycle(),
                bundle.canonicalDigest()
        );
    }

    private record RegistryStressDigest(
            int providerCount,
            String firstProviderId,
            String lastProviderId,
            String registryDigest
    ) {
    }

    private record ObservationStressDigest(
            int providerCount,
            int resourceCount,
            int capacityCount,
            int failureCount,
            long operationCount,
            AllocationObservationBundleStatus status,
            String bundleDigest
    ) {
    }

    private record ConflictStressDigest(
            int providerCount,
            int failureCount,
            AllocationObservationBundleStatus status,
            boolean usable,
            String bundleDigest
    ) {
    }
}
