package com.butchercraft.world.business;

import java.util.Arrays;

public enum BusinessOccupancyReason {
    FOUNDED("founded"),
    RELOCATED_TO("relocated_to"),
    EXPANSION_SITE("expansion_site"),
    ACQUIRED_SITE("acquired_site"),
    MERGER_RECORD("merger_record"),
    CLOSURE_RECORD("closure_record"),
    VACANCY_RECORD("vacancy_record");

    private final String serializedName;

    BusinessOccupancyReason(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BusinessOccupancyReason fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business occupancy reason: " + serializedName));
    }
}
