package com.butchercraft.world.materialhandling;

public enum MaterialTransferLifecycle {
    REQUESTED,
    SOURCE_BOUND,
    SOURCE_WITHDRAW_PREPARED,
    SOURCE_WITHDRAW_COMMITTED,
    IN_TRANSIT,
    DESTINATION_BOUND,
    DESTINATION_DEPOSIT_PREPARED,
    DESTINATION_DEPOSIT_COMMITTED,
    CANCELLATION_REQUESTED,
    CANCELLATION_RETURN_PREPARED,
    CANCELLATION_RETURN_COMMITTED,
    COMPLETED,
    CANCELLED,
    FAILED,
    UNKNOWN_OUTCOME,
    RECOVERY_REQUIRED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED
                || this == UNKNOWN_OUTCOME;
    }

    public boolean canTransitionTo(MaterialTransferLifecycle target) {
        if (this == target) return true;
        if (target == FAILED || target == UNKNOWN_OUTCOME || target == RECOVERY_REQUIRED) return !terminal();
        return switch (this) {
            case REQUESTED -> target == SOURCE_BOUND || target == CANCELLED;
            case SOURCE_BOUND -> target == SOURCE_WITHDRAW_PREPARED || target == CANCELLED;
            case SOURCE_WITHDRAW_PREPARED -> target == SOURCE_WITHDRAW_COMMITTED || target == CANCELLED;
            case SOURCE_WITHDRAW_COMMITTED -> target == IN_TRANSIT || target == CANCELLATION_REQUESTED;
            case IN_TRANSIT -> target == DESTINATION_BOUND || target == CANCELLATION_REQUESTED;
            case DESTINATION_BOUND -> target == DESTINATION_DEPOSIT_PREPARED
                    || target == CANCELLATION_REQUESTED;
            case DESTINATION_DEPOSIT_PREPARED -> target == DESTINATION_DEPOSIT_COMMITTED
                    || target == CANCELLATION_REQUESTED;
            case DESTINATION_DEPOSIT_COMMITTED -> target == COMPLETED;
            case CANCELLATION_REQUESTED -> target == CANCELLATION_RETURN_PREPARED
                    || target == CANCELLATION_RETURN_COMMITTED;
            case CANCELLATION_RETURN_PREPARED -> target == CANCELLATION_RETURN_COMMITTED;
            case CANCELLATION_RETURN_COMMITTED -> target == CANCELLED;
            case RECOVERY_REQUIRED -> target == CANCELLATION_REQUESTED
                    || target == SOURCE_WITHDRAW_COMMITTED
                    || target == DESTINATION_DEPOSIT_COMMITTED
                    || target == CANCELLATION_RETURN_COMMITTED;
            case UNKNOWN_OUTCOME -> target == SOURCE_WITHDRAW_COMMITTED
                    || target == DESTINATION_DEPOSIT_COMMITTED
                    || target == CANCELLATION_RETURN_COMMITTED;
            case COMPLETED, CANCELLED, FAILED -> false;
        };
    }
}
