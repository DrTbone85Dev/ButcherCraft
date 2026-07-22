package com.butchercraft.world.property;

import java.util.Arrays;

public enum PropertyAcquisitionMethod {
    ORIGINAL_CONSTRUCTION("original_construction"),
    FAMILY_PURCHASE("family_purchase"),
    REGIONAL_COMPANY_PURCHASE("regional_company_purchase"),
    ESTATE_TRANSFER("estate_transfer"),
    BANK_REPOSSESSION("bank_repossession"),
    COUNTY_AUCTION("county_auction"),
    PRIVATE_SALE("private_sale"),
    DEVELOPMENT_HOLD("development_hold");

    private final String serializedName;

    PropertyAcquisitionMethod(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static PropertyAcquisitionMethod fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(method -> method.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown property acquisition method: " + serializedName));
    }
}
