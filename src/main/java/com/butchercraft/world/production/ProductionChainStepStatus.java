package com.butchercraft.world.production;

public enum ProductionChainStepStatus {
    AWAITING_ASSIGNMENT,
    ASSIGNED,
    RUNNING,
    COMPLETE,
    FAILED,
    UNKNOWN_OUTCOME;

    public boolean terminal() {
        return this == COMPLETE || this == FAILED || this == UNKNOWN_OUTCOME;
    }
}
