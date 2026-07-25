package com.butchercraft.workstation;

import com.butchercraft.world.execution.ExecutionOperationId;

import java.util.Objects;

public record WorkstationExecutionDispatchRequest(
        WorkstationTickContext tickContext,
        ExecutionOperationId operationId
) {
    public WorkstationExecutionDispatchRequest {
        tickContext = Objects.requireNonNull(tickContext, "tickContext");
        operationId = Objects.requireNonNull(operationId, "operationId");
    }
}
