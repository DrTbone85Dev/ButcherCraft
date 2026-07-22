package com.butchercraft.world.economy.actor;

import java.util.Arrays;

public enum ActorType {
    PRODUCER("producer"),
    CONSUMER("consumer"),
    STORAGE("storage"),
    TRANSPORT("transport"),
    MARKET("market"),
    PROCESSOR("processor"),
    UTILITY("utility"),
    SERVICE("service"),
    MULTI_ROLE("multi_role");

    private final String serializedName;

    ActorType(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ActorType fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(type -> type.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown economic actor type: " + serializedName));
    }
}
