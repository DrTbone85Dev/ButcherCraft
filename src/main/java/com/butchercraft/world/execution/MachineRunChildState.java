package com.butchercraft.world.execution;

import java.util.Locale;

public enum MachineRunChildState {
    PREPARED,
    ADMITTED,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN_OUTCOME,
    RECOVERY_REQUIRED;

    public boolean terminal() {
        return switch (this) {
            case COMPLETED, FAILED, CANCELLED, UNKNOWN_OUTCOME, RECOVERY_REQUIRED -> true;
            case PREPARED, ADMITTED -> false;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MachineRunChildState fromSerializedName(String value) {
        String normalized = ExecutionValidation.requireText(value, "Machine Run child state", 64);
        for (MachineRunChildState state : values()) {
            if (state.serializedName().equals(normalized)) return state;
        }
        throw new IllegalArgumentException("Unknown Machine Run child state: " + value);
    }
}
