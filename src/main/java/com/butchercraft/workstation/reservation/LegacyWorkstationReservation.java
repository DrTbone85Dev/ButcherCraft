package com.butchercraft.workstation.reservation;

import java.util.Optional;
import java.util.OptionalLong;

public record LegacyWorkstationReservation(
        String workstationIdentity,
        String workstationType,
        String employeeIdentity,
        WorkstationReservationState state,
        long createdTick,
        OptionalLong expirationTick,
        Optional<String> invalidationReason,
        String dimensionIdentity,
        int workstationX,
        int workstationY,
        int workstationZ,
        int operatingX,
        int operatingY,
        int operatingZ,
        int anchorRadius
) {
}
