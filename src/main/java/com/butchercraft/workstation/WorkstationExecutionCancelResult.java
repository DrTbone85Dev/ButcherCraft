package com.butchercraft.workstation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationExecutionCancelResult(
        boolean accepted,
        Optional<WorkstationFailure> failure
) {
    public WorkstationExecutionCancelResult {
        failure = Objects.requireNonNull(failure, "failure");
        if (accepted == failure.isPresent()) {
            throw new IllegalArgumentException("Workstation Execution cancellation result is inconsistent");
        }
    }

    public static WorkstationExecutionCancelResult acceptedResult() {
        return new WorkstationExecutionCancelResult(true, Optional.empty());
    }

    public static WorkstationExecutionCancelResult rejected(WorkstationFailure failure) {
        return new WorkstationExecutionCancelResult(
                false,
                Optional.of(Objects.requireNonNull(failure, "failure"))
        );
    }
}
