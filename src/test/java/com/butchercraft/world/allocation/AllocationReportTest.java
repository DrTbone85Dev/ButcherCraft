package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AllocationReportTest {
    @Test
    void reportStructuresAreCanonicalImmutableAndDeterministic() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("report");
        AllocationReport report = AllocationTestFixtures.report(
                graph,
                AllocationTestFixtures.CURRENT_TICK
        );
        AllocationReport equal = AllocationTestFixtures.report(
                graph,
                AllocationTestFixtures.CURRENT_TICK
        );

        assertEquals(report, equal);
        assertEquals(report.hashCode(), equal.hashCode());
        assertEquals(List.of(graph.set().id()), report.successfulSetIds());
        assertEquals(List.of(graph.commitment().id()), report.commitmentIds());
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.successfulSetIds().add(graph.set().id())
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.workSummary().stageCounts().put("example:new", 1L)
        );
    }

    @Test
    void capacityAndConflictRecordsValidateExactUnitsAndBalance() {
        CapacityKey key = new CapacityKey(
                ResourceId.of("example:resource"),
                CapacityTypeId.of("example:machine_time"),
                CapacityUnits.MACHINE_TIME
        );
        AllocationCapacityReportEntry entry = new AllocationCapacityReportEntry(
                key,
                AllocationQuantity.of("10", CapacityUnits.MACHINE_TIME),
                AllocationQuantity.of("4", CapacityUnits.MACHINE_TIME),
                AllocationQuantity.of("6", CapacityUnits.MACHINE_TIME)
        );
        assertEquals("6", entry.remainingQuantity().canonicalAmount());

        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_REPORT,
                () -> new AllocationCapacityReportEntry(
                        key,
                        AllocationQuantity.of("10", CapacityUnits.MACHINE_TIME),
                        AllocationQuantity.of("4", CapacityUnits.MACHINE_TIME),
                        AllocationQuantity.of("7", CapacityUnits.MACHINE_TIME)
                )
        );
        AllocationConflictRecord conflict = new AllocationConflictRecord(
                AllocationConflictType.CAPACITY,
                key,
                List.of(AllocationSetId.of("example:winner")),
                List.of(AllocationSetId.of("example:loser")),
                AllocationQuantity.of("2", CapacityUnits.MACHINE_TIME),
                AllocationMetadata.empty()
        );
        assertEquals(AllocationConflictType.CAPACITY, conflict.type());
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_REPORT,
                () -> new AllocationConflictRecord(
                        AllocationConflictType.CAPACITY,
                        key,
                        List.of(AllocationSetId.of("example:same")),
                        List.of(AllocationSetId.of("example:same")),
                        AllocationQuantity.of("2", CapacityUnits.MACHINE_TIME),
                        AllocationMetadata.empty()
                )
        );
    }

    @Test
    void reportRejectsDuplicateOutcomeCategoriesCycleMismatchAndInvalidWorkBounds() {
        AllocationTestFixtures.AllocationGraph graph =
                AllocationTestFixtures.graph("invalid_report");
        AllocationReport valid = AllocationTestFixtures.report(
                graph,
                AllocationTestFixtures.CURRENT_TICK
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_REPORT,
                () -> new AllocationReport(
                        valid.allocationCycleId(),
                        List.of(graph.set().id()),
                        List.of(graph.set().id()),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        valid.commitmentIds(),
                        valid.conflicts(),
                        valid.capacities(),
                        valid.orderingContexts(),
                        valid.workSummary(),
                        valid.failures(),
                        valid.policyId(),
                        valid.simulationTick(),
                        valid.schemaVersion()
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_REPORT,
                () -> new AllocationReport(
                        AllocationCycleId.forTick(201L),
                        valid.successfulSetIds(),
                        valid.waitingSetIds(),
                        valid.rejectedSetIds(),
                        valid.failedSetIds(),
                        valid.releasedSetIds(),
                        valid.expiredSetIds(),
                        valid.commitmentIds(),
                        valid.conflicts(),
                        valid.capacities(),
                        valid.orderingContexts(),
                        valid.workSummary(),
                        valid.failures(),
                        valid.policyId(),
                        200L,
                        valid.schemaVersion()
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_REPORT,
                () -> new AllocationReportWorkSummary(
                        Map.of("example:stage", 1L),
                        11L,
                        10L,
                        false
                )
        );
    }

    @Test
    void reportRegistryRejectsDuplicateCyclesAndSupportsTickQueries() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("registry");
        AllocationReport report = AllocationTestFixtures.report(graph, 200L);
        AllocationReportRegistry registry = AllocationReportRegistry.of(List.of(report));

        assertEquals(report, registry.find(report.allocationCycleId()).orElseThrow());
        assertEquals(report, registry.findByTick(200L).orElseThrow());
        assertEquals(List.of(report), registry.findBetween(199L, 201L));
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                () -> AllocationReportRegistry.of(List.of(report, report))
        );
    }
}
