package com.butchercraft.world.business.runtime;

import java.util.Arrays;

public enum BusinessOperationalStatus {
    CLOSED("closed"),
    OPENING("opening"),
    OPERATING("operating"),
    SHIFT_CHANGE("shift_change"),
    CLOSING("closing"),
    MAINTENANCE("maintenance"),
    SUSPENDED("suspended");

    private final String serializedName;

    BusinessOperationalStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean isOpenStatus() {
        return this == OPENING || this == OPERATING || this == SHIFT_CHANGE;
    }

    public static BusinessOperationalStatus fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(status -> status.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business operational status: " + serializedName));
    }
}
