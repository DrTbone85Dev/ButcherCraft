package com.butchercraft.workstation.endpoint;

public enum WorkstationEndpointJournalState {
    REQUESTED,
    PREPARED,
    EFFECT_COMMITTED,
    RESULT_PUBLISHED,
    RECONCILED,
    REJECTED,
    FAILED,
    RECOVERY_REQUIRED,
    UNKNOWN_OUTCOME;

    public boolean terminal() {
        return this == RECONCILED || this == REJECTED || this == FAILED
                || this == RECOVERY_REQUIRED || this == UNKNOWN_OUTCOME;
    }

    public boolean canTransitionTo(WorkstationEndpointJournalState target) {
        if (this == target) return true;
        return switch (this) {
            case REQUESTED -> target == PREPARED || target == REJECTED || target == FAILED
                    || target == RECOVERY_REQUIRED;
            case PREPARED -> target == EFFECT_COMMITTED || target == REJECTED || target == FAILED
                    || target == RECOVERY_REQUIRED || target == UNKNOWN_OUTCOME;
            case EFFECT_COMMITTED -> target == RESULT_PUBLISHED || target == FAILED || target == RECOVERY_REQUIRED;
            case RESULT_PUBLISHED -> target == RECONCILED || target == FAILED || target == RECOVERY_REQUIRED;
            case RECONCILED, REJECTED, FAILED, RECOVERY_REQUIRED, UNKNOWN_OUTCOME -> false;
        };
    }
}
