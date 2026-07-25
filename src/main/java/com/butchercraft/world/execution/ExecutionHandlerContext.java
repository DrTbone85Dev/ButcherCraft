package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SchedulerEffectIdentity;
import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;

import java.util.Objects;

public record ExecutionHandlerContext(
        long authoritativeSimulationTick,
        ExecutionOperationSnapshot operation,
        SchedulerInvocationIdentity schedulerInvocationIdentity,
        SchedulerEffectIdentity schedulerEffectIdentity,
        long remainingWorkUnits
) {
    public ExecutionHandlerContext {
        authoritativeSimulationTick = ExecutionValidation.requireTick(
                authoritativeSimulationTick,
                "Execution handler tick"
        );
        operation = Objects.requireNonNull(operation, "operation");
        schedulerInvocationIdentity = Objects.requireNonNull(schedulerInvocationIdentity, "schedulerInvocationIdentity");
        schedulerEffectIdentity = Objects.requireNonNull(schedulerEffectIdentity, "schedulerEffectIdentity");
        if (remainingWorkUnits < 0L) {
            throw new IllegalArgumentException("Remaining work units must not be negative");
        }
    }
}
