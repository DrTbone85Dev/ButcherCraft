package com.butchercraft.workstation.operation;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum MachineOperatingState {
    OFF,
    STARTING,
    RUNNING,
    RUNNING_EMPTY,
    OUTPUT_BLOCKED,
    STOPPING,
    RESTART_REQUIRED,
    FAULTED,
    RECOVERY_REQUIRED;

    public boolean powered() {
        return this == RUNNING || this == RUNNING_EMPTY || this == OUTPUT_BLOCKED || this == STOPPING;
    }

    public boolean canAdmitChild() {
        return this == RUNNING;
    }

    public boolean canTransitionTo(MachineOperatingState next) {
        return transitions().contains(next);
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MachineOperatingState fromSerializedName(String value) {
        String normalized = MachineOperatingValidation.text(value, "machine operating state");
        for (MachineOperatingState state : values()) {
            if (state.serializedName().equals(normalized)) return state;
        }
        throw new IllegalArgumentException("Unknown machine operating state: " + value);
    }

    private Set<MachineOperatingState> transitions() {
        return switch (this) {
            case OFF -> EnumSet.of(OFF, STARTING, RECOVERY_REQUIRED);
            case STARTING -> EnumSet.of(STARTING, RUNNING, OFF, RECOVERY_REQUIRED);
            case RUNNING -> EnumSet.of(
                    RUNNING,
                    RUNNING_EMPTY,
                    OUTPUT_BLOCKED,
                    STOPPING,
                    RESTART_REQUIRED,
                    FAULTED,
                    RECOVERY_REQUIRED
            );
            case RUNNING_EMPTY -> EnumSet.of(
                    RUNNING_EMPTY,
                    RUNNING,
                    OUTPUT_BLOCKED,
                    STOPPING,
                    RESTART_REQUIRED,
                    FAULTED,
                    RECOVERY_REQUIRED
            );
            case OUTPUT_BLOCKED -> EnumSet.of(
                    OUTPUT_BLOCKED,
                    RUNNING,
                    RUNNING_EMPTY,
                    STOPPING,
                    RESTART_REQUIRED,
                    FAULTED,
                    RECOVERY_REQUIRED
            );
            case STOPPING -> EnumSet.of(STOPPING, OFF, RESTART_REQUIRED, RECOVERY_REQUIRED);
            case RESTART_REQUIRED -> EnumSet.of(
                    RESTART_REQUIRED,
                    RUNNING,
                    RUNNING_EMPTY,
                    OUTPUT_BLOCKED,
                    STOPPING,
                    RECOVERY_REQUIRED
            );
            case FAULTED -> EnumSet.of(FAULTED, STOPPING, OFF, RECOVERY_REQUIRED);
            case RECOVERY_REQUIRED -> EnumSet.of(RECOVERY_REQUIRED, STOPPING, OFF);
        };
    }
}
