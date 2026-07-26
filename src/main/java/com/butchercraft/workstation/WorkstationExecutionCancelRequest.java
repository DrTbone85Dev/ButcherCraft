package com.butchercraft.workstation;

import com.butchercraft.world.execution.ExecutionOperationId;

import java.util.Objects;

public record WorkstationExecutionCancelRequest(
        WorkstationTickContext tickContext,
        ExecutionOperationId operationId,
        String reason
) {
    public WorkstationExecutionCancelRequest {
        tickContext = Objects.requireNonNull(tickContext, "tickContext");
        operationId = Objects.requireNonNull(operationId, "operationId");
        reason = Objects.requireNonNull(reason, "reason").strip();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("Workstation Execution cancellation reason cannot be blank");
        }
    }
}
