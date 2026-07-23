package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCycleInputLedgerTest {
    private static final CapacityTypeId MACHINE =
            CapacityTypeId.of("test:machine");

    @Test
    void emptyAndMinimalInputsAreImmutableAndCanonical() {
        AllocationCycleInput empty = AllocationCycleInput.fromRegistries(
                AllocationCycleContext.firstFit(AllocationCycleFixtures.TICK),
                List.of(),
                List.of(),
                AllocationRegistry.empty(),
                AllocationRuntimeRegistry.empty(),
                List.of()
        );
        assertTrue(empty.candidateSets().isEmpty());

        var set = AllocationCycleFixtures.set(
                "canonical",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var firstObservation = AllocationCycleFixtures.observation(
                "z",
                ResourceExclusivityMode.SHARED,
                "2",
                MACHINE,
                CapacityUnits.PRODUCTION_SLOT
        );
        var secondObservation = AllocationCycleFixtures.observation(
                "a",
                ResourceExclusivityMode.SHARED,
                "2",
                MACHINE,
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(firstObservation, secondObservation)
        );
        List<ObservedResourceSnapshot> resources = new ArrayList<>(
                scenario.input().resources()
        );
        List<ObservedCapacitySnapshot> capacities = new ArrayList<>(
                scenario.input().capacities()
        );
        Collections.reverse(resources);
        Collections.reverse(capacities);
        AllocationCycleInput reordered = AllocationCycleInput.fromRegistries(
                scenario.input().context(),
                resources,
                capacities,
                scenario.service().definitions(),
                scenario.service().runtimes(),
                List.of(set.set().id())
        );
        resources.clear();
        capacities.clear();

        assertEquals(scenario.input(), reordered);
        assertEquals(2, reordered.resources().size());
        assertThrows(
                UnsupportedOperationException.class,
                () -> reordered.resources().clear()
        );
        assertEquals(
                AllocationCycleDigestSupport.input(scenario.input()),
                AllocationCycleDigestSupport.input(reordered)
        );
    }

    @Test
    void duplicateAndUnknownCycleReferencesFailDeterministically() {
        var set = AllocationCycleFixtures.set(
                "invalid",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "invalid",
                ResourceExclusivityMode.SHARED,
                "1",
                MACHINE,
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(observation)
        );

        AllocationCycleValidationException duplicate = assertThrows(
                AllocationCycleValidationException.class,
                () -> AllocationCycleInput.fromRegistries(
                        scenario.input().context(),
                        List.of(observation.resource(), observation.resource()),
                        List.of(observation.capacity()),
                        scenario.service().definitions(),
                        scenario.service().runtimes(),
                        List.of(set.set().id())
                )
        );
        assertEquals(
                AllocationCycleFailureCode.DUPLICATE_RESOURCE,
                duplicate.failures().getFirst().code()
        );

        AllocationCycleValidationException unknown = assertThrows(
                AllocationCycleValidationException.class,
                () -> AllocationCycleInput.fromRegistries(
                        scenario.input().context(),
                        scenario.input().resources(),
                        scenario.input().capacities(),
                        scenario.service().definitions(),
                        scenario.service().runtimes(),
                        List.of(AllocationSetId.of("test:missing"))
                )
        );
        assertEquals(
                AllocationCycleFailureCode.UNKNOWN_ALLOCATION_SET,
                unknown.failures().getFirst().code()
        );
    }

    @Test
    void duplicateDefinitionAndCommitmentIdentitiesAreCycleFatal() {
        var set = AllocationCycleFixtures.set(
                "duplicate_definitions",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "duplicate_definitions",
                ResourceExclusivityMode.SHARED,
                "2",
                MACHINE,
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(observation)
        );
        AllocationCommitmentDefinition commitment =
                AllocationCommitmentDefinition.create(
                        AllocationCycleId.forTick(150L),
                        set.requirements().getFirst(),
                        observation.resource().resourceId(),
                        observation.capacity().capacityId(),
                        set.requirements().getFirst().requiredQuantity(),
                        150L,
                        java.util.OptionalLong.of(500L),
                        List.of(
                                observation.resource()
                                        .authoritativeExternalReference(),
                                observation.capacity()
                                        .authoritativeExternalReference()
                        ),
                        AllocationMetadata.empty()
                );

        assertDuplicate(
                AllocationCycleFailureCode.DUPLICATE_REQUIREMENT,
                () -> AllocationCycleInput.of(
                        scenario.input().context(),
                        scenario.input().resources(),
                        scenario.input().capacities(),
                        List.of(
                                set.requirements().getFirst(),
                                set.requirements().getFirst()
                        ),
                        List.of(set.request()),
                        List.of(set.set()),
                        List.of(),
                        scenario.input().runtimes().views(),
                        List.of(set.set().id())
                )
        );
        assertDuplicate(
                AllocationCycleFailureCode.DUPLICATE_REQUEST,
                () -> AllocationCycleInput.of(
                        scenario.input().context(),
                        scenario.input().resources(),
                        scenario.input().capacities(),
                        set.requirements(),
                        List.of(set.request(), set.request()),
                        List.of(set.set()),
                        List.of(),
                        scenario.input().runtimes().views(),
                        List.of(set.set().id())
                )
        );
        assertDuplicate(
                AllocationCycleFailureCode.DUPLICATE_ALLOCATION_SET,
                () -> AllocationCycleInput.of(
                        scenario.input().context(),
                        scenario.input().resources(),
                        scenario.input().capacities(),
                        set.requirements(),
                        List.of(set.request()),
                        List.of(set.set(), set.set()),
                        List.of(),
                        scenario.input().runtimes().views(),
                        List.of(set.set().id())
                )
        );
        assertDuplicate(
                AllocationCycleFailureCode.DUPLICATE_COMMITMENT,
                () -> AllocationCycleInput.of(
                        scenario.input().context(),
                        scenario.input().resources(),
                        scenario.input().capacities(),
                        set.requirements(),
                        List.of(set.request()),
                        List.of(set.set()),
                        List.of(commitment, commitment),
                        scenario.input().runtimes().views(),
                        List.of(set.set().id())
                )
        );
    }

    @Test
    void invalidContextTickAndSchemaFailBeforeExecution() {
        assertEquals(
                AllocationCycleFailureCode.INVALID_SIMULATION_TICK,
                assertThrows(
                        AllocationCycleValidationException.class,
                        () -> AllocationCycleContext.firstFit(-1L)
                ).failures().getFirst().code()
        );
        assertEquals(
                AllocationCycleFailureCode.INVALID_SCHEMA_VERSION,
                assertThrows(
                        AllocationCycleValidationException.class,
                        () -> new AllocationCycleContext(
                                AllocationCycleId.forTick(
                                        AllocationCycleFixtures.TICK
                                ),
                                AllocationCycleFixtures.TICK,
                                AllocationPolicies.FIRST_FIT,
                                AllocationMetadata.empty(),
                                2
                        )
                ).failures().getFirst().code()
        );
    }

    @Test
    void candidateBatchBeyondStructuralBoundFailsWithoutTruncation() {
        List<AllocationSetId> candidates = new ArrayList<>(
                AllocationSchema.MAXIMUM_CYCLE_CANDIDATE_SETS + 1
        );
        for (int index = 0;
             index <= AllocationSchema.MAXIMUM_CYCLE_CANDIDATE_SETS;
             index++) {
            candidates.add(AllocationSetId.of("test:bounded_set_" + index));
        }
        AllocationCycleValidationException exception = assertThrows(
                AllocationCycleValidationException.class,
                () -> AllocationCycleInput.fromRegistries(
                        AllocationCycleContext.firstFit(
                                AllocationCycleFixtures.TICK
                        ),
                        List.of(),
                        List.of(),
                        AllocationRegistry.empty(),
                        AllocationRuntimeRegistry.empty(),
                        candidates
                )
        );
        assertEquals(
                AllocationCycleFailureCode.STRUCTURAL_BOUND_EXCEEDED,
                exception.failures().getFirst().code()
        );
    }

    @Test
    void exclusiveObservationMustExposeExactlyOneCompatibleUnit() {
        var set = AllocationCycleFixtures.set(
                "invalid_exclusive",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "invalid_exclusive",
                ResourceExclusivityMode.EXCLUSIVE,
                "2",
                MACHINE,
                CapacityUnits.PRODUCTION_SLOT
        );
        AllocationRegistryBuilder builder = AllocationRegistry.builder();
        set.requirements().forEach(builder::registerRequirement);
        builder.registerRequest(set.request()).registerSet(set.set());
        AllocationRuntimeService service = new AllocationRuntimeService(
                builder.build()
        );
        assertTrue(service.registerRequested(
                set.set().id(),
                AllocationMetadata.empty()
        ).accepted());

        AllocationCycleValidationException exception = assertThrows(
                AllocationCycleValidationException.class,
                () -> AllocationCycleInput.fromRegistries(
                        AllocationCycleContext.firstFit(
                                AllocationCycleFixtures.TICK
                        ),
                        List.of(observation.resource()),
                        List.of(observation.capacity()),
                        service.definitions(),
                        service.runtimes(),
                        List.of(set.set().id())
                )
        );
        assertEquals(
                AllocationCycleFailureCode.INVALID_EXCLUSIVITY,
                exception.failures().getFirst().code()
        );
    }

    @Test
    void ledgerBranchMergeAndDiscardPreserveExactBalances() {
        var set = AllocationCycleFixtures.set(
                "branch",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "2",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "branch",
                ResourceExclusivityMode.SHARED,
                "5",
                MACHINE,
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(observation)
        );
        WorkingCapacityLedger ledger = WorkingCapacityLedger.from(scenario.input());
        String initial = ledger.digest();
        WorkingCapacityLedger.Branch discarded = ledger.branch();
        assertTrue(discarded.reserve(
                scenario.input().context().cycleId(),
                set.set().id(),
                set.requirements().getFirst()
        ).accepted());
        assertEquals(initial, ledger.digest());

        WorkingCapacityLedger.Branch merged = ledger.branch();
        assertTrue(merged.reserve(
                scenario.input().context().cycleId(),
                set.set().id(),
                set.requirements().getFirst()
        ).accepted());
        merged.merge();

        assertNotEquals(initial, ledger.digest());
        AllocationLedgerEntryView entry = ledger.entries().getFirst();
        assertEquals("5", entry.observedQuantity().canonicalAmount());
        assertEquals("2", entry.proposedCommittedQuantity().canonicalAmount());
        assertEquals("3", entry.remainingQuantity().canonicalAmount());
        assertThrows(
                UnsupportedOperationException.class,
                () -> entry.consumingSetIds().clear()
        );
    }

    @Test
    void exclusiveLedgerRejectsSecondReservationWithoutChangingParent() {
        ResourceId resourceId = ResourceId.of("test:resource_exclusive");
        var first = AllocationCycleFixtures.set(
                "exclusive_first",
                3,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.of(resourceId)
                )
        );
        var second = AllocationCycleFixtures.set(
                "exclusive_second",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.of(resourceId)
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "exclusive",
                ResourceExclusivityMode.EXCLUSIVE,
                "1",
                MACHINE,
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(first, second),
                List.of(observation)
        );
        WorkingCapacityLedger ledger = WorkingCapacityLedger.from(scenario.input());
        WorkingCapacityLedger.Branch winner = ledger.branch();
        assertTrue(winner.reserve(
                scenario.input().context().cycleId(),
                first.set().id(),
                first.requirements().getFirst()
        ).accepted());
        winner.merge();
        String committed = ledger.digest();

        WorkingCapacityLedger.Branch loser = ledger.branch();
        LedgerReservationResult result = loser.reserve(
                scenario.input().context().cycleId(),
                second.set().id(),
                second.requirements().getFirst()
        );
        assertFalse(result.accepted());
        assertEquals(
                AllocationCycleFailureCode.EXCLUSIVE_CONFLICT,
                result.failure().orElseThrow().code()
        );
        assertTrue(result.conflict().isPresent());
        assertEquals(committed, ledger.digest());
    }

    private static void assertDuplicate(
            AllocationCycleFailureCode expected,
            org.junit.jupiter.api.function.Executable executable
    ) {
        AllocationCycleValidationException exception = assertThrows(
                AllocationCycleValidationException.class,
                executable
        );
        assertEquals(expected, exception.failures().getFirst().code());
    }
}
