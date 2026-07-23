package com.butchercraft.world.economy.actor;

public enum ActorRuntimeStatus {
    DISABLED(false, false),
    AVAILABLE(true, false),
    OPERATIONAL(true, true),
    SUSPENDED(true, false);

    private final boolean enabled;
    private final boolean operational;

    ActorRuntimeStatus(boolean enabled, boolean operational) {
        this.enabled = enabled;
        this.operational = operational;
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean operational() {
        return operational;
    }
}
