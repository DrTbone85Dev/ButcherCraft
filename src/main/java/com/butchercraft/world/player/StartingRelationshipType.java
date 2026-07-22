package com.butchercraft.world.player;

import java.util.Arrays;

public enum StartingRelationshipType {
    FAMILY_TIE("family_tie"),
    SUPPLIER_FAMILIARITY("supplier_familiarity"),
    COMMUNITY_REPUTATION("community_reputation"),
    EXISTING_BUSINESS_CONTACT("existing_business_contact");

    private final String serializedName;

    StartingRelationshipType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static StartingRelationshipType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown starting relationship type: " + serializedName));
    }
}
