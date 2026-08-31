package com.butchercraft.world.checkpoint;

public enum LiveCheckpointRequestOutcome {
    ACCEPTED,
    COALESCED,
    BUSY,
    RECOVERY_BLOCKED,
    NOT_INITIALIZED
}
