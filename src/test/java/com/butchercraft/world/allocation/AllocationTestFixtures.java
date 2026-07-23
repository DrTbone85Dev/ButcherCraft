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
}
