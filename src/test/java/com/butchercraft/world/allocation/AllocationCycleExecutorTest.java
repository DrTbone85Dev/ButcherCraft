package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCycleExecutorTest {
    @Test
    void emptyCyclePublishesCompleteDeterministicEvidence() {
        AllocationRuntimeService service = new AllocationRuntimeService(
                AllocationRegistry.empty()
        );
        AllocationCycleInput input = AllocationCycleInput.fromRegistries(
                AllocationCycleContext.firstFit(AllocationCycleFixtures.TICK),
                List.of(),
                List.of(),
                service.definitions(),
                service.runtimes(),
                List.of()
        );

        AllocationCycleOperationResult<AllocationCycleResult> execution =
                new AllocationCycleExecutor().execute(input, service);

        assertTrue(execution.accepted());
        AllocationCycleResult result = execution.value().orElseThrow();
        assertTrue(result.canonicalSetOrder().isEmpty());
        assertEquals(11, result.trace().phases().size());
        assertEquals(1, service.reports().size());
        assertEquals(1, service.traces().size());
    }

    @Test
    void singleRequirementUsesCanonicalFirstFitAndAllocatesRuntime() {
        var set = AllocationCycleFixtures.set(
                "first_fit",
                3,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var resourceZ = AllocationCycleFixtures.observation(
                "z",
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        var resourceA = AllocationCycleFixtures.observation(
                "a",
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(resourceZ, resourceA)
        );

        AllocationCycleResult result = execute(scenario);

        assertEquals(List.of(set.set().id()), result.successfulSetIds());
        assertEquals(
                resourceA.resource().resourceId(),
                result.createdCommitments().getFirst().resourceId()
        );
        assertEquals(
                AllocationRuntimeStatus.ALLOCATED,
                scenario.service().runtimes().find(
                        set.set().id()
                ).orElseThrow().status()
        );
        assertEquals(1, result.createdCommitments().size());
        assertEquals(AllocationPublicationStatus.PUBLISHED, result.publicationStatus());
        assertEquals(result.digests().reportDigest(),
                AllocationCycleDigestSupport.report(result.report()));
    }

    @Test
    void ordinaryScarcityTransitionsRequestedRuntimeToWaiting() {
        var set = AllocationCycleFixtures.set(
                "waiting",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "waiting",
                ResourceExclusivityMode.SHARED,
                "0",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(observation)
        );

        AllocationCycleResult result = execute(scenario);

        assertEquals(List.of(set.set().id()), result.waitingSetIds());
        assertTrue(result.createdCommitments().isEmpty());
        AllocationRuntimeView runtime = scenario.service().runtimes()
                .find(set.set().id()).orElseThrow();
        assertEquals(AllocationRuntimeStatus.WAITING, runtime.status());
        assertEquals(
                AllocationRuntimeFailureCode.CAPACITY_UNAVAILABLE,
                runtime.failureCode().orElseThrow()
        );
    }

    @Test
    void higherPrioritySetWinsMixedSharedAndExclusiveContentionAtomically() {
        var high = AllocationCycleFixtures.set(
                "high",
                10,
                AllocationCycleFixtures.demand(
                        "shared",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                ),
                AllocationCycleFixtures.demand(
                        "exclusive",
                        "1",
                        CapacityUnits.MACHINE_TIME,
                        Optional.empty()
                )
        );
        var low = AllocationCycleFixtures.set(
                "low",
                1,
                AllocationCycleFixtures.demand(
                        "shared",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                ),
                AllocationCycleFixtures.demand(
                        "exclusive",
                        "1",
                        CapacityUnits.MACHINE_TIME,
                        Optional.empty()
                )
        );
        var shared = AllocationCycleFixtures.observation(
                "shared",
                ResourceExclusivityMode.SHARED,
                "2",
                CapacityTypeId.of("test:shared"),
                CapacityUnits.PRODUCTION_SLOT
        );
        var exclusive = AllocationCycleFixtures.observation(
                "exclusive",
                ResourceExclusivityMode.EXCLUSIVE,
                "1",
                CapacityTypeId.of("test:exclusive"),
                CapacityUnits.MACHINE_TIME
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(low, high),
                List.of(exclusive, shared)
        );

        AllocationCycleResult result = execute(scenario);

        assertEquals(high.set().id(), result.canonicalSetOrder().getFirst());
        assertEquals(List.of(high.set().id()), result.successfulSetIds());
        assertEquals(List.of(low.set().id()), result.waitingSetIds());
        assertEquals(2, result.createdCommitments().size());
        AllocationLedgerEntryView sharedLedger = result.finalLedger().stream()
                .filter(entry -> entry.capacityKey().capacityTypeId().equals(
                        CapacityTypeId.of("test:shared")
                ))
                .findFirst().orElseThrow();
        assertEquals("1", sharedLedger.remainingQuantity().canonicalAmount());
        assertEquals("1", sharedLedger.proposedCommittedQuantity().canonicalAmount());
        assertEquals(1, result.conflicts().size());
        assertEquals(
                List.of(high.set().id()),
                result.conflicts().getFirst().winnerSetIds()
        );
        assertEquals(
                List.of(low.set().id()),
                result.conflicts().getFirst().loserSetIds()
        );
    }

    @Test
    void incompatibleUnitIsFailedEvidenceRatherThanScarcity() {
        var set = AllocationCycleFixtures.set(
                "unit",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "unit",
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.MACHINE_TIME
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(observation)
        );

        AllocationCycleResult result = execute(scenario);

        assertEquals(List.of(set.set().id()), result.failedSetIds());
        assertEquals(
                AllocationCycleFailureCode.INVALID_CAPACITY_UNIT,
                result.failures().getFirst().code()
        );
        assertEquals(
                AllocationRuntimeStatus.REQUESTED,
                scenario.service().runtimes().find(
                        set.set().id()
                ).orElseThrow().status()
        );
        assertTrue(result.createdCommitments().isEmpty());
    }

    @Test
    void expiredCandidateIsReportedWithoutAnUnauthorizedLifecycleTransition() {
        var original = AllocationCycleFixtures.set(
                "expired_candidate",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        AllocationSetDefinition expiredSet = AllocationSetDefinition.create(
                original.set().id(),
                original.set().executionWorkReference(),
                original.request(),
                original.requirements(),
                original.set().planningCycleReference(),
                original.set().creationSimulationTick(),
                OptionalLong.of(199L),
                AllocationMetadata.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        var expired = new AllocationCycleFixtures.SetGraph(
                original.requirements(),
                original.request(),
                expiredSet
        );
        var observation = AllocationCycleFixtures.observation(
                "expired_candidate",
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        var scenario = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(expired),
                List.of(observation)
        );

        AllocationCycleResult result = execute(scenario);

        assertEquals(List.of(expiredSet.id()), result.failedSetIds());
        assertEquals(List.of(expiredSet.id()), result.report().expiredSetIds());
        assertEquals(
                AllocationRuntimeStatus.REQUESTED,
                scenario.service().runtimes().find(
                        expiredSet.id()
                ).orElseThrow().status()
        );
        assertTrue(result.createdCommitments().isEmpty());
    }

    @Test
    void waitingSetCanAllocateOnLaterExplicitCycleWithoutInternalRetry() {
        var set = AllocationCycleFixtures.set(
                "later",
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var unavailable = AllocationCycleFixtures.observation(
                "later",
                ResourceExclusivityMode.SHARED,
                "0",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        var first = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(unavailable)
        );
        AllocationCycleResult waiting = execute(first);
        assertEquals(List.of(set.set().id()), waiting.waitingSetIds());

        var available = AllocationCycleFixtures.observation(
                "later",
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT,
                ResourceAvailability.AVAILABLE,
                AllocationCycleFixtures.TICK + 1L
        );
        var second = AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK + 1L,
                List.of(set),
                List.of(available),
                first.service()
        );
        AllocationCycleResult allocated = execute(second);

        assertEquals(List.of(set.set().id()), allocated.successfulSetIds());
        assertEquals(
                AllocationRuntimeStatus.ALLOCATED,
                second.service().runtimes().find(
                        set.set().id()
                ).orElseThrow().status()
        );
        assertEquals(2, second.service().reports().size());
        assertEquals(2, second.service().traces().size());
    }

    @Test
    void equivalentIndependentCyclesReplayExactly() {
        var first = replayScenario();
        var second = replayScenario();

        AllocationCycleResult firstResult = execute(first);
        AllocationCycleResult secondResult = execute(second);

        assertEquals(firstResult, secondResult);
        assertEquals(
                firstResult.digests().resultDigest(),
                secondResult.digests().resultDigest()
        );
        assertEquals(
                first.service().history().records(),
                second.service().history().records()
        );
        assertNotEquals(
                firstResult.digests().initialLedgerDigest(),
                firstResult.digests().finalLedgerDigest()
        );
    }

    private static AllocationCycleFixtures.Scenario replayScenario() {
        var first = AllocationCycleFixtures.set(
                "replay_first",
                5,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var second = AllocationCycleFixtures.set(
                "replay_second",
                1,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                "replay",
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        return AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(second, first),
                List.of(observation)
        );
    }

    private static AllocationCycleResult execute(
            AllocationCycleFixtures.Scenario scenario
    ) {
        AllocationCycleOperationResult<AllocationCycleResult> execution =
                new AllocationCycleExecutor().execute(
                        scenario.input(),
                        scenario.service()
                );
        assertTrue(execution.accepted(), () -> execution.failures().toString());
        return execution.value().orElseThrow();
    }
}
