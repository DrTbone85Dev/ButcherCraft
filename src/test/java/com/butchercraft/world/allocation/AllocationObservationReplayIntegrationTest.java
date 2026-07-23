package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationObservationReplayIntegrationTest {
    @Test
    void independentEquivalentRegistriesReplayToEqualBundlesAndDigests() {
        AllocationObservationBundle first = observe(
                AllocationProviderFixtures.provider(
                        "b",
                        ResourceCategories.PRODUCTION,
                        AllocationProviderFixtures.MACHINE_TIME,
                        CapacityUnits.MACHINE_TIME
                ),
                AllocationProviderFixtures.provider(
                        "a",
                        ResourceCategories.WORKFORCE,
                        AllocationProviderFixtures.WORKFORCE_SLOT,
                        CapacityUnits.WORKER_SLOT
                )
        );
        AllocationObservationBundle second = observe(
                AllocationProviderFixtures.provider(
                        "a",
                        ResourceCategories.WORKFORCE,
                        AllocationProviderFixtures.WORKFORCE_SLOT,
                        CapacityUnits.WORKER_SLOT
                ),
                AllocationProviderFixtures.provider(
                        "b",
                        ResourceCategories.PRODUCTION,
                        AllocationProviderFixtures.MACHINE_TIME,
                        CapacityUnits.MACHINE_TIME
                )
        );

        assertEquals(first, second);
        assertEquals(first.providerIds(), second.providerIds());
        assertEquals(first.providerResults(), second.providerResults());
        assertEquals(first.resources(), second.resources());
        assertEquals(first.capacities(), second.capacities());
        assertEquals(first.report(), second.report());
        assertEquals(first.canonicalDigest(), second.canonicalDigest());
        assertEquals(first.report().canonicalDigest(), second.report().canonicalDigest());
    }

    @Test
    void testProvidersCanExposeAggregateWorkforceAndProductionCapacity() {
        AllocationProviderDescriptor workforce = new AllocationProviderDescriptor(
                AllocationProviderId.of("test:aggregate_workforce"),
                List.of("test:workforce"),
                List.of(ResourceCategories.WORKFORCE),
                List.of(AllocationProviderFixtures.WORKFORCE_SLOT),
                List.of(CapacityUnits.WORKER_SLOT),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        var workforceProvider = AllocationProviderFixtures.provider(workforce, context -> {
            ObservedResourceSnapshot resource = new ObservedResourceSnapshot(
                    ResourceId.of("test:day_shift_positions"),
                    ResourceCategories.WORKFORCE,
                    workforce.providerId(),
                    ExternalReference.of(
                            "butchercraft:position_shift_capacity",
                            "test:day_shift_positions",
                            "test:workforce"
                    ).withRole("test:processor"),
                    ResourceAvailability.AVAILABLE,
                    ResourceExclusivityMode.SHARED,
                    context.simulationTick(),
                    AllocationMetadata.empty(),
                    AllocationSchema.CURRENT_VERSION
            );
            return AllocationObservationResult.success(
                    workforce.providerId(),
                    context.simulationTick(),
                    List.of(resource),
                    List.of(AllocationProviderFixtures.capacity(
                            "day_shift_positions",
                            resource.resourceId(),
                            AllocationProviderFixtures.WORKFORCE_SLOT,
                            CapacityUnits.WORKER_SLOT,
                            "test:workforce",
                            context.simulationTick(),
                            "4"
                    ))
            );
        });
        var productionProvider = AllocationProviderFixtures.provider(
                "production_line",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );

        AllocationObservationBundle bundle = observe(
                productionProvider,
                workforceProvider
        );

        assertTrue(bundle.usableForAllocationCycle());
        assertEquals(
                List.of(ResourceCategories.PRODUCTION, ResourceCategories.WORKFORCE)
                        .stream().sorted().toList(),
                bundle.resources().stream()
                        .map(ObservedResourceSnapshot::resourceCategory)
                        .sorted()
                        .toList()
        );
        assertEquals("4", bundle.capacities().stream()
                .filter(capacity -> capacity.capacityUnitId()
                        .equals(CapacityUnits.WORKER_SLOT))
                .findFirst()
                .orElseThrow()
                .observedAmount()
                .canonicalAmount());
    }

    @Test
    void oneProviderCanExposeMultipleDistinctCapacityEntries() {
        CapacityTypeId slots = CapacityTypeId.of("test:production_slots");
        CapacityTypeId time = AllocationProviderFixtures.MACHINE_TIME;
        AllocationProviderDescriptor descriptor = new AllocationProviderDescriptor(
                AllocationProviderId.of("test:multi_capacity"),
                List.of("test:production"),
                List.of(ResourceCategories.PRODUCTION),
                List.of(slots, time),
                List.of(CapacityUnits.MACHINE_TIME, CapacityUnits.PRODUCTION_SLOT),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        var provider = AllocationProviderFixtures.provider(descriptor, context -> {
            ObservedResourceSnapshot resource = AllocationProviderFixtures.resource(
                    descriptor,
                    "multi_capacity",
                    ResourceCategories.PRODUCTION,
                    "test:production",
                    context.simulationTick(),
                    ResourceExclusivityMode.SHARED
            );
            return AllocationObservationResult.success(
                    descriptor.providerId(),
                    context.simulationTick(),
                    List.of(resource),
                    List.of(
                            AllocationProviderFixtures.capacity(
                                    "multi_capacity_time",
                                    resource.resourceId(),
                                    time,
                                    CapacityUnits.MACHINE_TIME,
                                    "test:production",
                                    context.simulationTick(),
                                    "8"
                            ),
                            AllocationProviderFixtures.capacity(
                                    "multi_capacity_slots",
                                    resource.resourceId(),
                                    slots,
                                    CapacityUnits.PRODUCTION_SLOT,
                                    "test:production",
                                    context.simulationTick(),
                                    "2"
                            )
                    )
            );
        });

        AllocationObservationBundle bundle = observe(provider);

        assertEquals(AllocationObservationBundleStatus.COMPLETE, bundle.status());
        assertEquals(2, bundle.capacities().size());
        assertEquals(
                List.of("2", "8"),
                bundle.capacities().stream()
                        .map(capacity -> capacity.observedAmount().canonicalAmount())
                        .sorted()
                        .toList()
        );
    }

    @Test
    void usableBundleCanConstructCycleInputWithoutInvokingAllocation() {
        CapacityTypeId type = CapacityTypeId.of("test:provider_cycle_capacity");
        AllocationCycleFixtures.Observation observation =
                AllocationCycleFixtures.observation(
                        "provider_cycle",
                        ResourceExclusivityMode.SHARED,
                        "8",
                        type,
                        CapacityUnits.MACHINE_TIME
                );
        AllocationProviderDescriptor descriptor = new AllocationProviderDescriptor(
                AllocationProviderId.of("test:provider"),
                List.of("test:provider"),
                List.of(ResourceCategories.PRODUCTION),
                List.of(type),
                List.of(CapacityUnits.MACHINE_TIME),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        var provider = AllocationProviderFixtures.provider(
                descriptor,
                context -> AllocationObservationResult.success(
                        descriptor.providerId(),
                        context.simulationTick(),
                        List.of(observation.resource()),
                        List.of(observation.capacity())
                )
        );
        AllocationObservationRequest request = AllocationObservationRequest.all(
                AllocationObservationContext.allAtTick(AllocationCycleFixtures.TICK)
        );
        AllocationObservationBundle bundle = new AllocationObservationService(
                AllocationProviderRegistry.builder().register(provider).build()
        ).observe(request).bundle().orElseThrow();

        AllocationCycleFixtures.SetGraph graph = AllocationCycleFixtures.set(
                "provider_cycle",
                10,
                AllocationCycleFixtures.demand(
                        "provider_cycle_capacity",
                        "1",
                        CapacityUnits.MACHINE_TIME,
                        Optional.empty()
                )
        );
        AllocationCycleFixtures.Scenario scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(graph),
                List.of(observation)
        );
        AllocationCycleInput input = AllocationCycleInput.fromRegistries(
                AllocationCycleContext.firstFit(AllocationCycleFixtures.TICK),
                bundle.resources(),
                bundle.capacities(),
                scenario.service().definitions(),
                scenario.service().runtimes(),
                List.of(graph.set().id())
        );

        assertTrue(bundle.usableForAllocationCycle());
        assertEquals(bundle.resources(), input.resources());
        assertEquals(bundle.capacities(), input.capacities());
        assertEquals(0, scenario.service().definitions().commitmentCount());
        assertEquals(
                AllocationRuntimeStatus.REQUESTED,
                scenario.service().runtimes().find(graph.set().id())
                        .orElseThrow()
                        .status()
        );
    }

    @Test
    void warningEvidenceDoesNotMakeOtherwiseCompleteBundleUnusable() {
        AllocationProviderDescriptor descriptor = AllocationProviderFixtures.descriptor(
                "warning",
                "test:owner_warning",
                ResourceCategories.PRODUCTION,
                AllocationProviderFixtures.MACHINE_TIME,
                CapacityUnits.MACHINE_TIME
        );
        var provider = AllocationProviderFixtures.provider(descriptor, context ->
                AllocationObservationResult.success(
                        descriptor.providerId(),
                        context.simulationTick(),
                        List.of(),
                        List.of(),
                        List.of(new AllocationProviderWarning(
                                "test:bounded_scope",
                                descriptor.providerId(),
                                "test:scope",
                                "Provider completed its declared bounded scope",
                                context.simulationTick(),
                                AllocationSchema.CURRENT_VERSION
                        ))
                ));

        AllocationObservationBundle bundle = observe(provider);

        assertEquals(AllocationObservationBundleStatus.COMPLETE, bundle.status());
        assertTrue(bundle.usableForAllocationCycle());
        assertEquals(1, bundle.warnings().size());
        assertEquals(bundle.warnings(), bundle.report().warnings());
    }

    @Test
    void bundleContainsNoAllocationDemandOrRuntimeAuthority() {
        Map<String, Class<?>> components = Map.of(
                "resources", AllocationObservationBundle.class
                        .getRecordComponents()[3].getType(),
                "capacities", AllocationObservationBundle.class
                        .getRecordComponents()[4].getType()
        );
        assertEquals(List.class, components.get("resources"));
        assertEquals(List.class, components.get("capacities"));
        assertFalse(List.of(AllocationObservationBundle.class.getRecordComponents())
                .stream()
                .map(component -> component.getType().getSimpleName())
                .anyMatch(name -> name.contains("RequestDefinition")
                        || name.contains("AllocationSet")
                        || name.contains("Commitment")
                        || name.contains("RuntimeService")));
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
