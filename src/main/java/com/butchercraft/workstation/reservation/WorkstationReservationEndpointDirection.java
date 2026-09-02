package com.butchercraft.workstation.reservation;

import java.util.Arrays;

public enum WorkstationReservationEndpointDirection {
    NONE("none"),
    WITHDRAWAL("withdrawal"),
    DEPOSIT("deposit");

    private final String serializedName;

    WorkstationReservationEndpointDirection(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorkstationReservationEndpointDirection fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(direction -> direction.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown workstation reservation endpoint direction: " + serializedName));
    }
}
