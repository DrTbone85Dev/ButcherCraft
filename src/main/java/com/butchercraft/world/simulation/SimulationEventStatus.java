package com.butchercraft.world.simulation;

import java.util.Arrays;

public enum SimulationEventStatus {
    PENDING("pending"),
    EXECUTED("executed"),
    CANCELLED("cancelled");

    private final String serializedName;

    SimulationEventStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static SimulationEventStatus fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(status -> status.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown simulation event status: " + serializedName));
    }
}
