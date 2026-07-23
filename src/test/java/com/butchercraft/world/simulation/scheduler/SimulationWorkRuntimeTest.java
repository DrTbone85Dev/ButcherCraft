package com.butchercraft.world.simulation.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationWorkRuntimeTest {
    @Test
    void everyDeclaredStatusTransitionIsExplicitAndTerminalStatesAreIrreversible() {
        assertEquals(java.util.Set.of(SimulationWorkStatus.ELIGIBLE, SimulationWorkStatus.CANCELLED,
                        SimulationWorkStatus.EXPIRED),
                SimulationWorkStatus.SCHEDULED.allowedNextStatuses());
        assertEquals(java.util.Set.of(SimulationWorkStatus.RUNNING, SimulationWorkStatus.DEFERRED,
                        SimulationWorkStatus.CANCELLED, SimulationWorkStatus.EXPIRED),
                SimulationWorkStatus.ELIGIBLE.allowedNextStatuses());
        for (SimulationWorkStatus status : List.of(
                SimulationWorkStatus.COMPLETED, SimulationWorkStatus.FAILED,
                SimulationWorkStatus.CANCELLED, SimulationWorkStatus.EXPIRED
        )) {
            assertTrue(status.isTerminal());
            assertTrue(status.allowedNextStatuses().isEmpty());
        }
    }

    @Test
    void scheduledEligibleRunningCompletedLifecycleIncrementsAttemptAndRevision() {
        ScheduledSimulationWork work = work("test:completed", RetryPolicy.never(), 1);
        SimulationWorkRuntime runtime = SimulationWorkRuntime.scheduled(work);

        runtime.makeEligible(1);
        runtime.start(1);
        runtime.complete(1, new WorkPayload(List.of(WorkPayloadEntry.string("test:result", "done"))));

        assertEquals(SimulationWorkStatus.COMPLETED, runtime.status());
        assertEquals(1, runtime.attemptCount());
        assertEquals(3, runtime.revision());
        assertEquals(1, runtime.startedTick().orElseThrow());
        assertEquals(1, runtime.completedTick().orElseThrow());
        assertThrows(IllegalStateException.class, () -> runtime.start(2));
    }

    @Test
    void runningMayRetryFailOrDeferThroughSeparateValidLifecycles() {
        SimulationWorkRuntime retry = running(work("test:retry_runtime", RetryPolicy.nextTick(), 2));
        retry.retry(1, 2, WorkFailureCode.HANDLER_REJECTED, "retry requested");
        retry.makeEligible(2);
        retry.start(2);
        retry.complete(2, WorkPayload.empty());
        assertEquals(2, retry.attemptCount());

        SimulationWorkRuntime failed = running(work("test:failed_runtime", RetryPolicy.never(), 1));
        failed.fail(1, WorkFailureCode.HANDLER_REJECTED, "failed");
        assertEquals(SimulationWorkStatus.FAILED, failed.status());

        SimulationWorkRuntime deferred = running(work("test:deferred_runtime", RetryPolicy.never(), 2));
        deferred.defer(1, 3, "deferred");
        deferred.makeEligible(3);
        assertEquals(SimulationWorkStatus.ELIGIBLE, deferred.status());
    }

    @Test
    void cancellationExpirationBackwardTicksAndInvalidTransitionsAreRejected() {
        ScheduledSimulationWork work = work("test:cancel_runtime", RetryPolicy.never(), 1);
        SimulationWorkRuntime cancelled = SimulationWorkRuntime.scheduled(work);
        cancelled.cancel(0, "cancelled");
        assertThrows(IllegalStateException.class, () -> cancelled.makeEligible(1));

        SimulationWorkRuntime expired = SimulationWorkRuntime.scheduled(
                work("test:expire_runtime", RetryPolicy.never(), 1));
        expired.expire(2);
        assertEquals(WorkFailureCode.WORK_EXPIRED, expired.lastFailureCode().orElseThrow());

        SimulationWorkRuntime backward = SimulationWorkRuntime.scheduled(
                work("test:backward_runtime", RetryPolicy.never(), 1));
        backward.makeEligible(2);
        assertThrows(IllegalStateException.class, () -> backward.start(1));
        assertThrows(IllegalStateException.class, () -> backward.complete(2, WorkPayload.empty()));
    }

    @Test
    void persistedRunningAndAttemptOverflowStatesAreRejected() {
        ScheduledSimulationWork work = work("test:running_persisted", RetryPolicy.never(), 1);
        SimulationWorkRuntime running = new SimulationWorkRuntime(
                work.id(), SimulationWorkStatus.RUNNING, 1, 1, OptionalLong.of(1), OptionalLong.empty(),
                OptionalLong.empty(), Optional.empty(), Optional.empty(), WorkPayload.empty(), 2,
                SchedulerSchema.CURRENT_VERSION
        );
        assertThrows(IllegalArgumentException.class, () -> running.validateAgainst(work));

        SimulationWorkRuntime tooManyAttempts = new SimulationWorkRuntime(
                work.id(), SimulationWorkStatus.FAILED, 2, 1, OptionalLong.of(1), OptionalLong.of(1),
                OptionalLong.empty(), Optional.of(WorkFailureCode.RETRY_LIMIT_REACHED), Optional.of("failed"),
                WorkPayload.empty(), 2, SchedulerSchema.CURRENT_VERSION
        );
        assertThrows(IllegalArgumentException.class, () -> tooManyAttempts.validateAgainst(work));
        assertFalse(tooManyAttempts.nextEligibleTick().isPresent());
    }

    private static ScheduledSimulationWork work(String id, RetryPolicy policy, int maximumAttempts) {
        return ScheduledSimulationWork.fromRequest(SchedulerTestFixtures.request(
                id, BuiltInSimulationStages.EXECUTION, 0, 1, WorkPriority.NORMAL, policy, maximumAttempts
        ), 0, 0);
    }

    private static SimulationWorkRuntime running(ScheduledSimulationWork work) {
        SimulationWorkRuntime runtime = SimulationWorkRuntime.scheduled(work);
        runtime.makeEligible(1);
        runtime.start(1);
        return runtime;
    }
}
