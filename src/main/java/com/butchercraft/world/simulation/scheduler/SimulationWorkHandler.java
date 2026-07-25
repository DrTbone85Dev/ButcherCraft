package com.butchercraft.world.simulation.scheduler;

public interface SimulationWorkHandler {
    SimulationWorkTypeId supportedTypeId();
    HandlerEffectType effectType();
    default SchedulerEffectPolicy effectPolicy() {
        return SchedulerEffectPolicy.defaultFor(supportedTypeId(), effectType());
    }
    WorkValidationResult validate(ScheduledSimulationWork work);
    SimulationWorkResult execute(SimulationExecutionContext context);
}
