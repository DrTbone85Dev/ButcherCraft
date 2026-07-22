package com.butchercraft.world.trade;

import java.util.Arrays;

public enum SupplyRelationshipType {
    PRIMARY_SUPPLIER("primary_supplier"),
    SECONDARY_SUPPLIER("secondary_supplier"),
    REGIONAL_DISTRIBUTOR("regional_distributor"),
    EQUIPMENT_SUPPLIER("equipment_supplier"),
    INGREDIENT_SUPPLIER("ingredient_supplier"),
    PACKAGING_SUPPLIER("packaging_supplier"),
    COOPERATIVE_PARTNER("cooperative_partner"),
    WHOLESALE_NETWORK("wholesale_network");

    private final String serializedName;

    SupplyRelationshipType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static SupplyRelationshipType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown supply relationship type: " + serializedName));
    }
}
