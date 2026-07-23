package com.butchercraft.world.production;

public enum ProductionRunStatus {
    PLANNED,
    READY,
    SCHEDULED,
    RUNNING,
    BLOCKED,
    PAUSED,
    AWAITING_TRANSACTION,
    COMPLETED,
    FAILED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
