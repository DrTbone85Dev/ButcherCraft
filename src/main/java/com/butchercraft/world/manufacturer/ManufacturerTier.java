package com.butchercraft.world.manufacturer;

import java.util.Arrays;

public enum ManufacturerTier {
    LOCAL("local"),
    REGIONAL("regional"),
    NATIONAL("national"),
    INDUSTRY_LEADER("industry_leader");

    private final String serializedName;

    ManufacturerTier(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ManufacturerTier fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(tier -> tier.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown manufacturer tier: " + serializedName));
    }
}
