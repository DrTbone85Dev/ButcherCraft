package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum OrderPriority {
    LOW("low"), NORMAL("normal"), HIGH("high"), URGENT("urgent"), CRITICAL("critical");

    private final String serializedName;

    OrderPriority(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OrderPriority fromSerializedName(String value) {
        return Arrays.stream(values()).filter(priority -> priority.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order priority: " + value));
    }
}
