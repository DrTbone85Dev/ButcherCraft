package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationCyclePublicationTest {
    @Test
    void everyDeterministicPublicationFaultLeavesAllStateUnchanged() {
        for (AllocationPublicationFault fault : List.of(
                AllocationPublicationFault.AFTER_COMMITMENT_REGISTRATION,
                AllocationPublicationFault.AFTER_RUNTIME_TRANSITIONS,
                AllocationPublicationFault.AFTER_REPORT_REGISTRATION
        )) {
            var scenario = scenario("fault_" + fault.name().toLowerCase());
            ServiceSnapshot before = snapshot(scenario.service());

            AllocationCycleOperationResult<AllocationCycleResult> execution =
                    new AllocationCycleExecutor().execute(
                            scenario.input(),
                            scenario.service(),
                            fault
                    );

            assertFalse(execution.accepted());
            assertEquals(
                    AllocationCycleFailureCode.PUBLICATION_FAILED,
                    execution.failures().getFirst().code()
            );
            assertEquals(before, snapshot(scenario.service()));
        }
    }

    @Test
    void duplicateCycleIsRejectedWithoutDoubleAllocation() {
        var scenario = scenario("duplicate_cycle");
        AllocationCycleExecutor executor = new AllocationCycleExecutor();
        AllocationCycleOperationResult<AllocationCycleResult> first =
                executor.execute(scenario.input(), scenario.service());
        assertTrue(first.accepted(), () -> first.failures().toString());
        ServiceSnapshot published = snapshot(scenario.service());

        AllocationCycleOperationResult<AllocationCycleResult> duplicate =
                executor.execute(scenario.input(), scenario.service());

        assertFalse(duplicate.accepted());
        assertEquals(
                AllocationCycleFailureCode.DUPLICATE_CYCLE,
                duplicate.failures().getFirst().code()
        );
        assertEquals(published, snapshot(scenario.service()));
        assertEquals(1, scenario.service().definitions().commitmentCount());
        assertEquals(1, scenario.service().reports().size());
        assertEquals(1, scenario.service().traces().size());
    }

    @Test
    void optimisticSnapshotMismatchRejectsPublicationWithoutCycleMutation() {
        var scenario = scenario("stale");
        assertTrue(scenario.service().transition(
                AllocationRuntimeTransitionRequest.waiting(
                        scenario.graphs().getFirst().set().id(),
                        AllocationCycleFixtures.TICK,
                        "External deterministic test transition"
                )
        ).accepted());
        ServiceSnapshot externallyChanged = snapshot(scenario.service());

        AllocationCycleOperationResult<AllocationCycleResult> execution =
                new AllocationCycleExecutor().execute(
                        scenario.input(),
                        scenario.service()
                );

        assertFalse(execution.accepted());
        assertEquals(
                AllocationCycleFailureCode.STALE_RUNTIME_STATE,
                execution.failures().getFirst().code()
        );
        assertEquals(externallyChanged, snapshot(scenario.service()));
    }

    @Test
    void successfulPublicationExposesCompleteImmutableViewsTogether() {
        var scenario = scenario("complete");
        AllocationCycleOperationResult<AllocationCycleResult> execution =
                new AllocationCycleExecutor().execute(
                        scenario.input(),
                        scenario.service()
                );
        assertTrue(execution.accepted(), () -> execution.failures().toString());
        AllocationCycleResult result = execution.value().orElseThrow();

        assertTrue(scenario.service().definitions().findCommitment(
                result.createdCommitments().getFirst().id()
        ).isPresent());
        assertEquals(
                AllocationRuntimeStatus.ALLOCATED,
                scenario.service().runtimes().find(
                        result.successfulSetIds().getFirst()
                ).orElseThrow().status()
        );
        assertEquals(result.report(), scenario.service().reports()
                .find(result.cycleId()).orElseThrow());
        assertEquals(result.trace(), scenario.service().traces()
                .find(result.cycleId()).orElseThrow());
        assertEquals(2, scenario.service().history().size());
    }

    private static AllocationCycleFixtures.Scenario scenario(String suffix) {
        var set = AllocationCycleFixtures.set(
                suffix,
                2,
                AllocationCycleFixtures.demand(
                        "machine",
                        "1",
                        CapacityUnits.PRODUCTION_SLOT,
                        Optional.empty()
                )
        );
        var observation = AllocationCycleFixtures.observation(
                suffix,
                ResourceExclusivityMode.SHARED,
                "1",
                CapacityTypeId.of("test:machine"),
                CapacityUnits.PRODUCTION_SLOT
        );
        return AllocationCycleFixtures.scenario(
                AllocationCycleFixtures.TICK,
                List.of(set),
                List.of(observation)
        );
    }

    private static ServiceSnapshot snapshot(AllocationRuntimeService service) {
        return new ServiceSnapshot(
                service.definitions().requirements(),
                service.definitions().requests(),
                service.definitions().sets(),
                service.definitions().commitments(),
                service.runtimes().views(),
                service.reports().reports(),
                service.history().records(),
                service.traces().traces()
        );
    }

    private record ServiceSnapshot(
            List<RequirementDefinition> requirements,
            List<AllocationRequestDefinition> requests,
            List<AllocationSetDefinition> sets,
            List<AllocationCommitmentDefinition> commitments,
            List<AllocationRuntimeView> runtimes,
            List<AllocationReport> reports,
            List<AllocationRuntimeTransitionRecord> history,
            List<AllocationCycleTrace> traces
    ) {
    }
}
