package com.butchercraft.world.goods;

import java.util.Arrays;

public enum GoodCategory {
    COMMODITY("commodity"),
    PRODUCT("product");

    private final String serializedName;

    GoodCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static GoodCategory fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown good category: " + serializedName));
    }
}
