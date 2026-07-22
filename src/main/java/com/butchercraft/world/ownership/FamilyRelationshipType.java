package com.butchercraft.world.ownership;

import java.util.Arrays;

public enum FamilyRelationshipType {
    ANCESTRAL_BRANCH("ancestral_branch"),
    SUCCESSION_LINE("succession_line"),
    BUSINESS_PARTNERSHIP("business_partnership"),
    FORMER_PARTNERSHIP("former_partnership"),
    HISTORICAL_RIVALRY("historical_rivalry");

    private final String serializedName;

    FamilyRelationshipType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static FamilyRelationshipType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown family relationship type: " + serializedName));
    }
}
