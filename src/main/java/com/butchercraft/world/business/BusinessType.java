package com.butchercraft.world.business;

import java.util.Arrays;

public enum BusinessType {
    FAMILY_BUTCHER_SHOP("family_butcher_shop"),
    RETAIL_MEAT_MARKET("retail_meat_market"),
    CUSTOM_PROCESSOR("custom_processor"),
    REGIONAL_PROCESSING_COMPANY("regional_processing_company"),
    LOCKER_PLANT("locker_plant"),
    COLD_STORAGE_COMPANY("cold_storage_company"),
    FOOD_DISTRIBUTION_COMPANY("food_distribution_company"),
    WHOLESALE_SUPPLIER("wholesale_supplier");

    private final String serializedName;

    BusinessType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BusinessType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business type: " + serializedName));
    }
}
