package com.butchercraft.world.business;

import java.util.Arrays;

public enum BusinessReputation {
    LEGENDARY("legendary"),
    EXCELLENT("excellent"),
    GOOD("good"),
    AVERAGE("average"),
    DECLINING("declining"),
    POOR("poor");

    private final String serializedName;

    BusinessReputation(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static BusinessReputation fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(value -> value.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown business reputation: " + serializedName));
    }
}
