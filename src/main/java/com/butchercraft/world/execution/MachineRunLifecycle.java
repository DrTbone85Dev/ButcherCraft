package com.butchercraft.world.execution;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum MachineRunLifecycle {
    AUTHORIZED,
    SUSPENDED_RESTART_REQUIRED,
    STOP_REQUESTED,
    STOPPED,
    FAILED,
    RECOVERY_REQUIRED;

    public boolean terminal() {
        return this == STOPPED || this == FAILED;
    }

    public boolean authorizesChildren() {
        return this == AUTHORIZED;
    }

    public boolean canTransitionTo(MachineRunLifecycle next) {
        return allowedNext().contains(next);
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MachineRunLifecycle fromSerializedName(String value) {
        String normalized = ExecutionValidation.requireText(value, "Machine Run lifecycle", 64);
        for (MachineRunLifecycle lifecycle : values()) {
            if (lifecycle.serializedName().equals(normalized)) return lifecycle;
        }
        throw new IllegalArgumentException("Unknown Machine Run lifecycle: " + value);
    }

    private Set<MachineRunLifecycle> allowedNext() {
        return switch (this) {
            case AUTHORIZED -> EnumSet.of(
                    AUTHORIZED,
                    SUSPENDED_RESTART_REQUIRED,
                    STOP_REQUESTED,
                    FAILED,
                    RECOVERY_REQUIRED
            );
            case SUSPENDED_RESTART_REQUIRED -> EnumSet.of(
                    SUSPENDED_RESTART_REQUIRED,
                    AUTHORIZED,
                    STOP_REQUESTED,
                    RECOVERY_REQUIRED
            );
            case STOP_REQUESTED -> EnumSet.of(STOP_REQUESTED, STOPPED, RECOVERY_REQUIRED);
            case RECOVERY_REQUIRED -> EnumSet.of(RECOVERY_REQUIRED, STOP_REQUESTED);
            case STOPPED, FAILED -> EnumSet.noneOf(MachineRunLifecycle.class);
        };
    }
}
