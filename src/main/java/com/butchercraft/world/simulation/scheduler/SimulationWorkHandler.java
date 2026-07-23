package com.butchercraft.world.simulation.scheduler;

public interface SimulationWorkHandler {
    SimulationWorkTypeId supportedTypeId();
    HandlerEffectType effectType();
    WorkValidationResult validate(ScheduledSimulationWork work);
    SimulationWorkResult execute(SimulationExecutionContext context);
}
