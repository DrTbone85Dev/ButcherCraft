package com.butchercraft.world.property;

import java.util.Arrays;

public enum PropertyCondition {
    EXCELLENT("excellent"),
    GOOD("good"),
    FAIR("fair"),
    POOR("poor"),
    DERELICT("derelict");

    private final String serializedName;

    PropertyCondition(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PropertyCondition fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(condition -> condition.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown property condition: " + serializedName));
    }
}
