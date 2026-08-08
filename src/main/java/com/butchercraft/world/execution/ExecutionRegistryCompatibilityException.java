package com.butchercraft.world.execution;

import java.util.Objects;

public final class ExecutionRegistryCompatibilityException extends IllegalArgumentException {
    private final ExecutionRegistryCompatibilityObservation observation;

    public ExecutionRegistryCompatibilityException(ExecutionRegistryCompatibilityObservation observation) {
        super("Execution registry compatibility blocked: "
                + Objects.requireNonNull(observation, "observation").diagnosticSummary());
        this.observation = observation;
    }

    public ExecutionRegistryCompatibilityObservation observation() {
        return observation;
    }
}
