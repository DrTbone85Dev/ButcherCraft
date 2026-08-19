package com.butchercraft.workstation.operation;

import java.util.Locale;

public enum MachineOperatingResultCode {
    ACCEPTED,
    EXISTING_STATE,
    UNSUPPORTED_POLICY,
    INVALID_STATE,
    STALE_REVISION,
    START_IDENTITY_CONFLICT,
    STOP_IDENTITY_CONFLICT,
    RUN_IDENTITY_CONFLICT,
    CHILD_IDENTITY_CONFLICT,
    ENDPOINT_UNAVAILABLE,
    REPLACEMENT_INSTANCE,
    RECOVERY_REQUIRED,
    UNKNOWN_INSTANCE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MachineOperatingResultCode fromSerializedName(String value) {
        String normalized = MachineOperatingValidation.text(value, "machine operating result code");
        for (MachineOperatingResultCode code : values()) {
            if (code.serializedName().equals(normalized)) return code;
        }
        throw new IllegalArgumentException("Unknown machine operating result code: " + value);
    }
}
