package com.butchercraft.world.simulation;

@FunctionalInterface
public interface SimulationEventListener {
    void onSimulationEvent(ScheduledSimulationEvent event);
}
