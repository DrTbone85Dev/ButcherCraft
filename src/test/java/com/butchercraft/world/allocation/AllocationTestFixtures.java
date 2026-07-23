package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

final class AllocationTestFixtures {
    static final long NEED_TICK = 100L;
    static final long REQUEST_TICK = 120L;
    static final long CURRENT_TICK = 200L;

    private AllocationTestFixtures() {
    }

    static ExternalReference work(String suffix) {
        return ExternalReference.of(
                "butchercraft:execution_work",
                "butchercraft:work_" + suffix,
                "butchercraft:production"
        );
    }

    static ExternalReference planningCycle(String suffix) {
        return ExternalReference.of(
                "butchercraft:planning_cycle",
                "butchercraft:planning_cycle_" + suffix,
                "butchercraft:planning"
        );
    }

    static ExternalReference approvedPlan(String suffix) {
        return ExternalReference.of(
                "butchercraft:approved_plan",
                "butchercraft:approved_plan_" + suffix,
                "butchercraft:planning"
        );
    }

    static ExternalReference observation(String suffix) {
        return ExternalReference.of(
                "butchercraft:capacity_observation",
                "butchercraft:observation_" + suffix,
                "butchercraft:allocation_provider"
        );
    }

    static AllocationOrderingContext ordering(String suffix) {
        return ordering(suffix, 1, 2, OptionalLong.of(400L), NEED_TICK, REQUEST_TICK, 1L);
    }

    static AllocationOrderingContext ordering(
            String suffix,
            int horizon,
            int priority,
            OptionalLong requiredBy,
            long needTick,
            long requestTick,
            long sequence
    ) {
        return new AllocationOrderingContext(
                horizon,
                priority,
                requiredBy,
                needTick,
                planningCycle(suffix),
                approvedPlan(suffix),
                requestTick,
                sequence
        );
    }

    static AllocationSetId setId(String suffix, AllocationOrderingContext ordering) {
        ExternalReference work = work(suffix);
        AllocationRequestId requestId = AllocationIds.requestId(
                work,
                ordering,
                AllocationSchema.CURRENT_VERSION
        );
        return AllocationIds.setId(
                ordering.sourceApprovedPlanReference(),
                work,
                requestId,
                AllocationSchema.CURRENT_VERSION
        );
    }

    static RequirementDefinition requirement(String suffix) {
        AllocationOrderingContext ordering = ordering(suffix);
        return requirement(
                suffix,
                ordering,
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("butchercraft:machine_time"),
                Optional.empty(),
                AllocationQuantity.of("2.5", CapacityUnits.MACHINE_TIME)
        );
    }

    static RequirementDefinition requirement(
            String suffix,
            AllocationOrderingContext ordering,
            ResourceCategory category,
            CapacityTypeId capacityTypeId,
            Optional<ResourceId> exactResourceId,
            AllocationQuantity quantity
    ) {
        return RequirementDefinition.create(
                setId(suffix, ordering),
                work(suffix),
                category,
                capacityTypeId,
                exactResourceId,
                quantity,
                ordering.requestCreationSimulationTick(),
                AllocationMetadata.empty()
        );
    }

    static AllocationRequestDefinition request(String suffix) {
        AllocationOrderingContext ordering = ordering(suffix);
        RequirementDefinition requirement = requirement(suffix);
        return AllocationRequestDefinition.create(
                requirement.allocationSetId(),
                work(suffix),
                List.of(requirement),
                ordering,
                AllocationMetadata.empty()
        );
    }

    static AllocationRequestDefinition request(
            String suffix,
            AllocationOrderingContext ordering
    ) {
        RequirementDefinition requirement = requirement(
                suffix,
                ordering,
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("butchercraft:machine_time"),
                Optional.empty(),
                AllocationQuantity.of("2.5", CapacityUnits.MACHINE_TIME)
        );
        return AllocationRequestDefinition.create(
                requirement.allocationSetId(),
                work(suffix),
                List.of(requirement),
                ordering,
                AllocationMetadata.empty()
        );
    }

    static AllocationSetDefinition set(String suffix) {
        AllocationOrderingContext ordering = ordering(suffix);
        RequirementDefinition requirement = requirement(suffix);
        AllocationRequestDefinition request = AllocationRequestDefinition.create(
                requirement.allocationSetId(),
                work(suffix),
                List.of(requirement),
                ordering,
                AllocationMetadata.empty()
        );
        return AllocationSetDefinition.create(
                requirement.allocationSetId(),
                work(suffix),
                request,
                List.of(requirement),
                ordering.planningCycleReference(),
                ordering.requestCreationSimulationTick(),
                OptionalLong.of(500L),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
    }

    static AllocationGraph graph(String suffix) {
        AllocationOrderingContext ordering = ordering(suffix);
        RequirementDefinition requirement = requirement(suffix);
        AllocationRequestDefinition request = AllocationRequestDefinition.create(
                requirement.allocationSetId(),
                work(suffix),
                List.of(requirement),
                ordering,
                AllocationMetadata.empty()
        );
        AllocationSetDefinition set = AllocationSetDefinition.create(
                requirement.allocationSetId(),
                work(suffix),
                request,
                List.of(requirement),
                ordering.planningCycleReference(),
                ordering.requestCreationSimulationTick(),
                OptionalLong.of(500L),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        AllocationCommitmentDefinition commitment =
                AllocationCommitmentDefinition.create(
                        AllocationCycleId.forTick(CURRENT_TICK),
                        requirement,
                        ResourceId.of("example:resource_" + suffix),
                        CapacityId.of("example:capacity_" + suffix),
                        requirement.requiredQuantity(),
                        CURRENT_TICK,
                        OptionalLong.of(500L),
                        List.of(observation(suffix)),
                        AllocationMetadata.empty()
                );
        AllocationRegistry definitions = AllocationRegistry.builder()
                .registerRequirement(requirement)
                .registerRequest(request)
                .registerSet(set)
                .registerCommitment(commitment)
                .build();
        return new AllocationGraph(
                requirement,
                request,
                set,
                commitment,
                definitions
        );
    }

    static AllocationRegistry definitionsWithoutCommitment(String suffix) {
        AllocationGraph graph = graph(suffix);
        return AllocationRegistry.builder()
                .registerRequirement(graph.requirement())
                .registerRequest(graph.request())
                .registerSet(graph.set())
                .build();
    }

    static AllocationReport report(AllocationGraph graph, long tick) {
        CapacityKey key = new CapacityKey(
                graph.commitment().resourceId(),
                graph.requirement().capacityTypeId(),
                graph.requirement().capacityUnitId()
        );
        return new AllocationReport(
                AllocationCycleId.forTick(tick),
                List.of(graph.set().id()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(graph.commitment().id()),
                List.of(),
                List.of(new AllocationCapacityReportEntry(
                        key,
                        graph.requirement().requiredQuantity(),
                        graph.commitment().committedQuantity(),
                        AllocationQuantity.zero(key.capacityUnitId())
                )),
                List.of(new AllocationReportOrderingRecord(
                        graph.request().id(),
                        graph.request().orderingContext()
                )),
                new AllocationReportWorkSummary(
                        java.util.Map.of("butchercraft:validation", 1L),
                        1L,
                        10L,
                        false
                ),
                List.of(),
                AllocationPolicyId.of("butchercraft:first_fit"),
                tick,
                AllocationSchema.CURRENT_VERSION
        );
    }

    static AllocationValidationException failure(
            org.junit.jupiter.api.function.Executable executable
    ) {
        return org.junit.jupiter.api.Assertions.assertThrows(
                AllocationValidationException.class,
                executable
        );
    }

    static void assertFailure(
            AllocationValidationFailureCode code,
            org.junit.jupiter.api.function.Executable executable
    ) {
        AllocationValidationException exception = failure(executable);
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.failures().stream().anyMatch(failure -> failure.code() == code),
                () -> "Expected " + code + " in " + exception.failures()
        );
    }

    static void assertRuntimeFailure(
            AllocationRuntimeFailureCode code,
            org.junit.jupiter.api.function.Executable executable
    ) {
        AllocationRuntimeValidationException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        AllocationRuntimeValidationException.class,
                        executable
                );
        org.junit.jupiter.api.Assertions.assertTrue(
                exception.failures().stream().anyMatch(failure -> failure.code() == code),
                () -> "Expected " + code + " in " + exception.failures()
        );
    }

    record AllocationGraph(
            RequirementDefinition requirement,
            AllocationRequestDefinition request,
            AllocationSetDefinition set,
            AllocationCommitmentDefinition commitment,
            AllocationRegistry definitions
    ) {
    }
}
