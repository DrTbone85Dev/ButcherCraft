package com.butchercraft.development.checkpoint;

import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;

import java.util.Objects;

public record DevelopmentCheckpointCaptureRequest(
        DevelopmentCheckpointRequestContext context,
        SimulationClock clock,
        SimulationSchedulerManager scheduler
) {
    public DevelopmentCheckpointCaptureRequest {
        context = Objects.requireNonNull(context, "context");
        clock = Objects.requireNonNull(clock, "clock");
        scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }
}
