package com.butchercraft.world.workforce.materialhandling;

public enum EmployeeMaterialHandlingAssignmentState {
    IDLE,
    WALKING_TO_SOURCE,
    WAITING_FOR_SOURCE_RESERVATION,
    WITHDRAWAL_REQUESTED,
    CARRYING_TO_DESTINATION,
    WAITING_FOR_DESTINATION_RESERVATION,
    DEPOSIT_REQUESTED,
    COMPLETED,
    CANCELLATION_REQUESTED,
    CANCELLED,
    RECOVERY_REQUIRED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }

    public boolean canTransitionTo(EmployeeMaterialHandlingAssignmentState target) {
        if (this == target) {
            return true;
        }
        if (target == RECOVERY_REQUIRED || target == CANCELLATION_REQUESTED || target == FAILED) {
            return !terminal();
        }
        return switch (this) {
            case IDLE -> target == WALKING_TO_SOURCE || target == WAITING_FOR_SOURCE_RESERVATION;
            case WALKING_TO_SOURCE -> target == WITHDRAWAL_REQUESTED || target == WAITING_FOR_SOURCE_RESERVATION;
            case WAITING_FOR_SOURCE_RESERVATION -> target == WALKING_TO_SOURCE;
            case WITHDRAWAL_REQUESTED -> target == CARRYING_TO_DESTINATION;
            case CARRYING_TO_DESTINATION -> target == WAITING_FOR_DESTINATION_RESERVATION
                    || target == DEPOSIT_REQUESTED;
            case WAITING_FOR_DESTINATION_RESERVATION -> target == CARRYING_TO_DESTINATION;
            case DEPOSIT_REQUESTED -> target == COMPLETED;
            case CANCELLATION_REQUESTED -> target == CANCELLED;
            case RECOVERY_REQUIRED -> target == CANCELLATION_REQUESTED;
            case COMPLETED, CANCELLED, FAILED -> false;
        };
    }
}
