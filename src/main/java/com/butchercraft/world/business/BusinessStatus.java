package com.butchercraft.world.business;

import java.util.Arrays;

public enum BusinessStatus {
    OPERATING("operating"),
    CLOSED("closed"),
    RELOCATED("relocated"),
    MERGED("merged"),
    BANKRUPT("bankrupt"),
    SEASONAL("seasonal"),
    VACANT_RECORD("vacant_record");

    private final String serializedName;

    BusinessStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean hasActiveOccupancy() {
        return this == OPERATING || this == RELOCATED || this == SEASONAL;
    }

    public static BusinessStatus fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business status: " + serializedName));
    }
}
