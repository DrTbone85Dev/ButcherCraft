package com.butchercraft.workstation.reservation;

import java.util.Optional;

@FunctionalInterface
public interface WorkstationReservationMigrationResolver {
    Optional<WorkstationReservationMigrationEvidence> resolve(LegacyWorkstationReservation reservation);

    static WorkstationReservationMigrationResolver noProof() {
        return ignored -> Optional.empty();
    }
}
