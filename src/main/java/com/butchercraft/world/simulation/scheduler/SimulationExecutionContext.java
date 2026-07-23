package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SimulationExecutionContext(
        long authoritativeSimulationTick,
        SimulationStageDefinition stage,
        ScheduledSimulationWork work,
        SimulationWorkRuntime runtimeSnapshot,
        int attemptNumber,
        int remainingTickItems,
        int remainingStageItems,
        long remainingWorkUnits,
        int generationDepth
) {
    public SimulationExecutionContext {
        authoritativeSimulationTick = SchedulerValidation.requireTick(
                authoritativeSimulationTick, "Execution context tick"
        );
        stage = Objects.requireNonNull(stage, "stage");
        work = Objects.requireNonNull(work, "work");
        runtimeSnapshot = Objects.requireNonNull(runtimeSnapshot, "runtimeSnapshot").snapshot();
        if (attemptNumber <= 0 || remainingTickItems < 0 || remainingStageItems < 0
                || remainingWorkUnits < 0L || generationDepth < 0) {
            throw new IllegalArgumentException("Execution context budget values are invalid");
        }
    }
}
