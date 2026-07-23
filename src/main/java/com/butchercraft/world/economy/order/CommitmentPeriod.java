package com.butchercraft.world.economy.order;

import java.util.Arrays;

public enum CommitmentPeriod {
    PER_ORDER("per_order"), DAILY("daily"), WEEKLY("weekly"), INTERVAL("interval"), LIFETIME("lifetime");

    private final String serializedName;

    CommitmentPeriod(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static CommitmentPeriod fromSerializedName(String value) {
        return Arrays.stream(values()).filter(period -> period.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown commitment period: " + value));
    }
}
