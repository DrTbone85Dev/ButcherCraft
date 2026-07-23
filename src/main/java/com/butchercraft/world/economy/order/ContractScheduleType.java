package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum ContractScheduleType {
    ONE_TIME("one_time"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly"), SEASONAL("seasonal"),
    INTERVAL("interval"), ON_DEMAND("on_demand");

    private final String serializedName;

    ContractScheduleType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ContractScheduleType fromSerializedName(String value) {
        return Arrays.stream(values()).filter(type -> type.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown contract schedule type: " + value));
    }
}
