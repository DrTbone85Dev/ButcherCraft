package com.butchercraft.world.player;

import java.util.Arrays;

public enum LegacyAcquisitionType {
    INHERITED("inherited"),
    PURCHASED("purchased"),
    APPOINTED_MANAGER("appointed_manager"),
    FOUNDED("founded"),
    COUNTY_ASSIGNED("county_assigned"),
    COOPERATIVE_ASSIGNED("cooperative_assigned");

    private final String serializedName;

    LegacyAcquisitionType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static LegacyAcquisitionType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown legacy acquisition type: " + serializedName));
    }
}
