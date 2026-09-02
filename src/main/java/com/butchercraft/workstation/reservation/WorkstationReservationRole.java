package com.butchercraft.workstation.reservation;

import java.util.Arrays;

public enum WorkstationReservationRole {
    MACHINE_OPERATOR("machine_operator"),
    MATERIAL_HANDLER("material_handler"),
    LEGACY_EXCLUSIVE("legacy_exclusive");

    private final String serializedName;

    WorkstationReservationRole(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorkstationReservationRole fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(role -> role.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown workstation reservation role: " + serializedName));
    }
}
