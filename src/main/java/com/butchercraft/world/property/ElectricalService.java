package com.butchercraft.world.property;

import java.util.Arrays;

public enum ElectricalService {
    LIGHT_COMMERCIAL("light_commercial"),
    STANDARD_COMMERCIAL("standard_commercial"),
    HEAVY_COMMERCIAL("heavy_commercial"),
    INDUSTRIAL_THREE_PHASE("industrial_three_phase");

    private final String serializedName;

    ElectricalService(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ElectricalService fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(service -> service.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown electrical service: " + serializedName));
    }
}
