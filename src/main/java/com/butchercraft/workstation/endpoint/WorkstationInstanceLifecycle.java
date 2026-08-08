package com.butchercraft.workstation.endpoint;

public enum WorkstationInstanceLifecycle {
    PENDING_BINDING,
    ACTIVE,
    RETIRED,
    IDENTITY_CONFLICT,
    RECOVERY_REQUIRED;

    public boolean canTransitionTo(WorkstationInstanceLifecycle target) {
        if (this == target) return true;
        return switch (this) {
            case PENDING_BINDING -> target == ACTIVE
                    || target == IDENTITY_CONFLICT
                    || target == RECOVERY_REQUIRED;
            case ACTIVE -> target == RETIRED
                    || target == IDENTITY_CONFLICT
                    || target == RECOVERY_REQUIRED;
            case RETIRED, IDENTITY_CONFLICT, RECOVERY_REQUIRED -> false;
        };
    }
}
