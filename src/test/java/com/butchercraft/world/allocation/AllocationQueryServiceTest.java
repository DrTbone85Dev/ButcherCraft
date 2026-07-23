package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationQueryServiceTest {
    @Test
    void queriesResolveDefinitionsRuntimeCommitmentsHistoryAndReports() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("queries");
        AllocationRuntimeService service = new AllocationRuntimeService(graph.definitions());
        assertTrue(service.registerRequested(
                graph.set().id(),
                AllocationMetadata.empty()
        ).accepted());
        assertTrue(service.transition(AllocationRuntimeTransitionRequest.allocated(
                graph.set().id(),
                200L,
                List.of(graph.commitment().id())
        )).accepted());
        assertTrue(service.transition(AllocationRuntimeTransitionRequest.active(
                graph.set().id(),
                210L
        )).accepted());
        AllocationReport report = AllocationTestFixtures.report(graph, 200L);
        assertTrue(service.registerReport(report).accepted());

        AllocationQueryService queries = service.queries();
        assertEquals(graph.request(), queries.findRequest(graph.request().id()).orElseThrow());
        assertEquals(graph.set(), queries.findSet(graph.set().id()).orElseThrow());
        assertEquals(
                graph.commitment(),
                queries.findCommitment(graph.commitment().id()).orElseThrow()
        );
        assertEquals(
                AllocationRuntimeStatus.ACTIVE,
                queries.findRuntime(graph.set().id()).orElseThrow().status()
        );
        assertEquals(1, queries.findRuntimeByRequest(graph.request().id()).size());
        assertEquals(1, queries.activeRuntime().size());
        assertTrue(queries.waitingRuntime().isEmpty());
        assertEquals(
                List.of(graph.set()),
                queries.setsByPlanningCycle(graph.set().planningCycleReference())
        );
        assertEquals(
                List.of(graph.commitment()),
                queries.commitmentsBySet(graph.set().id())
        );
        assertEquals(
                List.of(graph.commitment()),
                queries.commitmentsByRequirement(graph.requirement().id())
        );
        assertEquals(
                List.of(graph.commitment()),
                queries.commitmentsByResource(graph.commitment().resourceId())
        );
        assertEquals(
                List.of(graph.commitment()),
                queries.commitmentsByExecutionWork(graph.set().executionWorkReference())
        );
        assertEquals(3, queries.history(graph.set().id()).size());
        assertEquals(report, queries.findReportByTick(200L).orElseThrow());
    }

    @Test
    void detachedQueryServiceDoesNotObserveLaterMutation() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("detached");
        AllocationRuntimeService service = new AllocationRuntimeService(graph.definitions());
        assertTrue(service.registerRequested(
                graph.set().id(),
                AllocationMetadata.empty()
        ).accepted());
        AllocationQueryService before = service.queries();
        assertTrue(service.transition(AllocationRuntimeTransitionRequest.failed(
                graph.set().id(),
                130L,
                AllocationRuntimeFailureCode.SET_FAILED,
                "Later failure"
        )).accepted());

        assertEquals(
                AllocationRuntimeStatus.REQUESTED,
                before.findRuntime(graph.set().id()).orElseThrow().status()
        );
        assertTrue(before.failedRuntime().isEmpty());
        AllocationQueryService after = service.queries();
        assertEquals(
                AllocationRuntimeStatus.FAILED,
                after.findRuntime(graph.set().id()).orElseThrow().status()
        );
    }

    @Test
    void statusQueriesReturnCanonicalImmutableViews() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("status");
        AllocationRuntimeService service = new AllocationRuntimeService(graph.definitions());
        assertTrue(service.registerRequested(
                graph.set().id(),
                AllocationMetadata.empty()
        ).accepted());
        assertTrue(service.transition(AllocationRuntimeTransitionRequest.waiting(
                graph.set().id(),
                130L,
                "Waiting"
        )).accepted());
        List<AllocationRuntimeView> waiting = service.queries().waitingRuntime();

        assertEquals(List.of(graph.set().id()), waiting.stream()
                .map(AllocationRuntimeView::allocationSetId)
                .toList());
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> waiting.add(waiting.getFirst())
        );
    }
}
