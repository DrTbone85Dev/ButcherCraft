package com.butchercraft.world.goods;

import java.util.Arrays;

public enum TransportRequirement {
    STANDARD("standard"),
    REFRIGERATED("refrigerated"),
    FROZEN("frozen"),
    LIVESTOCK("livestock"),
    LIQUID("liquid"),
    BULK("bulk"),
    HAZARDOUS("hazardous");

    private final String serializedName;

    TransportRequirement(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static TransportRequirement fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown transport requirement: " + serializedName));
    }
}
