package com.butchercraft.world.goods;

import java.util.Arrays;

public enum CommodityType {
    LIVESTOCK("livestock"),
    AGRICULTURAL("agricultural"),
    ENERGY("energy"),
    UTILITY("utility"),
    FUEL("fuel"),
    TRANSPORT("transport"),
    LABOR("labor"),
    RAW_MATERIAL("raw_material"),
    WATER("water"),
    OTHER("other");

    private final String serializedName;

    CommodityType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CommodityType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown commodity type: " + serializedName));
    }
}
