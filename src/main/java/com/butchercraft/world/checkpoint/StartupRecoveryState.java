package com.butchercraft.world.checkpoint;

public enum StartupRecoveryState {
    NOT_STARTED,
    ANALYZING,
    LIVE_SELECTED,
    RESTORING,
    RESTORED,
    RESTORED_WITH_AUTHORITY_BLOCK,
    BLOCKED
}
