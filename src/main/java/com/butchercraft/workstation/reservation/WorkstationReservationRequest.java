package com.butchercraft.workstation.reservation;

import java.util.Objects;

public record WorkstationReservationRequest(
        String workstationIdentity,
        String workstationType,
        String employeeIdentity,
        long createdTick,
        String dimensionIdentity,
        int workstationX,
        int workstationY,
        int workstationZ,
        int operatingX,
        int operatingY,
        int operatingZ,
        int anchorRadius
) {
    public WorkstationReservationRequest {
        workstationIdentity = WorkstationReservationValidation.requireIdentity(
                workstationIdentity,
                "workstation identity"
        );
        workstationType = WorkstationReservationValidation.requireToken(workstationType, "workstation type");
        employeeIdentity = WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity");
        if (createdTick < 0L) {
            throw new IllegalArgumentException("Workstation reservation created tick must not be negative");
        }
        dimensionIdentity = WorkstationReservationValidation.requireIdentity(dimensionIdentity, "dimension identity");
        if (anchorRadius < 1 || anchorRadius > 16) {
            throw new IllegalArgumentException("Workstation reservation anchor radius must be 1-16: " + anchorRadius);
        }
        Objects.requireNonNull(workstationIdentity, "workstationIdentity");
    }
}
