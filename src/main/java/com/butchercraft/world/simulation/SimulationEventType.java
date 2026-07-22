package com.butchercraft.world.simulation;

import java.util.Arrays;

public enum SimulationEventType {
    DAILY_ROLLOVER("daily_rollover", 10),
    WEEKLY_ROLLOVER("weekly_rollover", 20),
    MONTHLY_ROLLOVER("monthly_rollover", 30),
    YEARLY_ROLLOVER("yearly_rollover", 40);

    private final String serializedName;
    private final int executionPriority;

    SimulationEventType(String serializedName, int executionPriority) {
        this.serializedName = serializedName;
        this.executionPriority = executionPriority;
    }

    public String serializedName() {
        return serializedName;
    }

    public int executionPriority() {
        return executionPriority;
    }

    public static SimulationEventType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown simulation event type: " + serializedName));
    }
}
