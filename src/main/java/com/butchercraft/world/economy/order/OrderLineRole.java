package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum OrderLineRole {
    REQUESTED("requested"), OFFERED("offered"), INPUT("input"), OUTPUT("output"), SERVICE("service"),
    RETURN("return"), OTHER("other");

    private final String serializedName;

    OrderLineRole(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OrderLineRole fromSerializedName(String value) {
        return Arrays.stream(values()).filter(role -> role.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown order line role: " + value));
    }
}
