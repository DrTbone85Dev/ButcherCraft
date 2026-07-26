package com.butchercraft.workstation;

import com.butchercraft.world.execution.ExecutionOperationId;

import java.util.Optional;

public interface WorkstationExecutionCoordinator {
    WorkstationExecutionStartResult start(WorkstationExecutionStartRequest request);

    WorkstationExecutionDispatchResult dispatch(WorkstationExecutionDispatchRequest request);

    WorkstationExecutionCancelResult cancel(WorkstationExecutionCancelRequest request);

    Optional<WorkstationExecutionObservation> observe(ExecutionOperationId operationId, WorkstationTickContext context);
}
