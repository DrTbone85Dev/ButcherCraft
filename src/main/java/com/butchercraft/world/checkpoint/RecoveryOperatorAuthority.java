package com.butchercraft.world.checkpoint;

public enum RecoveryOperatorAuthority {
    ORDINARY_PLAYER(false),
    OPERATOR(true),
    ADMINISTRATOR(true);

    private final boolean mayPublishRecovery;

    RecoveryOperatorAuthority(boolean mayPublishRecovery) {
        this.mayPublishRecovery = mayPublishRecovery;
    }

    public boolean mayPublishRecovery() {
        return mayPublishRecovery;
    }
}
