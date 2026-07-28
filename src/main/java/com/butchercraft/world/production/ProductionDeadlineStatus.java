package com.butchercraft.world.production;

public enum ProductionDeadlineStatus {
    NO_DEADLINE,
    UPCOMING,
    DUE_NOW,
    OVERDUE,
    COMPLETED_EARLY,
    COMPLETED_ON_TIME,
    COMPLETED_LATE,
    CANCELLED;

    public boolean terminalCompletion() {
        return this == COMPLETED_EARLY || this == COMPLETED_ON_TIME || this == COMPLETED_LATE;
    }
}
