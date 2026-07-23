package com.butchercraft.world.allocation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllocationRuntimeLifecycleTest {
    @Test
    void statusGraphMatchesRfcRevisionTwoExactly() {
        assertEquals(
                Set.of(
                        AllocationRuntimeStatus.WAITING,
                        AllocationRuntimeStatus.ALLOCATED,
                        AllocationRuntimeStatus.FAILED,
                        AllocationRuntimeStatus.EXPIRED
                ),
                AllocationRuntimeStatus.REQUESTED.allowedNextStatuses()
        );
        assertEquals(
                Set.of(
                        AllocationRuntimeStatus.ALLOCATED,
                        AllocationRuntimeStatus.FAILED,
                        AllocationRuntimeStatus.EXPIRED
                ),
                AllocationRuntimeStatus.WAITING.allowedNextStatuses()
        );
        assertEquals(
                Set.of(
                        AllocationRuntimeStatus.ACTIVE,
                        AllocationRuntimeStatus.RELEASED,
                        AllocationRuntimeStatus.FAILED,
                        AllocationRuntimeStatus.EXPIRED
                ),
                AllocationRuntimeStatus.ALLOCATED.allowedNextStatuses()
        );
        assertEquals(
                Set.of(
                        AllocationRuntimeStatus.RELEASED,
                        AllocationRuntimeStatus.FAILED,
                        AllocationRuntimeStatus.EXPIRED
                ),
                AllocationRuntimeStatus.ACTIVE.allowedNextStatuses()
        );
        assertTrue(AllocationRuntimeStatus.RELEASED.isTerminal());
        assertTrue(AllocationRuntimeStatus.FAILED.isTerminal());
        assertTrue(AllocationRuntimeStatus.EXPIRED.isTerminal());
        assertEquals(
                List.of(
                        AllocationRuntimeStatus.WAITING,
                        AllocationRuntimeStatus.ALLOCATED,
                        AllocationRuntimeStatus.FAILED,
                        AllocationRuntimeStatus.EXPIRED
                ),
                new ArrayList<>(
                        AllocationRuntimeStatus.REQUESTED.allowedNextStatuses()
                )
        );
    }

    @Test
    void servicePerformsTheCompleteWaitingAllocatedActiveReleasedLifecycle() {
        AllocationTestFixtures.AllocationGraph graph =
                AllocationTestFixtures.graph("lifecycle");
        AllocationRuntimeService service = new AllocationRuntimeService(
                AllocationTestFixtures.definitionsWithoutCommitment("lifecycle")
        );

        AllocationRuntimeView requested = accepted(
                service.registerRequested(graph.set().id(), AllocationMetadata.empty())
        );
        AllocationRuntimeView waiting = accepted(service.transition(
                AllocationRuntimeTransitionRequest.waiting(
                        graph.set().id(),
                        150L,
                        "Capacity has not been committed"
                )
        ));
        assertTrue(service.registerCommitments(List.of(graph.commitment())).accepted());
        AllocationRuntimeView allocated = accepted(service.transition(
                AllocationRuntimeTransitionRequest.allocated(
                        graph.set().id(),
                        200L,
                        List.of(graph.commitment().id())
                )
        ));
        AllocationRuntimeView active = accepted(service.transition(
                AllocationRuntimeTransitionRequest.active(graph.set().id(), 210L)
        ));
        AllocationRuntimeView released = accepted(service.transition(
                AllocationRuntimeTransitionRequest.released(graph.set().id(), 220L)
        ));

        assertEquals(AllocationRuntimeStatus.REQUESTED, requested.status());
        assertEquals(AllocationRuntimeStatus.WAITING, waiting.status());
        assertEquals(AllocationRuntimeStatus.ALLOCATED, allocated.status());
        assertEquals(AllocationRuntimeStatus.ACTIVE, active.status());
        assertEquals(AllocationRuntimeStatus.RELEASED, released.status());
        assertEquals(4L, released.revision());
        assertEquals(List.of(graph.commitment().id()), released.commitmentIds());
        assertEquals(5, service.history().size());
        assertEquals(released, service.runtimes().find(graph.set().id()).orElseThrow());
    }

    @Test
    void structuralServiceSupportsFailureAndExpirationWithoutAlgorithms() {
        AllocationTestFixtures.AllocationGraph failedGraph =
                AllocationTestFixtures.graph("failed");
        AllocationRuntimeService failedService = new AllocationRuntimeService(
                failedGraph.definitions()
        );
        accepted(failedService.registerRequested(
                failedGraph.set().id(),
                AllocationMetadata.empty()
        ));
        AllocationRuntimeView failed = accepted(failedService.transition(
                AllocationRuntimeTransitionRequest.failed(
                        failedGraph.set().id(),
                        130L,
                        AllocationRuntimeFailureCode.SET_FAILED,
                        "Owning subsystem rejected the set"
                )
        ));
        assertEquals(AllocationRuntimeStatus.FAILED, failed.status());
        assertEquals(
                AllocationRuntimeFailureCode.SET_FAILED,
                failed.failureCode().orElseThrow()
        );

        AllocationTestFixtures.AllocationGraph expiredGraph =
                AllocationTestFixtures.graph("expired");
        AllocationRuntimeService expiredService = new AllocationRuntimeService(
                expiredGraph.definitions()
        );
        accepted(expiredService.registerRequested(
                expiredGraph.set().id(),
                AllocationMetadata.empty()
        ));
        AllocationRuntimeView expired = accepted(expiredService.transition(
                AllocationRuntimeTransitionRequest.expired(
                        expiredGraph.set().id(),
                        500L,
                        "AllocationSet reached its explicit expiration"
                )
        ));
        assertEquals(AllocationRuntimeStatus.EXPIRED, expired.status());
        assertEquals(
                AllocationRuntimeFailureCode.SET_EXPIRED,
                expired.failureCode().orElseThrow()
        );
    }

    @Test
    void invalidBackwardAndTerminalTransitionsAreRejectedWithoutMutation() {
        AllocationTestFixtures.AllocationGraph graph =
                AllocationTestFixtures.graph("illegal");
        AllocationRuntimeService service = new AllocationRuntimeService(
                graph.definitions()
        );
        accepted(service.registerRequested(graph.set().id(), AllocationMetadata.empty()));

        AllocationRuntimeOperationResult<AllocationRuntimeView> backward =
                service.transition(AllocationRuntimeTransitionRequest.waiting(
                        graph.set().id(),
                        119L,
                        "Invalid backward transition"
                ));
        assertFalse(backward.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.INVALID_SIMULATION_TICK,
                backward.failures().getFirst().code()
        );
        assertEquals(
                AllocationRuntimeStatus.REQUESTED,
                service.runtimes().find(graph.set().id()).orElseThrow().status()
        );

        accepted(service.transition(AllocationRuntimeTransitionRequest.failed(
                graph.set().id(),
                130L,
                AllocationRuntimeFailureCode.SET_FAILED,
                "Terminal failure"
        )));
        AllocationRuntimeOperationResult<AllocationRuntimeView> terminal =
                service.transition(AllocationRuntimeTransitionRequest.expired(
                        graph.set().id(),
                        140L,
                        "Cannot expire terminal runtime"
                ));
        assertFalse(terminal.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.TERMINAL_RUNTIME,
                terminal.failures().getFirst().code()
        );
        assertEquals(2, service.history().size());
    }

    @Test
    void allocationRequiresExactlyOneKnownCommitmentPerRequirement() {
        AllocationTestFixtures.AllocationGraph graph =
                AllocationTestFixtures.graph("coverage");
        AllocationRuntimeService service = new AllocationRuntimeService(
                AllocationTestFixtures.definitionsWithoutCommitment("coverage")
        );
        accepted(service.registerRequested(graph.set().id(), AllocationMetadata.empty()));

        AllocationRuntimeOperationResult<AllocationRuntimeView> unknown =
                service.transition(AllocationRuntimeTransitionRequest.allocated(
                        graph.set().id(),
                        200L,
                        List.of(graph.commitment().id())
                ));
        assertFalse(unknown.accepted());
        assertEquals(
                AllocationRuntimeFailureCode.UNKNOWN_COMMITMENT,
                unknown.failures().getFirst().code()
        );
        assertTrue(service.registerCommitments(List.of(graph.commitment())).accepted());
        assertTrue(service.transition(AllocationRuntimeTransitionRequest.allocated(
                graph.set().id(),
                200L,
                List.of(graph.commitment().id())
        )).accepted());
    }

    private static AllocationRuntimeView accepted(
            AllocationRuntimeOperationResult<AllocationRuntimeView> result
    ) {
        assertTrue(result.accepted(), () -> "Unexpected rejection: " + result.failures());
        return result.value().orElseThrow();
    }
}
