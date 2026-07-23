package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCommitmentTest {
    @Test
    void commitmentIsImmutableDeterministicAndEvidenceBacked() {
        RequirementDefinition requirement = exactRequirement("commitment");
        List<ExternalReference> observations = new ArrayList<>(List.of(
                AllocationTestFixtures.observation("z"),
                AllocationTestFixtures.observation("a")
        ));
        AllocationCommitmentDefinition first = AllocationCommitmentDefinition.create(
                AllocationCycleId.forTick(200L),
                requirement,
                requirement.exactResourceId().orElseThrow(),
                CapacityId.of("example:machine_hours"),
                AllocationQuantity.of("2", CapacityUnits.MACHINE_TIME),
                200L,
                OptionalLong.of(300L),
                observations,
                AllocationMetadata.empty()
        );
        observations.clear();
        AllocationCommitmentDefinition second = AllocationCommitmentDefinition.create(
                AllocationCycleId.forTick(200L),
                requirement,
                requirement.exactResourceId().orElseThrow(),
                CapacityId.of("example:machine_hours"),
                AllocationQuantity.of("2", CapacityUnits.MACHINE_TIME),
                200L,
                OptionalLong.of(300L),
                List.of(
                        AllocationTestFixtures.observation("a"),
                        AllocationTestFixtures.observation("z")
                ),
                AllocationMetadata.empty()
        );

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.id(), second.id());
        assertEquals(
                List.of(
                        AllocationTestFixtures.observation("a"),
                        AllocationTestFixtures.observation("z")
                ),
                first.sourceObservationReferences()
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.sourceObservationReferences().add(
                        AllocationTestFixtures.observation("new")
                )
        );
    }

    @Test
    void commitmentRejectsZeroWrongUnitWrongResourceAndInvalidExpiration() {
        RequirementDefinition requirement = exactRequirement("invalid");
        ResourceId exact = requirement.exactResourceId().orElseThrow();
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.ZERO_QUANTITY,
                () -> create(
                        requirement,
                        exact,
                        AllocationQuantity.zero(CapacityUnits.MACHINE_TIME),
                        OptionalLong.empty(),
                        List.of(AllocationTestFixtures.observation("zero"))
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INCOMPATIBLE_UNIT,
                () -> create(
                        requirement,
                        exact,
                        AllocationQuantity.of("1", CapacityUnits.ENERGY),
                        OptionalLong.empty(),
                        List.of(AllocationTestFixtures.observation("unit"))
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INCONSISTENT_SET_ASSOCIATION,
                () -> create(
                        requirement,
                        ResourceId.of("example:another_line"),
                        AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME),
                        OptionalLong.empty(),
                        List.of(AllocationTestFixtures.observation("resource"))
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_EXPIRATION,
                () -> create(
                        requirement,
                        exact,
                        AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME),
                        OptionalLong.of(199L),
                        List.of(AllocationTestFixtures.observation("expiration"))
                )
        );
    }

    @Test
    void commitmentRejectsMissingOrDuplicateObservationEvidence() {
        RequirementDefinition requirement = exactRequirement("evidence");
        ExternalReference observation = AllocationTestFixtures.observation("duplicate");
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.NONCANONICAL_INPUT,
                () -> create(
                        requirement,
                        requirement.exactResourceId().orElseThrow(),
                        AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME),
                        OptionalLong.empty(),
                        List.of()
                )
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.DUPLICATE_OBSERVATION_REFERENCE,
                () -> create(
                        requirement,
                        requirement.exactResourceId().orElseThrow(),
                        AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME),
                        OptionalLong.empty(),
                        List.of(observation, observation)
                )
        );
    }

    @Test
    void commitmentOrderingIncludesFinalStableIdentity() {
        RequirementDefinition requirement = exactRequirement("ordering");
        AllocationCommitmentDefinition first = create(
                requirement,
                requirement.exactResourceId().orElseThrow(),
                AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME),
                OptionalLong.empty(),
                List.of(AllocationTestFixtures.observation("ordering"))
        );
        AllocationCommitmentDefinition second = AllocationCommitmentDefinition.create(
                AllocationCycleId.forTick(200L),
                requirement,
                requirement.exactResourceId().orElseThrow(),
                CapacityId.of("example:other_capacity"),
                AllocationQuantity.of("1", CapacityUnits.MACHINE_TIME),
                200L,
                OptionalLong.empty(),
                List.of(AllocationTestFixtures.observation("ordering")),
                AllocationMetadata.empty()
        );

        assertNotEquals(first.id(), second.id());
        assertEquals(
                Integer.signum(first.id().compareTo(second.id())),
                Integer.signum(first.compareTo(second))
        );
        assertTrue(first.toString().contains(first.id().value()));
    }

    private static RequirementDefinition exactRequirement(String suffix) {
        AllocationOrderingContext ordering = AllocationTestFixtures.ordering(suffix);
        return AllocationTestFixtures.requirement(
                suffix,
                ordering,
                ResourceCategories.PRODUCTION,
                CapacityTypeId.of("butchercraft:machine_time"),
                Optional.of(ResourceId.of("example:line_" + suffix)),
                AllocationQuantity.of("2", CapacityUnits.MACHINE_TIME)
        );
    }

    private static AllocationCommitmentDefinition create(
            RequirementDefinition requirement,
            ResourceId resourceId,
            AllocationQuantity quantity,
            OptionalLong expiration,
            List<ExternalReference> observations
    ) {
        return AllocationCommitmentDefinition.create(
                AllocationCycleId.forTick(200L),
                requirement,
                resourceId,
                CapacityId.of("example:machine_hours"),
                quantity,
                200L,
                expiration,
                observations,
                AllocationMetadata.empty()
        );
    }
}
