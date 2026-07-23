package com.butchercraft.world.allocation;

public enum AllocationMetadataValueType {
    BOOLEAN("boolean"),
    DECIMAL("decimal"),
    IDENTIFIER("identifier"),
    INTEGER("integer"),
    TEXT("text");

    private final String serializedName;

    AllocationMetadataValueType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }
}
