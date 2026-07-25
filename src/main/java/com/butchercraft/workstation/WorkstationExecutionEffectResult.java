package com.butchercraft.workstation;

import com.butchercraft.world.execution.ExecutionOwnerResultEvidence;

import java.util.Objects;
import java.util.Optional;

public record WorkstationExecutionEffectResult(
        boolean accepted,
        Optional<ExecutionOwnerResultEvidence> ownerResultEvidence,
        Optional<WorkstationFailure> failure
) {
    public WorkstationExecutionEffectResult {
        ownerResultEvidence = Objects.requireNonNull(ownerResultEvidence, "ownerResultEvidence");
        failure = Objects.requireNonNull(failure, "failure");
        if (accepted && (ownerResultEvidence.isEmpty() || failure.isPresent())) {
            throw new IllegalArgumentException("Accepted Workstation Execution effect result is incomplete");
        }
        if (!accepted && failure.isEmpty()) {
            throw new IllegalArgumentException("Rejected Workstation Execution effect result requires a failure");
        }
    }

    public static WorkstationExecutionEffectResult accepted(ExecutionOwnerResultEvidence ownerResultEvidence) {
        return new WorkstationExecutionEffectResult(
                true,
                Optional.of(Objects.requireNonNull(ownerResultEvidence, "ownerResultEvidence")),
                Optional.empty()
        );
    }

    public static WorkstationExecutionEffectResult rejected(WorkstationFailure failure) {
        return new WorkstationExecutionEffectResult(
                false,
                Optional.empty(),
                Optional.of(Objects.requireNonNull(failure, "failure"))
        );
    }
}
