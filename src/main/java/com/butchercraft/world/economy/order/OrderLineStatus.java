package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum OrderLineStatus {
    OPEN("open"), PARTIALLY_FULFILLED("partially_fulfilled"), FULFILLED("fulfilled"),
    CANCELLED("cancelled"), FAILED("failed");

    private final String serializedName;

    OrderLineStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean isTerminal() {
        return this == FULFILLED || this == CANCELLED || this == FAILED;
    }

    public static OrderLineStatus fromSerializedName(String value) {
        return Arrays.stream(values()).filter(status -> status.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order line status: " + value));
    }
}
