package com.butchercraft.workstation.reservation;

import java.util.Arrays;

public enum WorkstationReservationEndpointPurpose {
    NONE("none"),
    SOURCE("source"),
    DESTINATION("destination"),
    SOURCE_RETURN("source_return");

    private final String serializedName;

    WorkstationReservationEndpointPurpose(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorkstationReservationEndpointPurpose fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(purpose -> purpose.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown workstation reservation endpoint purpose: " + serializedName));
    }
}
