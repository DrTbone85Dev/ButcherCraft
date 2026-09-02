package com.butchercraft.workstation.reservation;

import java.util.Objects;
import java.util.Optional;

public record WorkstationReservationCompatibility(
        WorkstationReservationCompatibilityDecision decision,
        Optional<WorkstationReservationRecord> existingReservation,
        Optional<WorkstationReservationFailure> failure
) {
    public WorkstationReservationCompatibility {
        decision = Objects.requireNonNull(decision, "decision");
        existingReservation = Objects.requireNonNull(existingReservation, "existingReservation");
        failure = Objects.requireNonNull(failure, "failure");
        if (decision == WorkstationReservationCompatibilityDecision.DUPLICATE && existingReservation.isEmpty()) {
            throw new IllegalArgumentException("Duplicate compatibility requires the existing reservation");
        }
        if ((decision == WorkstationReservationCompatibilityDecision.REJECTED) != failure.isPresent()) {
            throw new IllegalArgumentException("Only rejected compatibility contains a failure");
        }
    }

    public static WorkstationReservationCompatibility allowed() {
        return new WorkstationReservationCompatibility(
                WorkstationReservationCompatibilityDecision.ALLOWED, Optional.empty(), Optional.empty());
    }

    public static WorkstationReservationCompatibility duplicate(WorkstationReservationRecord existing) {
        return new WorkstationReservationCompatibility(
                WorkstationReservationCompatibilityDecision.DUPLICATE,
                Optional.of(existing),
                Optional.empty()
        );
    }

    public static WorkstationReservationCompatibility rejected(
            WorkstationReservationFailureCode code,
            String detail
    ) {
        return new WorkstationReservationCompatibility(
                WorkstationReservationCompatibilityDecision.REJECTED,
                Optional.empty(),
                Optional.of(new WorkstationReservationFailure(code, detail))
        );
    }
}
