package com.butchercraft.world.player;

import java.util.Arrays;

public enum StartingScenarioType {
    INHERITED_FAMILY_BUSINESS("inherited_family_business", true),
    VACANT_PROPERTY_PURCHASE("vacant_property_purchase", false),
    EXISTING_BUSINESS_MANAGER("existing_business_manager", false),
    STARTUP_OPERATION("startup_operation", false),
    COUNTY_CONTRACT("county_contract", false),
    COOPERATIVE_ASSIGNMENT("cooperative_assignment", false);

    private final String serializedName;
    private final boolean inheritanceRecordRequired;

    StartingScenarioType(String serializedName, boolean inheritanceRecordRequired) {
        this.serializedName = serializedName;
        this.inheritanceRecordRequired = inheritanceRecordRequired;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean inheritanceRecordRequired() {
        return inheritanceRecordRequired;
    }

    public static StartingScenarioType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown starting scenario type: " + serializedName));
    }
}
