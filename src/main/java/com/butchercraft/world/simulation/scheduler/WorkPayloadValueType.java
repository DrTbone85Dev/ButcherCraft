package com.butchercraft.world.simulation.scheduler;

import java.util.Arrays;

public enum WorkPayloadValueType {
    STRING("string"), LONG("long"), BOOLEAN("boolean"), DECIMAL("decimal"), IDENTIFIER("identifier");
    private final String serializedName;
    WorkPayloadValueType(String serializedName) { this.serializedName = serializedName; }
    public String serializedName() { return serializedName; }
    public static WorkPayloadValueType fromSerializedName(String value) {
        return Arrays.stream(values()).filter(type -> type.serializedName.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown payload value type: " + value));
    }
}
