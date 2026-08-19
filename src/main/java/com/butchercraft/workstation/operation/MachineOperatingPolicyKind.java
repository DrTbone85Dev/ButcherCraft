package com.butchercraft.workstation.operation;

import java.util.Locale;

public enum MachineOperatingPolicyKind {
    MANUAL_DISCRETE,
    POWERED_ONE_CYCLE,
    POWERED_CONTINUOUS_EXPLICIT_STOP;

    public boolean supportsPersistentRun() {
        return this != MANUAL_DISCRETE;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MachineOperatingPolicyKind fromSerializedName(String value) {
        String normalized = MachineOperatingValidation.text(value, "machine operating policy");
        for (MachineOperatingPolicyKind kind : values()) {
            if (kind.serializedName().equals(normalized)) return kind;
        }
        throw new IllegalArgumentException("Unknown machine operating policy: " + value);
    }
}
