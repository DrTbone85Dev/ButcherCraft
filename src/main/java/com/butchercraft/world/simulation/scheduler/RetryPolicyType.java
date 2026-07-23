package com.butchercraft.world.simulation.scheduler;

import java.util.Arrays;

public enum RetryPolicyType {
    NEVER("never"), NEXT_TICK("next_tick"), FIXED_INTERVAL("fixed_interval"),
    EXPONENTIAL_SIMULATION_INTERVAL("exponential_simulation_interval"), HANDLER_REQUESTED("handler_requested");
    private final String serializedName;
    RetryPolicyType(String serializedName) { this.serializedName = serializedName; }
    public String serializedName() { return serializedName; }
    public static RetryPolicyType fromSerializedName(String value) {
        return Arrays.stream(values()).filter(type -> type.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown retry policy type: " + value));
    }
}
