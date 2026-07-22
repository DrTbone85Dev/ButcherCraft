package com.butchercraft.world.identity;

import java.util.Arrays;

public enum SettlementType {
    HAMLET("hamlet"),
    VILLAGE("village"),
    TOWN("town"),
    REGIONAL_CITY("regional_city");

    private final String serializedName;

    SettlementType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static SettlementType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown settlement type: " + serializedName));
    }
}
