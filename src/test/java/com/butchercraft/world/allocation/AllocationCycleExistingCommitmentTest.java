package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCycleExistingCommitmentTest {
    @Test
    void activeCommitmentIsSubtractedExactlyOnceBeforeNewAllocation() {
        ExistingScenario scenario = scenario("2", OptionalLong.of(1_000L), false);

        WorkingCapacityLedger initial = WorkingCapacityLedger.from(scenario.input());
        AllocationLedgerEntryView before = initial.entries().getFirst();
        assertEquals("1", before.existingCommittedQuantity().canonicalAmount());
        assertEquals("1", before.remainingQuantity().canonicalAmount());

        AllocationCycleOperationResult<AllocationCycleResult> execution =
                new AllocationCycleExecutor().execute(
                        scenario.input(),
                        scenario.service()
                );
        assertTrue(execution.accepted(), () -> execution.failures().toString());
        AllocationLedgerEntryView after = execution.value().orElseThrow()
                .finalLedger().getFirst();
        assertEquals("1", after.existingCommittedQuantity().canonicalAmount());
        assertEquals("1", after.proposedCommittedQuantity().canonicalAmount());
        assertEquals("0", after.remainingQuantity().canonicalAmount());
    }

    @Test
    void releasedCommitmentDoesNotConsumeCapacity() {
        ExistingScenario scenario = scenario("1", OptionalLong.of(1_000L), true);

        WorkingCapacityLedger ledger = WorkingCapacityLedger.from(scenario.input());
        assertEquals(
                "0",
                ledger.entries().getFirst()
                        .existingCommittedQuantity().canonicalAmount()
        );
        AllocationCycleOperationResult<AllocationCycleResult> execution =
                new AllocationCycleExecutor().execute(
                        scenario.input(),
                        scenario.service()
                );
        assertTrue(execution.accepted(), () -> execution.failures().toString());
        assertEquals(
                List.of(scenario.candidate().set().id()),
                execution.value().orElseThrow().successfulSetIds()
        );
    }

    @Test
    void expiredActiveCommitmentFailsEnvelopeWithoutMutation() {
        AllocationCycleValidationException exception = assertThrows(
                AllocationCycleValidationException.class,
                () -> scenario("2", OptionalLong.of(199L), false)
        );
        assertEquals(
                AllocationCycleFailureCode.EXPIRED_ACTIVE_COMMITMENT,
                exception.failures().getFirst().code()
        );
    }

    @Test
    void duplicateAndOvercommittedExistingEvidenceFailLedgerConstruction() {
        var graph = AllocationCycleFixtures.set(
                "invalid_existing",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "2",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "invalid_existing",
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        AllocationCommitmentDefinition commitment = commitment(
                graph,
                observation,
                OptionalLong.of(1_000L)
        );
        AllocationCycleValidationException underflow = assertThrows(
                AllocationCycleValidationException.class,
                () -> WorkingCapacityLedger.fromObservations(
                        List.of(observation.resource()),
                        List.of(observation.capacity()),
                        List.of(commitment)
                )
        );
        assertEquals(
                AllocationCycleFailureCode.CAPACITY_UNDERFLOW,
                underflow.failures().getFirst().code()
        );

        var oneGraph = AllocationCycleFixtures.set(
                "duplicate_existing",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var twoCapacity = AllocationCycleFixtures.observation(
                "duplicate_existing",
                ResourceExclusivityMode.SHARED,
                "2",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        AllocationCommitmentDefinition duplicate = commitment(
                oneGraph,
                twoCapacity,
                OptionalLong.of(1_000L)
        );
        AllocationCycleValidationException duplicateFailure = assertThrows(
                AllocationCycleValidationException.class,
                () -> WorkingCapacityLedger.fromObservations(
                        List.of(twoCapacity.resource()),
                        List.of(twoCapacity.capacity()),
                        List.of(duplicate, duplicate)
                )
        );
        assertEquals(
                AllocationCycleFailureCode.DUPLICATE_COMMITMENT,
                duplicateFailure.failures().getFirst().code()
        );
    }

    private static ExistingScenario scenario(
            String observedAmount,
            OptionalLong commitmentExpiration,
            boolean release
    ) {
        var existing = AllocationCycleFixtures.set(
                "existing",
                5,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var candidate = AllocationCycleFixtures.set(
                "candidate",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "shared_existing",
                ResourceExclusivityMode.SHARED,
                observedAmount,
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        AllocationCommitmentDefinition commitment = commitment(
                existing,
                observation,
                commitmentExpiration
        );
        AllocationRegistryBuilder builder = AllocationRegistry.builder();
        for (var graph : List.of(existing, candidate)) {
            graph.requirements().forEach(builder::registerRequirement);
            builder.registerRequest(graph.request());
            builder.registerSet(graph.set());
        }
        builder.registerCommitment(commitment);
        AllocationRuntimeService service = new AllocationRuntimeService(builder.build());
        for (var graph : List.of(existing, candidate)) {
            assertTrue(service.registerRequested(
                    graph.set().id(),
                    AllocationMetadata.empty()
            ).accepted());
        }
        assertTrue(service.transition(AllocationRuntimeTransitionRequest.allocated(
                existing.set().id(),
                150L,
                List.of(commitment.id())
        )).accepted());
        if (release) {
            assertTrue(service.transition(AllocationRuntimeTransitionRequest.released(
                    existing.set().id(),
                    180L
            )).accepted());
        }
        AllocationCycleInput input = AllocationCycleInput.fromRegistries(
                AllocationCycleContext.firstFit(AllocationCycleFixtures.TICK),
                List.of(observation.resource()),
                List.of(observation.capacity()),
                service.definitions(),
                service.runtimes(),
                List.of(candidate.set().id())
        );
        return new ExistingScenario(service, input, candidate);
    }

    private static AllocationCommitmentDefinition commitment(
            AllocationCycleFixtures.SetGraph graph,
            AllocationCycleFixtures.Observation observation,
            OptionalLong expiration
    ) {
        return AllocationCommitmentDefinition.create(
                AllocationCycleId.forTick(150L),
                graph.requirements().getFirst(),
                observation.resource().resourceId(),
                observation.capacity().capacityId(),
                graph.requirements().getFirst().requiredQuantity(),
                150L,
                expiration,
                List.of(
                        observation.resource().authoritativeExternalReference(),
                        observation.capacity().authoritativeExternalReference()
                ),
                AllocationMetadata.empty()
        );
    }

    private record ExistingScenario(
            AllocationRuntimeService service,
            AllocationCycleInput input,
            AllocationCycleFixtures.SetGraph candidate
    ) {
    }
}
