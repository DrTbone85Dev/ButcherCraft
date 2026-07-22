package com.butchercraft.world.manufacturer;

import java.util.Arrays;

public enum EngineeringPhilosophy {
    BUILT_TO_LAST("built_to_last"),
    INNOVATION_FIRST("innovation_first"),
    BUDGET_VALUE("budget_value"),
    PRECISION_ENGINEERING("precision_engineering"),
    INDUSTRIAL_SIMPLICITY("industrial_simplicity"),
    FAMILY_CRAFTSMANSHIP("family_craftsmanship"),
    HIGH_THROUGHPUT("high_throughput"),
    ENERGY_EFFICIENCY("energy_efficiency"),
    SERVICEABILITY("serviceability");

    private final String serializedName;

    EngineeringPhilosophy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static EngineeringPhilosophy fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(philosophy -> philosophy.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown engineering philosophy: " + serializedName));
    }
}
