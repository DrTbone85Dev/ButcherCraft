package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationRuntimeServiceValidationTest {
    @Test
    void serviceRejectsUnknownAndDuplicateRuntimeRegistration() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("registration");
        AllocationRuntimeService service = new AllocationRuntimeService(graph.definitions());
        AllocationRuntimeOperationResult<AllocationRuntimeView> unknown =
                service.registerRequested(
                        AllocationSetId.of("example:unknown"),
                        AllocationMetadata.empty()
                );
        assertFalse(unknown.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.UNKNOWN_SET,
                unknown.failures().getFirst().code()
        );
        assertTrue(service.registerRequested(
                graph.set().id(),
                AllocationMetadata.empty()
        ).accepted());
        AllocationRuntimeOperationResult<AllocationRuntimeView> duplicate =
                service.registerRequested(
                        graph.set().id(),
                        AllocationMetadata.empty()
                );
        assertFalse(duplicate.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.DUPLICATE_RUNTIME,
                duplicate.failures().getFirst().code()
        );
    }

    @Test
    void commitmentAndReportRegistrationAreDuplicateSafe() {
        AllocationTestFixtures.AllocationGraph graph =
                AllocationTestFixtures.graph("definitions");
        AllocationRuntimeService service = new AllocationRuntimeService(
                AllocationTestFixtures.definitionsWithoutCommitment("definitions")
        );
        assertTrue(service.registerCommitments(List.of(graph.commitment())).accepted());
        AllocationRuntimeOperationResult<List<AllocationCommitmentDefinition>> duplicate =
                service.registerCommitments(List.of(graph.commitment()));
        assertFalse(duplicate.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                duplicate.failures().getFirst().code()
        );

        AllocationReport report = AllocationTestFixtures.report(graph, 200L);
        assertTrue(service.registerReport(report).accepted());
        AllocationRuntimeOperationResult<AllocationReport> duplicateReport =
                service.registerReport(report);
        assertFalse(duplicateReport.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                duplicateReport.failures().getFirst().code()
        );
    }

    @Test
    void loadedRuntimeRequiresKnownSetAndMatchingHistory() {
        AllocationTestFixtures.AllocationGraph graph = AllocationTestFixtures.graph("loaded");
        AllocationRuntimeView requested = AllocationSetRuntime.requested(
                graph.set(),
                AllocationMetadata.empty()
        ).snapshot();
        AllocationRuntimeTransitionRecord record = new AllocationRuntimeTransitionRecord(
                graph.set().id(),
                java.util.Optional.empty(),
                AllocationRuntimeStatus.REQUESTED,
                graph.set().creationSimulationTick(),
                0L,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                AllocationSchema.CURRENT_VERSION
        );
        AllocationRuntimeService loaded = new AllocationRuntimeService(
                graph.definitions(),
                List.of(requested),
                List.of(),
                AllocationHistory.of(List.of(record))
        );
        assertEquals(requested, loaded.runtimes().find(graph.set().id()).orElseThrow());

        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_HISTORY,
                () -> new AllocationRuntimeService(
                        graph.definitions(),
                        List.of(requested),
                        List.of(),
                        AllocationHistory.empty()
                )
        );
        AllocationTestFixtures.assertRuntimeFailure(
                AllocationRuntimeFailureCode.UNKNOWN_SET,
                () -> new AllocationRuntimeService(
                        AllocationRegistry.empty(),
                        List.of(requested),
                        List.of(),
                        AllocationHistory.of(List.of(record))
                )
        );
    }

    @Test
    void runtimeFailuresAndOperationResultsAreCanonical() {
        AllocationRuntimeFailure first = new AllocationRuntimeFailure(
                AllocationRuntimeFailureCode.UNKNOWN_SET,
                "example:set",
                "Unknown set"
        );
        AllocationRuntimeFailure second = new AllocationRuntimeFailure(
                AllocationRuntimeFailureCode.INVALID_TRANSITION,
                "example:set",
                "Invalid transition"
        );
        AllocationRuntimeOperationResult<Object> result =
                AllocationRuntimeOperationResult.rejected(
                        List.of(first, second, first)
                );

        assertFalse(result.accepted());
        assertEquals(List.of(first, second), result.failures());
        assertTrue(result.value().isEmpty());
    }

    @Test
    void reportRegistrationRejectsCommitmentsFromAnotherCycle() {
        AllocationTestFixtures.AllocationGraph graph =
                AllocationTestFixtures.graph("report_cycle");
        AllocationRuntimeService service = new AllocationRuntimeService(
                graph.definitions()
        );
        AllocationRuntimeOperationResult<AllocationReport> result =
                service.registerReport(AllocationTestFixtures.report(
                        graph,
                        AllocationTestFixtures.CURRENT_TICK + 1L
                ));

        assertFalse(result.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.INVALID_REPORT,
                result.failures().getFirst().code()
        );
        assertEquals(0, service.reports().size());
    }
}
