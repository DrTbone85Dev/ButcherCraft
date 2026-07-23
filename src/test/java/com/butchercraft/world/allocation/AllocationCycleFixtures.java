package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class AllocationCycleFixtures {
    static final long TICK = 200L;
    static final ExternalReference PLANNING_CYCLE = ExternalReference.of(
            "butchercraft:planning_cycle",
            "test:allocation_cycle_planning",
            "butchercraft:planning"
    );

    private AllocationCycleFixtures() {
    }

    static SetGraph set(String suffix, int priority, Demand... demands) {
        long requestTick = 100L + Math.floorMod(suffix.hashCode(), 50);
        AllocationOrderingContext ordering = new AllocationOrderingContext(
                1,
                priority,
                OptionalLong.of(500L),
                50L,
                PLANNING_CYCLE,
                ExternalReference.of(
                        "butchercraft:approved_plan",
                        "test:approved_" + suffix,
                        "butchercraft:planning"
                ),
                requestTick,
                Math.floorMod(suffix.hashCode(), 10_000)
        );
        ExternalReference work = ExternalReference.of(
                "butchercraft:execution_work",
                "test:work_" + suffix,
                "butchercraft:production"
        );
        AllocationRequestId requestId = AllocationIds.requestId(
                work,
                ordering,
                AllocationSchema.CURRENT_VERSION
        );
        AllocationSetId setId = AllocationIds.setId(
                ordering.sourceApprovedPlanReference(),
                work,
                requestId,
                AllocationSchema.CURRENT_VERSION
        );
        List<RequirementDefinition> requirements = new ArrayList<>();
        for (Demand demand : demands) {
            requirements.add(RequirementDefinition.create(
                    setId,
                    work,
                    demand.category(),
                    demand.capacityTypeId(),
                    demand.exactResourceId(),
                    demand.quantity(),
                    requestTick,
                    AllocationMetadata.empty()
            ));
        }
        AllocationRequestDefinition request = AllocationRequestDefinition.create(
                setId,
                work,
                requirements,
                ordering,
                AllocationMetadata.empty()
        );
        AllocationSetDefinition set = AllocationSetDefinition.create(
                setId,
                work,
                request,
                requirements,
                PLANNING_CYCLE,
                requestTick,
                OptionalLong.of(1_000L),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        return new SetGraph(requirements, request, set);
    }

    static Demand demand(
            String type,
            String amount,
            CapacityUnitId unit,
            Optional<ResourceId> exactResourceId
    ) {
        return new Demand(
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("test:" + type),
                exactResourceId,
                AllocationQuantity.of(amount, unit)
        );
    }

    static Observation observation(
            String suffix,
            ResourceExclusivityMode mode,
            String amount,
            CapacityTypeId type,
            CapacityUnitId unit
    ) {
        return observation(
                suffix,
                mode,
                amount,
                type,
                unit,
                ResourceAvailability.AVAILABLE,
                TICK
        );
    }

    static Observation observation(
            String suffix,
            ResourceExclusivityMode mode,
            String amount,
            CapacityTypeId type,
            CapacityUnitId unit,
            ResourceAvailability availability,
            long tick
    ) {
        ResourceId resourceId = ResourceId.of("test:resource_" + suffix);
        ObservedResourceSnapshot resource = new ObservedResourceSnapshot(
                resourceId,
                ResourceCategories.PRODUCTION,
                AllocationProviderId.of("test:provider"),
                ExternalReference.of(
                        "butchercraft:resource_observation",
                        "test:resource_observation_" + suffix,
                        "test:provider"
                ),
                availability,
                mode,
                tick,
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        ObservedCapacitySnapshot capacity = new ObservedCapacitySnapshot(
                CapacityId.of("test:capacity_" + suffix),
                resourceId,
                type,
                AllocationQuantity.of(amount, unit),
                unit,
                tick,
                ExternalReference.of(
                        "butchercraft:capacity_observation",
                        "test:capacity_observation_" + suffix,
                        "test:provider"
                ),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        return new Observation(resource, capacity);
    }

    static Scenario scenario(
            long tick,
            List<SetGraph> graphs,
            List<Observation> observations
    ) {
        AllocationRegistryBuilder definitions = AllocationRegistry.builder();
        graphs.forEach(graph -> {
            graph.requirements().forEach(definitions::registerRequirement);
            definitions.registerRequest(graph.request());
            definitions.registerSet(graph.set());
        });
        AllocationRuntimeService service = new AllocationRuntimeService(
                definitions.build()
        );
        graphs.forEach(graph -> {
            AllocationRuntimeOperationResult<AllocationRuntimeView> registered =
                    service.registerRequested(
                            graph.set().id(),
                            AllocationMetadata.empty()
                    );
            if (!registered.accepted()) {
                throw new IllegalStateException(registered.failures().toString());
            }
        });
        return scenario(tick, graphs, observations, service);
    }

    static Scenario scenario(
            long tick,
            List<SetGraph> graphs,
            List<Observation> observations,
            AllocationRuntimeService service
    ) {
        List<ObservedResourceSnapshot> resources = observations.stream()
                .map(Observation::resource)
                .toList();
        List<ObservedCapacitySnapshot> capacities = observations.stream()
                .map(Observation::capacity)
                .toList();
        AllocationCycleInput input = AllocationCycleInput.fromRegistries(
                AllocationCycleContext.firstFit(tick),
                resources,
                capacities,
                service.definitions(),
                service.runtimes(),
                graphs.stream().map(graph -> graph.set().id()).toList()
        );
        return new Scenario(input, service, graphs, observations);
    }

    record Demand(
            ResourceCategory category,
            CapacityTypeId capacityTypeId,
            Optional<ResourceId> exactResourceId,
            AllocationQuantity quantity
    ) {
    }

    record SetGraph(
            List<RequirementDefinition> requirements,
            AllocationRequestDefinition request,
            AllocationSetDefinition set
    ) {
    }

    record Observation(
            ObservedResourceSnapshot resource,
            ObservedCapacitySnapshot capacity
    ) {
    }

    record Scenario(
            AllocationCycleInput input,
            AllocationRuntimeService service,
            List<SetGraph> graphs,
            List<Observation> observations
    ) {
    }
}
