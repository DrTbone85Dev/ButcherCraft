package com.butchercraft.world.ownership;

import java.util.Arrays;

public enum OwnershipEntityType {
    INDIVIDUAL("individual"),
    FAMILY("family"),
    PARTNERSHIP("partnership"),
    COOPERATIVE("cooperative"),
    CORPORATION("corporation"),
    ESTATE("estate"),
    MUNICIPALITY("municipality");

    private final String serializedName;

    OwnershipEntityType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static OwnershipEntityType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown ownership entity type: " + serializedName));
    }
}
