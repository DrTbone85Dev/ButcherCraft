package com.butchercraft.world.goods;

import java.util.Arrays;

public enum Stackability {
    STACKABLE("stackable"),
    NON_STACKABLE("non_stackable");

    private final String serializedName;

    Stackability(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static Stackability fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown stackability: " + serializedName));
    }
}
