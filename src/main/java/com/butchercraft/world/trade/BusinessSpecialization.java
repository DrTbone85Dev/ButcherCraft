package com.butchercraft.world.trade;

import java.util.Arrays;

public enum BusinessSpecialization {
    CUSTOM_PROCESSING("custom_processing"),
    RETAIL("retail"),
    WHOLESALE("wholesale"),
    DISTRIBUTION("distribution"),
    COLD_STORAGE("cold_storage"),
    SAUSAGE_PRODUCTION("sausage_production"),
    SMOKED_PRODUCTS("smoked_products"),
    DRY_AGING("dry_aging"),
    WILD_GAME_PROCESSING("wild_game_processing"),
    LIVESTOCK_PROCESSING("livestock_processing");

    private final String serializedName;

    BusinessSpecialization(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BusinessSpecialization fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(specialization -> specialization.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business specialization: " + serializedName));
    }
}
