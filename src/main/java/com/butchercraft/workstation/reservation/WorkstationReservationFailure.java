package com.butchercraft.workstation.reservation;

import java.util.Objects;

public record WorkstationReservationFailure(
        WorkstationReservationFailureCode code,
        String detail
) {
    public WorkstationReservationFailure {
        code = Objects.requireNonNull(code, "code");
        detail = WorkstationReservationValidation.requireText(detail, "workstation reservation failure detail", 1024);
    }
}
