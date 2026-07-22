package com.butchercraft.world.goods;

import java.util.Arrays;

public enum EconomicFlag {
    TRADEABLE("tradeable"),
    CONSUMABLE("consumable"),
    PERISHABLE("perishable"),
    REGULATED("regulated"),
    HAZARDOUS("hazardous"),
    CAPACITY("capacity");

    private final String serializedName;

    EconomicFlag(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static EconomicFlag fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown economic flag: " + serializedName));
    }
}
