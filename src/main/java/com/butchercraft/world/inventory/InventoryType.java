package com.butchercraft.world.inventory;

import java.util.Arrays;

public enum InventoryType {
    WAREHOUSE("warehouse"),
    PROCESSING("processing"),
    RETAIL("retail"),
    TRANSPORT("transport"),
    UTILITY("utility"),
    TEMPORARY("temporary"),
    PLAYER("player"),
    OTHER("other");

    private final String serializedName;

    InventoryType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static InventoryType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown inventory type: " + serializedName));
    }
}
