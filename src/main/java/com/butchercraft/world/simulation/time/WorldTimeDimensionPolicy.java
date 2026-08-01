package com.butchercraft.world.simulation.time;

public enum WorldTimeDimensionPolicy {
    OVERWORLD_BUSINESS_SOURCE("overworld_business_source");

    private final String serializedName;

    WorldTimeDimensionPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static WorldTimeDimensionPolicy fromSerializedName(String value) {
        for (WorldTimeDimensionPolicy policy : values()) {
            if (policy.serializedName.equals(value)) {
                return policy;
            }
        }
        throw new IllegalArgumentException("Unknown world time dimension policy: " + value);
    }
}
