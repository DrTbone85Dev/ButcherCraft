package com.butchercraft.world.property;

import java.util.Arrays;

public enum RefrigerationCapacity {
    NONE("none"),
    SMALL_RETAIL("small_retail"),
    LOCKER_ROOM("locker_room"),
    WAREHOUSE("warehouse"),
    INDUSTRIAL("industrial");

    private final String serializedName;

    RefrigerationCapacity(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static RefrigerationCapacity fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(capacity -> capacity.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown refrigeration capacity: " + serializedName));
    }
}
