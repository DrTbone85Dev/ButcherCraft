package com.butchercraft.world.business;

import java.util.Arrays;

public enum BusinessRelationshipType {
    PREDECESSOR("predecessor"),
    SUCCESSOR("successor"),
    MERGED_INTO("merged_into"),
    FORMER_AFFILIATE("former_affiliate"),
    SHARED_FAMILY_HISTORY("shared_family_history");

    private final String serializedName;

    BusinessRelationshipType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BusinessRelationshipType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business relationship type: " + serializedName));
    }
}
