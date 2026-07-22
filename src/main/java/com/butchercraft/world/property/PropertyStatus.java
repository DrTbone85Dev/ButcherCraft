package com.butchercraft.world.property;

import java.util.Arrays;

public enum PropertyStatus {
    OPERATING("operating"),
    VACANT("vacant"),
    ABANDONED("abandoned"),
    CONDEMNED("condemned"),
    UNDER_RENOVATION("under_renovation"),
    RESERVED("reserved");

    private final String serializedName;

    PropertyStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PropertyStatus fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(status -> status.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown property status: " + serializedName));
    }
}
