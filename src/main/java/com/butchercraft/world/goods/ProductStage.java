package com.butchercraft.world.goods;

import java.util.Arrays;

public enum ProductStage {
    RAW("raw"),
    INTERMEDIATE("intermediate"),
    FINISHED("finished"),
    PACKAGED("packaged"),
    CONSUMABLE("consumable"),
    RECYCLABLE("recyclable"),
    WASTE("waste");

    private final String serializedName;

    ProductStage(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ProductStage fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown product stage: " + serializedName));
    }
}
