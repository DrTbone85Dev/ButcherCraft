package com.butchercraft.world.manufacturer;

import java.util.Arrays;

public enum ManufacturerCategory {
    PROCESSING_EQUIPMENT("processing_equipment"),
    REFRIGERATION("refrigeration"),
    PACKAGING("packaging"),
    SCALES("scales"),
    KNIVES_AND_CUTLERY("knives_and_cutlery"),
    SANITATION("sanitation"),
    MATERIAL_HANDLING("material_handling"),
    SMALLWARES("smallwares"),
    SAFETY_EQUIPMENT("safety_equipment"),
    SPECIALTY_EQUIPMENT("specialty_equipment"),
    UTILITIES("utilities");

    private final String serializedName;

    ManufacturerCategory(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ManufacturerCategory fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(category -> category.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown manufacturer category: " + serializedName));
    }
}
