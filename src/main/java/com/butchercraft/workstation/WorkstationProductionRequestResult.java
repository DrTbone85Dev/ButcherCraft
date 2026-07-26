package com.butchercraft.workstation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationProductionRequestResult(
        boolean accepted,
        WorkstationProductionSnapshot snapshot,
        Optional<WorkstationFailure> failure
) {
    public WorkstationProductionRequestResult {
        snapshot = Objects.requireNonNull(snapshot, "snapshot");
        failure = Objects.requireNonNull(failure, "failure");
        if (accepted == failure.isPresent()) {
            throw new IllegalArgumentException("Workstation production request result shape is inconsistent");
        }
    }

    public static WorkstationProductionRequestResult accepted(WorkstationProductionSnapshot snapshot) {
        return new WorkstationProductionRequestResult(
                true,
                Objects.requireNonNull(snapshot, "snapshot"),
                Optional.empty()
        );
    }

    public static WorkstationProductionRequestResult rejected(
            WorkstationProductionSnapshot snapshot,
            WorkstationFailure failure
    ) {
        return new WorkstationProductionRequestResult(
                false,
                Objects.requireNonNull(snapshot, "snapshot"),
                Optional.of(Objects.requireNonNull(failure, "failure"))
        );
    }
}
