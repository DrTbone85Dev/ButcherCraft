package com.butchercraft.world.execution;

public enum ExecutionRegistryCompatibilityClassification {
    IDENTICAL(true),
    ADDITIVE_COMPATIBLE(true),
    INCOMPATIBLE(false),
    INDETERMINATE_RECOVERY_REQUIRED(false);

    private final boolean permitsExecutionAuthority;

    ExecutionRegistryCompatibilityClassification(boolean permitsExecutionAuthority) {
        this.permitsExecutionAuthority = permitsExecutionAuthority;
    }

    public boolean permitsExecutionAuthority() {
        return permitsExecutionAuthority;
    }

    public boolean operatorRecoveryRequired() {
        return !permitsExecutionAuthority;
    }
}
