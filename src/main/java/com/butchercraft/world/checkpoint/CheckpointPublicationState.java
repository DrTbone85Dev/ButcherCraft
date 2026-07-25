package com.butchercraft.world.checkpoint;

public enum CheckpointPublicationState {
    PREPARING,
    COMPLETE_CANDIDATE,
    COMMITTED,
    SUPERSEDED,
    ABANDONED_FAILED_CANDIDATE;

    public boolean committedHistory() {
        return this == COMMITTED || this == SUPERSEDED;
    }

    public boolean authoritativeHeadState() {
        return this == COMMITTED;
    }
}
