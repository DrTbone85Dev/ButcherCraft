package com.butchercraft.world.goods;

import java.util.Arrays;

public enum StorageRequirement {
    AMBIENT("ambient"),
    REFRIGERATED("refrigerated"),
    FROZEN("frozen"),
    CLIMATE_CONTROLLED("climate_controlled"),
    HAZARDOUS("hazardous");

    private final String serializedName;

    StorageRequirement(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static StorageRequirement fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown storage requirement: " + serializedName));
    }
}
