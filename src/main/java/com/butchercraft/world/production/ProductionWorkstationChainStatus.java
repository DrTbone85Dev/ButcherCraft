package com.butchercraft.world.production;

public enum ProductionWorkstationChainStatus {
    AWAITING_GRINDER_ASSIGNMENT,
    GRINDER_ASSIGNED,
    GRINDER_RUNNING,
    GRINDER_COMPLETE,
    AWAITING_MANUAL_TRANSFER,
    AWAITING_PATTY_FORMER_ASSIGNMENT,
    PATTY_FORMER_ASSIGNED,
    PATTY_FORMER_RUNNING,
    PATTY_FORMER_COMPLETE,
    COMPLETE,
    FAILED,
    UNKNOWN_OUTCOME,
    CANCELLED_BEFORE_FIRST_EFFECT;

    public boolean terminal() {
        return this == COMPLETE
                || this == FAILED
                || this == UNKNOWN_OUTCOME
                || this == CANCELLED_BEFORE_FIRST_EFFECT;
    }
}
