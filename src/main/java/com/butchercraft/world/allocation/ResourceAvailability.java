package com.butchercraft.world.allocation;

public enum ResourceAvailability {
    AVAILABLE("available"),
    UNAVAILABLE("unavailable");

    private final String serializedName;

    ResourceAvailability(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
