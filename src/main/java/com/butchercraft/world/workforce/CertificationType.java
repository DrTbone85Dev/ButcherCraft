package com.butchercraft.world.workforce;

import java.util.Arrays;

public enum CertificationType {
    NONE("none"),
    FOOD_SAFETY("food_safety"),
    HACCP("haccp"),
    USDA_INSPECTION("usda_inspection"),
    FORKLIFT("forklift"),
    EQUIPMENT_OPERATION("equipment_operation"),
    SANITATION("sanitation");

    private final String serializedName;

    CertificationType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CertificationType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown workforce certification type: " + serializedName));
    }
}
