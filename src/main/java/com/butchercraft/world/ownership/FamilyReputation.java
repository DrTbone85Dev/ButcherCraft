package com.butchercraft.world.ownership;

import java.util.Arrays;

public enum FamilyReputation {
    LEGENDARY("legendary"),
    RESPECTED("respected"),
    ESTABLISHED("established"),
    ORDINARY("ordinary"),
    DECLINING("declining"),
    DISGRACED("disgraced");

    private final String serializedName;

    FamilyReputation(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static FamilyReputation fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown family reputation: " + serializedName));
    }
}
