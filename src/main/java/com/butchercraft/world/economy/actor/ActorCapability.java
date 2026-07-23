package com.butchercraft.world.economy.actor;

import java.util.Arrays;

public enum ActorCapability {
    PRODUCE("produce"),
    CONSUME("consume"),
    STORE("store"),
    TRANSPORT("transport"),
    TRANSFORM("transform"),
    BUY("buy"),
    SELL("sell"),
    IMPORT("import"),
    EXPORT("export"),
    MAINTAIN("maintain");

    private final String serializedName;

    ActorCapability(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static ActorCapability fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(capability -> capability.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown economic actor capability: " + serializedName
                ));
    }
}
