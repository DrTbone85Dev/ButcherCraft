package com.butchercraft.world.execution;

import java.util.Locale;

public enum MachineRunResultCode {
    ACCEPTED,
    EXISTING_RUN,
    ALREADY_RUNNING,
    START_IDENTITY_CONFLICT,
    STOP_IDENTITY_CONFLICT,
    UNKNOWN_RUN,
    STALE_RUN,
    INVALID_LIFECYCLE,
    STALE_REVISION,
    INSTANCE_MISMATCH,
    CONFIGURATION_MISMATCH,
    CHILD_ALREADY_ACTIVE,
    CHILD_IDENTITY_CONFLICT,
    CHILD_NOT_PREPARED,
    CHILD_NOT_ACTIVE,
    CHILD_RESULT_CONFLICT,
    ENDPOINT_UNAVAILABLE,
    REPLACEMENT_INSTANCE,
    RECOVERY_REQUIRED,
    CAPACITY_EXHAUSTED;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MachineRunResultCode fromSerializedName(String value) {
        String normalized = ExecutionValidation.requireText(value, "Machine Run result code", 96);
        for (MachineRunResultCode code : values()) {
            if (code.serializedName().equals(normalized)) return code;
        }
        throw new IllegalArgumentException("Unknown Machine Run result code: " + value);
    }
}
