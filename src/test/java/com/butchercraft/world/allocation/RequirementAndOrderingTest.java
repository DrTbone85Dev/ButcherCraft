package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementAndOrderingTest {
    @Test
    void requirementDerivesStableIdentityAndKeepsOnlyExternalReferences() {
        RequirementDefinition first = AllocationTestFixtures.requirement("stable");
        RequirementDefinition second = AllocationTestFixtures.requirement("stable");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertEquals(first.id(), AllocationIds.requirementId(
                first.allocationSetId(),
                first.executionWorkReference(),
                first.resourceCategory(),
                first.capacityTypeId(),
                first.exactResourceId(),
                first.requiredQuantity(),
                first.schemaVersion()
        ));
        assertTrue(first.exactCapacityKey().isEmpty());
        assertTrue(first.selectorKey().contains("butchercraft:production"));
    }

    @Test
    void requirementValidationAggregatesStructuralFailures() {
        RequirementDefinition valid = AllocationTestFixtures.requirement("invalid");
        AllocationValidationException failure = AllocationTestFixtures.failure(
                () -> new RequirementDefinition(
                        RequirementId.of("example:wrong"),
                        valid.allocationSetId(),
                        valid.executionWorkReference(),
                        valid.resourceCategory(),
                        valid.capacityTypeId(),
                        Optional.empty(),
                        AllocationQuantity.zero(CapacityUnits.ENERGY),
                        CapacityUnits.MACHINE_TIME,
                        -1L,
                        AllocationMetadata.empty(),
                        2
                )
        );

        List<AllocationValidationFailureCode> codes = failure.failures().stream()
                .map(AllocationValidationFailure::code)
                .toList();
        assertTrue(codes.contains(AllocationValidationFailureCode.ZERO_QUANTITY));
        assertTrue(codes.contains(AllocationValidationFailureCode.INCOMPATIBLE_UNIT));
        assertTrue(codes.contains(AllocationValidationFailureCode.INVALID_SIMULATION_TICK));
        assertTrue(codes.contains(AllocationValidationFailureCode.INVALID_SCHEMA_VERSION));
    }

    @Test
    void orderingContextUsesExplicitSimulationTimeAndRejectsTemporalContradictions() {
        AllocationOrderingContext context = AllocationTestFixtures.ordering("age");
        assertEquals(100L, context.starvationAge(200L));
        AllocationOrderingContext large = AllocationTestFixtures.ordering(
                "large",
                1,
                1,
                OptionalLong.of(Long.MAX_VALUE),
                0L,
                Long.MAX_VALUE,
                Long.MAX_VALUE
        );
        assertEquals(Long.MAX_VALUE, large.starvationAge(Long.MAX_VALUE));
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                () -> context.starvationAge(99L)
        );
        AllocationTestFixtures.assertFailure(
                AllocationValidationFailureCode.INVALID_ORDERING_CONTEXT,
                () -> AllocationTestFixtures.ordering(
                        "invalid_time",
                        1,
                        1,
                        OptionalLong.empty(),
                        20L,
                        19L,
                        0L
                )
        );
    }

    @Test
    void requestComparatorImplementsTheCanonicalRfcOrdering() {
        assertOrdered(
                request("horizon_a", 0, 0, OptionalLong.empty(), 100L, 120L, 0L),
                request("horizon_b", 1, 4, OptionalLong.of(1L), 0L, 120L, 0L)
        );
        assertOrdered(
                request("priority_a", 1, 4, OptionalLong.empty(), 100L, 120L, 0L),
                request("priority_b", 1, 3, OptionalLong.of(1L), 0L, 120L, 0L)
        );
        assertOrdered(
                request("required_a", 1, 2, OptionalLong.of(300L), 100L, 120L, 0L),
                request("required_b", 1, 2, OptionalLong.of(400L), 0L, 120L, 0L)
        );
        assertOrdered(
                request("present_a", 1, 2, OptionalLong.of(500L), 100L, 120L, 0L),
                request("absent_b", 1, 2, OptionalLong.empty(), 0L, 120L, 0L)
        );
        assertOrdered(
                request("starved_a", 1, 2, OptionalLong.empty(), 10L, 120L, 0L),
                request("starved_b", 1, 2, OptionalLong.empty(), 20L, 120L, 0L)
        );
        assertOrdered(
                request("sequence_a", 1, 2, OptionalLong.empty(), 100L, 120L, 1L),
                request("sequence_b", 1, 2, OptionalLong.empty(), 100L, 120L, 2L)
        );

        AllocationOrderingContext tied = AllocationTestFixtures.ordering(
                "tied",
                1,
                2,
                OptionalLong.empty(),
                100L,
                120L,
                1L
        );
        AllocationRequestDefinition first = AllocationTestFixtures.request("id_a", tied);
        AllocationRequestDefinition second = AllocationTestFixtures.request("id_b", tied);
        int expected = first.id().compareTo(second.id());
        int actual = AllocationRequestDefinition.canonicalComparator(
                AllocationTestFixtures.CURRENT_TICK
        ).compare(first, second);
        assertEquals(Integer.signum(expected), Integer.signum(actual));
    }

    @Test
    void requestOrderingIsStableForPermutedInputs() {
        List<AllocationRequestDefinition> canonical = new ArrayList<>(List.of(
                request("c", 2, 1, OptionalLong.empty(), 100L, 120L, 3L),
                request("a", 0, 1, OptionalLong.empty(), 100L, 120L, 1L),
                request("b", 1, 4, OptionalLong.of(500L), 100L, 120L, 2L)
        ));
        List<AllocationRequestDefinition> reversed = new ArrayList<>(canonical.reversed());
        var comparator = AllocationRequestDefinition.canonicalComparator(
                AllocationTestFixtures.CURRENT_TICK
        );
        canonical.sort(comparator);
        reversed.sort(comparator);

        assertEquals(canonical, reversed);
        assertNotEquals(canonical.getFirst().id(), canonical.getLast().id());
    }

    private static AllocationRequestDefinition request(
            String suffix,
            int horizon,
            int priority,
            OptionalLong requiredBy,
            long needTick,
            long requestTick,
            long sequence
    ) {
        return AllocationTestFixtures.request(
                suffix,
                AllocationTestFixtures.ordering(
                        suffix,
                        horizon,
                        priority,
                        requiredBy,
                        needTick,
                        requestTick,
                        sequence
                )
        );
    }

    private static void assertOrdered(
            AllocationRequestDefinition first,
            AllocationRequestDefinition second
    ) {
        assertTrue(
                AllocationRequestDefinition.canonicalComparator(
                        AllocationTestFixtures.CURRENT_TICK
                ).compare(first, second) < 0,
                () -> first.id() + " should precede " + second.id()
        );
    }
}
