package com.butchercraft.world.execution;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum ExecutionStatus {
    AUTHORIZED,
    READY,
    DISPATCHED,
    RUNNING,
    AWAITING_OWNER_RESULT,
    SUCCEEDED,
    REJECTED,
    FAILED,
    UNKNOWN_OUTCOME,
    CANCELLED_BEFORE_START;

    public boolean terminal() {
        return switch (this) {
            case SUCCEEDED, REJECTED, FAILED, UNKNOWN_OUTCOME, CANCELLED_BEFORE_START -> true;
            default -> false;
        };
    }

    Set<ExecutionStatus> allowedNextStatuses() {
        return switch (this) {
            case AUTHORIZED -> EnumSet.of(READY, REJECTED, FAILED, CANCELLED_BEFORE_START);
            case READY -> EnumSet.of(DISPATCHED, REJECTED, FAILED, CANCELLED_BEFORE_START);
            case DISPATCHED -> EnumSet.of(RUNNING, FAILED, UNKNOWN_OUTCOME);
            case RUNNING -> EnumSet.of(AWAITING_OWNER_RESULT, SUCCEEDED, REJECTED, FAILED, UNKNOWN_OUTCOME);
            case AWAITING_OWNER_RESULT -> EnumSet.of(DISPATCHED, SUCCEEDED, REJECTED, FAILED, UNKNOWN_OUTCOME);
            case SUCCEEDED, REJECTED, FAILED, UNKNOWN_OUTCOME, CANCELLED_BEFORE_START -> EnumSet.noneOf(
                    ExecutionStatus.class
            );
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ExecutionStatus fromSerializedName(String value) {
        String normalized = ExecutionValidation.requireText(value, "Execution status", 64);
        for (ExecutionStatus status : values()) {
            if (status.serializedName().equals(normalized)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown Execution status: " + value);
    }
}
