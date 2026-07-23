package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AllocationRequestSetTest {
    @Test
    void requestAndSetCanonicalizeRequirementOrderAndDefensivelyCopyCollections() {
        String suffix = "canonical";
        AllocationOrderingContext ordering = AllocationTestFixtures.ordering(suffix);
        RequirementDefinition machine = AllocationTestFixtures.requirement(
                suffix,
                ordering,
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("butchercraft:machine_time"),
                Optional.empty(),
                AllocationQuantity.of("2", CapacityUnits.MACHINE_TIME)
        );
        RequirementDefinition storage = AllocationTestFixtures.requirement(
                suffix,
                ordering,
                ResourceCategories.STORAGE,
                CapacityTypeId.of("butchercraft:storage_mass"),
                Optional.empty(),
                AllocationQuantity.of("100", CapacityUnits.STORAGE_MASS)
        );
        List<RequirementDefinition> mutable = new ArrayList<>(List.of(storage, machine));
        AllocationRequestDefinition request = AllocationRequestDefinition.create(
                machine.allocationSetId(),
                AllocationTestFixtures.work(suffix),
                mutable,
                ordering,
                AllocationMetadata.empty()
        );
        AllocationSetDefinition set = AllocationSetDefinition.create(
                machine.allocationSetId(),
                AllocationTestFixtures.work(suffix),
                request,
                mutable,
                ordering.planningCycleReference(),
                ordering.requestCreationSimulationTick(),
                OptionalLong.empty(),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        mutable.clear();

        List<RequirementId> expected = List.of(machine.id(), storage.id()).stream().sorted().toList();
        assertEquals(expected, request.requirementIds());
        assertEquals(expected, set.requirementIds());
        assertEquals(request.id(), set.sourceRequestId());
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.requirementIds().add(machine.id())
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> set.requirementIds().add(machine.id())
        );
    }

    @Test
    void requestRejectsEmptyDuplicateAndForeignRequirements() {
        AllocationRequestDefinition valid = AllocationTestFixtures.request("request_validation");
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NULL_VALUE,
                () -> new AllocationRequestDefinition(
                        valid.id(),
                        valid.allocationSetId(),
                        valid.executionWorkReference(),
                        valid.requirementIds(),
                        null,
                        valid.creationSimulationTick(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.EMPTY_REQUIREMENTS,
                () -> new AllocationRequestDefinition(
                        valid.id(),
                        valid.allocationSetId(),
                        valid.executionWorkReference(),
                        List.of(),
                        valid.orderingContext(),
                        valid.creationSimulationTick(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.DUPLICATE_REQUIREMENT,
                () -> new AllocationRequestDefinition(
                        valid.id(),
                        valid.allocationSetId(),
                        valid.executionWorkReference(),
                        List.of(valid.requirementIds().getFirst(), valid.requirementIds().getFirst()),
                        valid.orderingContext(),
                        valid.creationSimulationTick(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
        RequirementDefinition foreign = AllocationTestFixtures.requirement("foreign");
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INCONSISTENT_SET_ASSOCIATION,
                () -> AllocationRequestDefinition.create(
                        valid.allocationSetId(),
                        valid.executionWorkReference(),
                        List.of(foreign),
                        valid.orderingContext(),
                        AllocationMetadata.empty()
                )
        );
        List<RequirementId> withNull = new ArrayList<>(valid.requirementIds());
        withNull.add(null);
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NULL_VALUE,
                () -> new AllocationRequestDefinition(
                        valid.id(),
                        valid.allocationSetId(),
                        valid.executionWorkReference(),
                        withNull,
                        valid.orderingContext(),
                        valid.creationSimulationTick(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    @Test
    void setRejectsEmptyDuplicateResourceAndDuplicateCapacitySelectors() {
        String suffix = "set_validation";
        AllocationOrderingContext ordering = AllocationTestFixtures.ordering(suffix);
        ResourceId exact = ResourceId.of("example:line_one");
        RequirementDefinition first = AllocationTestFixtures.requirement(
                suffix,
                ordering,
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("example:machine_time"),
                Optional.of(exact),
                AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME)
        );
        RequirementDefinition sameResource = AllocationTestFixtures.requirement(
                suffix,
                ordering,
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("example:production_slot"),
                Optional.of(exact),
                AllocationQuantity.of("1", CapacityUnits.PRODUCTION_SLOT)
        );
        RequirementDefinition sameSelector = AllocationTestFixtures.requirement(
                suffix,
                ordering,
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("example:machine_time"),
                Optional.of(exact),
                AllocationQuantity.of("2", CapacityUnits.MACHINE_TIME)
        );
        AllocationRequestDefinition firstRequest = AllocationRequestDefinition.create(
                first.allocationSetId(),
                AllocationTestFixtures.work(suffix),
                List.of(first),
                ordering,
                AllocationMetadata.empty()
        );

        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.EMPTY_ALLOCATION_SET,
                () -> AllocationSetDefinition.create(
                        first.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        firstRequest,
                        List.of(),
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.empty(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );

        AllocationRequestDefinition resourceRequest = AllocationRequestDefinition.create(
                first.allocationSetId(),
                AllocationTestFixtures.work(suffix),
                List.of(first, sameResource),
                ordering,
                AllocationMetadata.empty()
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.DUPLICATE_RESOURCE,
                () -> AllocationSetDefinition.create(
                        first.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        resourceRequest,
                        List.of(first, sameResource),
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.empty(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );

        AllocationRequestDefinition selectorRequest = AllocationRequestDefinition.create(
                first.allocationSetId(),
                AllocationTestFixtures.work(suffix),
                List.of(first, sameSelector),
                ordering,
                AllocationMetadata.empty()
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.DUPLICATE_CAPACITY_KEY,
                () -> AllocationSetDefinition.create(
                        first.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        selectorRequest,
                        List.of(first, sameSelector),
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.empty(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    @Test
    void setRejectsInvalidExpirationAndNonCanonicalIdentity() {
        String suffix = "set_identity";
        AllocationOrderingContext ordering = AllocationTestFixtures.ordering(suffix);
        RequirementDefinition requirement = AllocationTestFixtures.requirement(suffix);
        AllocationRequestDefinition request = AllocationRequestDefinition.create(
                requirement.allocationSetId(),
                AllocationTestFixtures.work(suffix),
                List.of(requirement),
                ordering,
                AllocationMetadata.empty()
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_EXPIRATION,
                () -> AllocationSetDefinition.create(
                        requirement.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        request,
                        List.of(requirement),
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.of(ordering.requestCreationSimulationTick() - 1L),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NONCANONICAL_INPUT,
                () -> AllocationSetDefinition.create(
                        AllocationSetId.of("example:wrong"),
                        AllocationTestFixtures.work(suffix),
                        request,
                        List.of(requirement),
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.empty(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );
    }

    @Test
    void setRejectsMixedWorkAndNullRequirementsAndContainsNoRuntimeState() {
        String suffix = "set_boundary";
        AllocationOrderingContext ordering = AllocationTestFixtures.ordering(suffix);
        RequirementDefinition requirement = AllocationTestFixtures.requirement(suffix);
        AllocationRequestDefinition request = AllocationRequestDefinition.create(
                requirement.allocationSetId(),
                AllocationTestFixtures.work(suffix),
                List.of(requirement),
                ordering,
                AllocationMetadata.empty()
        );
        RequirementDefinition foreign = AllocationTestFixtures.requirement("other_work");
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INCONSISTENT_EXECUTION_REFERENCE,
                () -> AllocationSetDefinition.create(
                        requirement.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        request,
                        List.of(requirement, foreign),
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.empty(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );

        List<RequirementDefinition> withNull = new ArrayList<>();
        withNull.add(requirement);
        withNull.add(null);
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NULL_VALUE,
                () -> AllocationSetDefinition.create(
                        requirement.allocationSetId(),
                        AllocationTestFixtures.work(suffix),
                        request,
                        withNull,
                        ordering.planningCycleReference(),
                        ordering.requestCreationSimulationTick(),
                        OptionalLong.empty(),
                        AllocationMetadata.empty(),
                        AllocationSchema.CURRENT_VERSION
                )
        );

        List<String> fieldNames = Arrays.stream(AllocationSetDefinition.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        assertEquals(
                List.of(),
                fieldNames.stream()
                        .filter(name -> name.toLowerCase().contains("status")
                                || name.toLowerCase().contains("remaining")
                                || name.toLowerCase().contains("retry")
                                || name.toLowerCase().contains("runtime"))
                        .toList()
        );
    }
}
