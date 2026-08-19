package com.butchercraft.world.execution;

public record MachineRunConfiguration(
        int maximumRetainedRuns,
        int maximumRetainedChildrenPerRun,
        String configurationIdentity
) {
    public MachineRunConfiguration {
        if (maximumRetainedRuns <= 0 || maximumRetainedChildrenPerRun <= 0) {
            throw new IllegalArgumentException("Machine Run limits must be positive");
        }
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Machine Run configuration identity"
        );
    }

    public static MachineRunConfiguration standard() {
        return new MachineRunConfiguration(
                4_096,
                4_096,
                "butchercraft:machine_run_configuration/schema_1"
        );
    }
}
