package com.butchercraft.workstation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationExecutionDispatchResult(
        boolean accepted,
        Optional<WorkstationFailure> failure
) {
    public WorkstationExecutionDispatchResult {
        failure = Objects.requireNonNull(failure, "failure");
        if (accepted == failure.isPresent()) {
            throw new IllegalArgumentException("Workstation Execution dispatch result is inconsistent");
        }
    }

    public static WorkstationExecutionDispatchResult acceptedResult() {
        return new WorkstationExecutionDispatchResult(true, Optional.empty());
    }

    public static WorkstationExecutionDispatchResult rejected(WorkstationFailure failure) {
        return new WorkstationExecutionDispatchResult(
                false,
                Optional.of(Objects.requireNonNull(failure, "failure"))
        );
    }
}
