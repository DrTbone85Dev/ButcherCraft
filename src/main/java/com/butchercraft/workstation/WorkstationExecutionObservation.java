package com.butchercraft.workstation;

import com.butchercraft.world.execution.ExecutionStatus;

import java.util.Objects;
import java.util.Optional;

public record WorkstationExecutionObservation(
        ExecutionStatus status,
        Optional<WorkstationFailure> terminalFailure
) {
    public WorkstationExecutionObservation {
        status = Objects.requireNonNull(status, "status");
        terminalFailure = Objects.requireNonNull(terminalFailure, "terminalFailure");
        if (status.terminal() && status != ExecutionStatus.SUCCEEDED && terminalFailure.isEmpty()) {
            throw new IllegalArgumentException("Unsuccessful terminal Execution observation requires a failure");
        }
    }
}
