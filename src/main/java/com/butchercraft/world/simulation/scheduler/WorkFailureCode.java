package com.butchercraft.world.simulation.scheduler;

import java.util.Arrays;
import java.util.Locale;

public enum WorkFailureCode {
    UNKNOWN_WORK, UNKNOWN_WORK_TYPE, UNKNOWN_STAGE, HANDLER_NOT_REGISTERED, INVALID_PAYLOAD,
    INVALID_STATUS, INVALID_STATUS_TRANSITION, INVALID_TICK, BACKWARD_TICK, DUPLICATE_WORK_ID,
    DUPLICATE_SUBMISSION_SEQUENCE, BUDGET_EXHAUSTED, RETRY_LIMIT_REACHED, WORK_EXPIRED,
    HANDLER_REJECTED, HANDLER_EXCEPTION, PERSISTENCE_FAILURE, UNSUPPORTED_SCHEMA,
    VALIDATION_FAILED, INTERNAL_INVARIANT_VIOLATION, UNKNOWN;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WorkFailureCode fromSerializedName(String value) {
        return Arrays.stream(values()).filter(code -> code.serializedName().equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown work failure code: " + value));
    }
}
