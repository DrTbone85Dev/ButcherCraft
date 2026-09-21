package com.butchercraft.world.workforce.machineoperation;

public enum EmployeeMachineOperationAssignmentState {
    CREATED,
    NAVIGATING,
    WAITING_FOR_RESERVATION,
    WAITING_FOR_MACHINE,
    WAITING_FOR_INPUT,
    READY_TO_START,
    START_REQUESTED,
    RUNNING,
    RUNNING_EMPTY,
    OUTPUT_BLOCKED,
    STOP_REQUESTED,
    WAITING_FOR_SAFE_STOP,
    CANCELLATION_REQUESTED,
    RESTART_REQUIRED,
    COMPLETED,
    CANCELLED,
    INTERRUPTED,
    RECOVERY_REQUIRED,
    FAILED;

    public boolean terminal() {
        return this == COMPLETED || this == CANCELLED || this == INTERRUPTED || this == FAILED;
    }

    public boolean requiresRunReference() {
        return switch (this) {
            case START_REQUESTED, RUNNING, RUNNING_EMPTY, OUTPUT_BLOCKED, STOP_REQUESTED,
                    WAITING_FOR_SAFE_STOP, RESTART_REQUIRED, COMPLETED -> true;
            default -> false;
        };
    }

    public boolean requiresReservation() {
        return switch (this) {
            case NAVIGATING, WAITING_FOR_INPUT, READY_TO_START, START_REQUESTED, RUNNING, RUNNING_EMPTY,
                    WAITING_FOR_MACHINE, OUTPUT_BLOCKED, STOP_REQUESTED, WAITING_FOR_SAFE_STOP,
                    RESTART_REQUIRED -> true;
            default -> false;
        };
    }

    public boolean canTransitionTo(EmployeeMachineOperationAssignmentState target) {
        if (this == target) return true;
        if (terminal()) return false;
        if (target == RECOVERY_REQUIRED || target == FAILED) return true;
        if (target == CANCELLATION_REQUESTED) return true;
        return switch (this) {
            case CREATED -> target == WAITING_FOR_RESERVATION || target == NAVIGATING;
            case WAITING_FOR_RESERVATION -> target == NAVIGATING || target == CANCELLED;
            case NAVIGATING -> target == WAITING_FOR_INPUT || target == READY_TO_START
                    || target == WAITING_FOR_RESERVATION || target == CANCELLED;
            case WAITING_FOR_INPUT -> target == READY_TO_START || target == NAVIGATING
                    || target == WAITING_FOR_MACHINE || target == CANCELLED;
            case WAITING_FOR_MACHINE -> target == READY_TO_START || target == WAITING_FOR_INPUT
                    || target == NAVIGATING || target == CANCELLED;
            case READY_TO_START -> target == START_REQUESTED || target == WAITING_FOR_INPUT
                    || target == WAITING_FOR_MACHINE || target == CANCELLED;
            case START_REQUESTED -> target == RUNNING || target == RUNNING_EMPTY || target == OUTPUT_BLOCKED
                    || target == RESTART_REQUIRED || target == CANCELLED;
            case RUNNING -> target == RUNNING_EMPTY || target == OUTPUT_BLOCKED || target == STOP_REQUESTED
                    || target == WAITING_FOR_SAFE_STOP
                    || target == RESTART_REQUIRED || target == INTERRUPTED;
            case RUNNING_EMPTY -> target == RUNNING || target == OUTPUT_BLOCKED || target == STOP_REQUESTED
                    || target == WAITING_FOR_SAFE_STOP
                    || target == RESTART_REQUIRED || target == INTERRUPTED;
            case OUTPUT_BLOCKED -> target == RUNNING || target == RUNNING_EMPTY || target == STOP_REQUESTED
                    || target == WAITING_FOR_SAFE_STOP
                    || target == RESTART_REQUIRED || target == INTERRUPTED;
            case STOP_REQUESTED -> target == WAITING_FOR_SAFE_STOP || target == COMPLETED
                    || target == CANCELLED || target == INTERRUPTED || target == RESTART_REQUIRED;
            case WAITING_FOR_SAFE_STOP -> target == COMPLETED || target == CANCELLED
                    || target == INTERRUPTED || target == RESTART_REQUIRED;
            case CANCELLATION_REQUESTED -> target == WAITING_FOR_SAFE_STOP || target == CANCELLED
                    || target == RESTART_REQUIRED;
            case RESTART_REQUIRED -> target == RUNNING || target == RUNNING_EMPTY || target == OUTPUT_BLOCKED
                    || target == STOP_REQUESTED || target == WAITING_FOR_SAFE_STOP
                    || target == CANCELLATION_REQUESTED || target == INTERRUPTED;
            case RECOVERY_REQUIRED -> target == CANCELLATION_REQUESTED || target == CANCELLED;
            case COMPLETED, CANCELLED, INTERRUPTED, FAILED -> false;
        };
    }
}
