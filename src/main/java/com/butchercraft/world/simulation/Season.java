package com.butchercraft.world.simulation;

import java.util.Arrays;

public enum Season {
    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn"),
    WINTER("winter");

    private final String serializedName;

    Season(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Season fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(season -> season.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown simulation season: " + serializedName));
    }
}
