package com.butchercraft.world.execution;

import java.util.Locale;

public enum ExecutionFailureCode {
    UNKNOWN_OPERATION,
    UNSUPPORTED_OPERATION_TYPE,
    HANDLER_NOT_REGISTERED,
    INVALID_AUTHORIZATION_EVIDENCE,
    STALE_AUTHORIZATION_EVIDENCE,
    AUTHORIZATION_ALREADY_CONSUMED,
    AUTHORIZATION_IDENTITY_CONFLICT,
    OPERATION_IDENTITY_CONFLICT,
    INVALID_STATUS,
    INVALID_STATUS_TRANSITION,
    INVALID_SCHEDULER_INVOCATION,
    INVALID_SCHEDULER_EFFECT_IDENTITY,
    INVALID_FROZEN_INPUT,
    HANDLER_REJECTED_AUTHORIZATION,
    HANDLER_REJECTED_INPUT,
    HANDLER_FAILED,
    HANDLER_EXCEPTION_UNKNOWN_OUTCOME,
    OWNER_RESULT_MISSING,
    OWNER_RESULT_CONFLICT,
    PUBLICATION_REJECTED,
    RUNTIME_CAPACITY_EXHAUSTED,
    INTERNAL_INVARIANT_VIOLATION,
    PERSISTENCE_FAILURE,
    UNSUPPORTED_SCHEMA,
    CANCELLATION_REQUESTED,
    CANCEL_UNSUPPORTED_AFTER_START,
    UNKNOWN;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ExecutionFailureCode fromSerializedName(String value) {
        String normalized = ExecutionValidation.requireText(value, "Execution failure code", 128);
        for (ExecutionFailureCode code : values()) {
            if (code.serializedName().equals(normalized)) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unknown Execution failure code: " + value);
    }
}
