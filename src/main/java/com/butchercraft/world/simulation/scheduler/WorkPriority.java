package com.butchercraft.world.simulation.scheduler;

import java.util.Arrays;

public enum WorkPriority {
    LOW("low", 0), NORMAL("normal", 1), HIGH("high", 2), URGENT("urgent", 3), CRITICAL("critical", 4);
    private final String serializedName;
    private final int rank;
    WorkPriority(String serializedName, int rank) { this.serializedName = serializedName; this.rank = rank; }
    public String serializedName() { return serializedName; }
    public int rank() { return rank; }
    public static WorkPriority fromSerializedName(String value) {
        return Arrays.stream(values()).filter(priority -> priority.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown work priority: " + value));
    }
}
