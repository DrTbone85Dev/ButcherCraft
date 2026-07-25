package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;
import java.util.Optional;

public record SimulationExecutionContext(
        long authoritativeSimulationTick,
        SimulationStageDefinition stage,
        ScheduledSimulationWork work,
        SimulationWorkRuntime runtimeSnapshot,
        SchedulerInvocationIdentity invocationIdentity,
        Optional<SchedulerEffectIdentity> effectIdentity,
        SchedulerEffectPolicy effectPolicy,
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
        invocationIdentity = Objects.requireNonNull(invocationIdentity, "invocationIdentity");
        effectIdentity = Objects.requireNonNull(effectIdentity, "effectIdentity");
        effectPolicy = Objects.requireNonNull(effectPolicy, "effectPolicy");
        if (effectIdentity.isPresent() && !effectPolicy.requiresEffectIdentity()) {
            throw new IllegalArgumentException("Read-only context cannot include Effect Identity");
        }
        if (effectPolicy.requiresEffectIdentity() && effectIdentity.isEmpty()) {
            throw new IllegalArgumentException("Consequential context requires Effect Identity");
        }
        if (attemptNumber <= 0 || remainingTickItems < 0 || remainingStageItems < 0
                || remainingWorkUnits < 0L || generationDepth < 0) {
            throw new IllegalArgumentException("Execution context budget values are invalid");
        }
    }
}
