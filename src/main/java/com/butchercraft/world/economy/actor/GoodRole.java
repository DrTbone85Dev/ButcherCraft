package com.butchercraft.world.economy.actor;

import java.util.Arrays;
import java.util.Set;

public enum GoodRole {
    INPUT("input", Set.of(ActorCapability.CONSUME, ActorCapability.TRANSFORM)),
    OUTPUT("output", Set.of(ActorCapability.PRODUCE, ActorCapability.TRANSFORM)),
    CONSUMED("consumed", Set.of(ActorCapability.CONSUME)),
    STORED("stored", Set.of(ActorCapability.STORE)),
    TRANSPORTED("transported", Set.of(ActorCapability.TRANSPORT)),
    SUPPORTED("supported", Set.of());

    private final String serializedName;
    private final Set<ActorCapability> supportingCapabilities;

    GoodRole(String serializedName, Set<ActorCapability> supportingCapabilities) {
        this.serializedName = serializedName;
        this.supportingCapabilities = Set.copyOf(supportingCapabilities);
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean supports(Set<ActorCapability> capabilities) {
        return supportingCapabilities.isEmpty() || capabilities.stream().anyMatch(supportingCapabilities::contains);
    }

    public static GoodRole fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(role -> role.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown economic actor good role: " + serializedName));
    }
}
