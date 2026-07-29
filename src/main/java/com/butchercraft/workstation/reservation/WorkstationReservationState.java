package com.butchercraft.workstation.reservation;

import java.util.Arrays;

public enum WorkstationReservationState {
    REQUESTED("requested", true),
    RESERVED("reserved", true),
    EMPLOYEE_EN_ROUTE("employee_en_route", true),
    EMPLOYEE_ARRIVED("employee_arrived", true),
    RELEASED("released", false),
    INVALIDATED("invalidated", false);

    private final String serializedName;
    private final boolean active;

    WorkstationReservationState(String serializedName, boolean active) {
        this.serializedName = serializedName;
        this.active = active;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean active() {
        return active;
    }

    public static WorkstationReservationState fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(state -> state.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown workstation reservation state: " + serializedName));
    }
}
