package com.butchercraft.world.trade;

import java.util.Arrays;

public enum RelationshipStrength {
    EXCLUSIVE("exclusive"),
    PREFERRED("preferred"),
    ESTABLISHED("established"),
    OCCASIONAL("occasional"),
    HISTORICAL("historical"),
    FORMER("former");

    private final String serializedName;

    RelationshipStrength(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static RelationshipStrength fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(strength -> strength.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown relationship strength: " + serializedName));
    }
}
