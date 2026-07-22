package com.butchercraft.world.property;

import java.util.Arrays;

public enum CommercialPropertyType {
    FAMILY_BUTCHER_SHOP("family_butcher_shop"),
    VACANT_STOREFRONT("vacant_storefront"),
    LOCKER_PLANT("locker_plant"),
    WAREHOUSE("warehouse"),
    INDUSTRIAL_BUILDING("industrial_building"),
    EMPTY_COMMERCIAL_LOT("empty_commercial_lot"),
    DISTRIBUTION_CENTER("distribution_center"),
    COLD_STORAGE_FACILITY("cold_storage_facility");

    private final String serializedName;

    CommercialPropertyType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CommercialPropertyType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown commercial property type: " + serializedName));
    }
}
