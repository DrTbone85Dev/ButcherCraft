package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SimulationStageDefinition(
        SimulationStageId id,
        String displayName,
        int executionOrder,
        StageFailurePolicy defaultFailurePolicy,
        boolean allowsSameTickEnqueue,
        int schemaVersion
) {
    public SimulationStageDefinition {
        id = Objects.requireNonNull(id, "id");
        displayName = SchedulerValidation.requireText(displayName, "Simulation stage display name", 256);
        if (executionOrder < 0) throw new IllegalArgumentException("Stage execution order must not be negative");
        defaultFailurePolicy = Objects.requireNonNull(defaultFailurePolicy, "defaultFailurePolicy");
        schemaVersion = SchedulerValidation.requireSchema(schemaVersion, "stage definition");
    }
}
