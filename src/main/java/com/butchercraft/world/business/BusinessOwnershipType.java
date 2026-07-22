package com.butchercraft.world.business;

import java.util.Arrays;

public enum BusinessOwnershipType {
    FAMILY("family"),
    INDEPENDENT_OPERATOR("independent_operator"),
    COOPERATIVE("cooperative"),
    REGIONAL_COMPANY("regional_company"),
    ESTATE("estate"),
    BANK_MANAGED("bank_managed");

    private final String serializedName;

    BusinessOwnershipType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BusinessOwnershipType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business ownership type: " + serializedName));
    }
}
