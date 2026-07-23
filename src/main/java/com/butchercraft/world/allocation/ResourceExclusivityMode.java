package com.butchercraft.world.allocation;

public enum ResourceExclusivityMode {
    EXCLUSIVE("exclusive"),
    SHARED("shared");

    private final String serializedName;

    ResourceExclusivityMode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
