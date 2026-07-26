package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionSchedulerIntegrationTest {
    @Test
    void schedulerCompletesOnlyAfterExecutionPublishesOwnerResultEvidence() {
        ExecutionManager execution = new ExecutionManager(
                ExecutionTestFixtures.registry(ExecutionTestFixtures::ownerResult),
                ExecutionTestFixtures.CONFIGURATION
        );
        ExecutionOperationSnapshot operation = execution.acceptAuthorization(
                ExecutionTestFixtures.authorization("test:work/scheduler", "test:frozen/scheduler", 0),
                0
        ).value().orElseThrow();
        SimulationWorkRequest request = execution.prepareSchedulerWork(operation.operationId(), 0, 1)
                .value().orElseThrow();
        SimulationSchedulerManager scheduler = scheduler(execution);

        assertTrue(scheduler.submit(request, 0).accepted());
        new SimulationPipeline(scheduler, SimulationExecutionBudget.standard()).execute(1);

        ExecutionOperationSnapshot completed = execution.find(operation.operationId()).orElseThrow();
        assertEquals(ExecutionStatus.SUCCEEDED, completed.status());
        assertTrue(completed.ownerResultEvidence().isPresent());
        assertTrue(completed.resultEvidence().isPresent());

        var runtime = scheduler.runtimeFor(request.id()).orElseThrow();
        assertEquals(SimulationWorkStatus.COMPLETED, runtime.status());
        assertEquals("butchercraft:execution", runtime.effectOwnerSubsystemId().orElseThrow());
        assertEquals(completed.resultEvidence().orElseThrow().evidenceIdentity(),
                runtime.ownerResultIdentity().orElseThrow());
        assertEquals(completed.resultEvidence().orElseThrow().resultContentDigest(),
                runtime.effectContentDigest().orElseThrow());
    }

    @Test
    void cancellationIsAcceptedOnlyBeforeInvocationStarts() {
        ExecutionManager execution = new ExecutionManager(
                ExecutionTestFixtures.registry(ExecutionTestFixtures::ownerResult),
                ExecutionTestFixtures.CONFIGURATION
        );
        ExecutionOperationSnapshot operation = execution.acceptAuthorization(
                ExecutionTestFixtures.authorization("test:work/cancel", "test:frozen/cancel", 0),
                0
        ).value().orElseThrow();
        ExecutionOperationResult<ExecutionOperationSnapshot> cancelled =
                execution.cancelBeforeStart(operation.operationId(), 1, "test cancellation");

        assertTrue(cancelled.accepted());
        assertEquals(ExecutionStatus.CANCELLED_BEFORE_START, cancelled.value().orElseThrow().status());

        ExecutionOperationSnapshot started = startAndComplete(
                execution,
                "test:work/no_cancel",
                "test:frozen/no_cancel"
        );
        ExecutionOperationResult<ExecutionOperationSnapshot> rejected =
                execution.cancelBeforeStart(started.operationId(), 2, "too late");

        assertFalse(rejected.accepted());
        assertEquals(ExecutionFailureCode.CANCEL_UNSUPPORTED_AFTER_START,
                rejected.failureCode().orElseThrow());
    }

    private static ExecutionOperationSnapshot startAndComplete(
            ExecutionManager execution,
            String workId,
            String frozenInputId
    ) {
        ExecutionOperationSnapshot operation = execution.acceptAuthorization(
                ExecutionTestFixtures.authorization(workId, frozenInputId, 0),
                0
        ).value().orElseThrow();
        SimulationWorkRequest request = execution.prepareSchedulerWork(operation.operationId(), 0, 1)
                .value().orElseThrow();
        SimulationSchedulerManager scheduler = scheduler(execution);
        assertTrue(scheduler.submit(request, 0).accepted());
        new SimulationPipeline(scheduler, SimulationExecutionBudget.standard()).execute(1);
        return execution.find(operation.operationId()).orElseThrow();
    }

    private static SimulationSchedulerManager scheduler(ExecutionManager execution) {
        return new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(new GenericExecutionWorkHandler(() -> execution))),
                0
        );
    }
}
