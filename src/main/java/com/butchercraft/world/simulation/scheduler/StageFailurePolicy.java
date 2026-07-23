package com.butchercraft.world.simulation.scheduler;

import java.util.Arrays;

public enum StageFailurePolicy {
    CONTINUE_STAGE("continue_stage"), STOP_STAGE("stop_stage"), STOP_TICK("stop_tick"),
    FAIL_PIPELINE("fail_pipeline");

    private final String serializedName;
    StageFailurePolicy(String serializedName) { this.serializedName = serializedName; }
    public String serializedName() { return serializedName; }
    public static StageFailurePolicy fromSerializedName(String value) {
        return Arrays.stream(values()).filter(policy -> policy.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stage failure policy: " + value));
    }
}
