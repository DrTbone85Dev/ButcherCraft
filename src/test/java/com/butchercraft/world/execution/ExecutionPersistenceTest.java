package com.butchercraft.world.execution;

import com.butchercraft.world.execution.persistence.ExecutionStorage;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistenceRoundTripRetainsTerminalEvidenceWithoutRuntimeAuthorization() {
        ExecutionManager execution = new ExecutionManager(
                ExecutionTestFixtures.registry(ExecutionTestFixtures::ownerResult),
                ExecutionTestFixtures.CONFIGURATION
        );
        ExecutionOperationSnapshot operation = execute(execution, "test:work/persisted", "test:frozen/persisted");
        ExecutionStorage storage = storage();

        String serialized = storage.serialize(execution);
        ExecutionManager loaded = storage.deserialize(serialized);
        ExecutionOperationSnapshot restored = loaded.find(operation.operationId()).orElseThrow();

        assertEquals(ExecutionStatus.SUCCEEDED, restored.status());
        assertEquals(operation.resultEvidence().orElseThrow(), restored.resultEvidence().orElseThrow());
        assertFalse(serialized.contains("consumed"));
    }

    @Test
    void unresolvedOwnerResultIsRecoveredAsUnknownOutcomeWithoutReusingRuntimeAuthority() {
        ExecutionManager execution = new ExecutionManager(
                ExecutionTestFixtures.registry(context ->
                        ExecutionHandlerResult.waiting(context.authoritativeSimulationTick() + 5L, "owner pending", 2)),
                ExecutionTestFixtures.CONFIGURATION
        );
        ExecutionOperationSnapshot operation = acceptAndSchedule(execution, "test:work/waiting", "test:frozen/waiting");
        ExecutionStorage storage = storage();

        String serialized = storage.serialize(execution);
        ExecutionManager loaded = storage.deserialize(serialized);
        ExecutionOperationSnapshot restored = loaded.find(operation.operationId()).orElseThrow();

        assertEquals(ExecutionStatus.AWAITING_OWNER_RESULT,
                execution.find(operation.operationId()).orElseThrow().status());
        assertEquals(ExecutionStatus.UNKNOWN_OUTCOME, restored.status());
        assertEquals(ExecutionFailureCode.HANDLER_EXCEPTION_UNKNOWN_OUTCOME,
                restored.failure().orElseThrow().code());
        assertTrue(restored.resultEvidence().isEmpty());
    }

    private ExecutionOperationSnapshot execute(
            ExecutionManager execution,
            String workId,
            String frozenInputId
    ) {
        acceptAndSchedule(execution, workId, frozenInputId);
        return execution.operations().getFirst();
    }

    private ExecutionOperationSnapshot acceptAndSchedule(
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
        SimulationSchedulerManager scheduler = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(new GenericExecutionWorkHandler(() -> execution))),
                0
        );
        assertTrue(scheduler.submit(request, 0).accepted());
        new SimulationPipeline(scheduler, SimulationExecutionBudget.standard()).execute(1);
        assertEquals(
                execution.find(operation.operationId()).orElseThrow().status() == ExecutionStatus.AWAITING_OWNER_RESULT
                        ? SimulationWorkStatus.DEFERRED
                        : SimulationWorkStatus.COMPLETED,
                scheduler.runtimeFor(request.id()).orElseThrow().status()
        );
        return operation;
    }

    private ExecutionStorage storage() {
        return new ExecutionStorage(
                temporaryDirectory.resolve(ExecutionSchema.FILE_NAME),
                ExecutionTestFixtures.registry(ExecutionTestFixtures::ownerResult),
                ExecutionTestFixtures.CONFIGURATION
        );
    }
}
