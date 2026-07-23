package com.butchercraft.world.goods;

import java.util.Arrays;

public enum UnitOfMeasure {
    EACH("each"),
    POUND("pound"),
    KILOGRAM("kilogram"),
    LITER("liter"),
    GALLON("gallon"),
    BUSHEL("bushel"),
    TON("ton"),
    KILOWATT_HOUR("kilowatt_hour"),
    HEAD("head"),
    PALLET("pallet"),
    CRATE("crate"),
    BOX("box");

    private final String serializedName;

    UnitOfMeasure(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static UnitOfMeasure fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown unit of measure: " + serializedName));
    }
}
