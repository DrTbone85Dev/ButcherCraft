package com.butchercraft.workstation.reservation;

import java.util.Arrays;

public enum WorkstationReservationConflictDomain {
    WORKSTATION_INSTANCE("workstation_instance");

    private final String serializedName;

    WorkstationReservationConflictDomain(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorkstationReservationConflictDomain fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(domain -> domain.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown workstation reservation conflict domain: " + serializedName));
    }
}
