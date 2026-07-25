package com.butchercraft.development.checkpoint;

import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationEventBus;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record DevelopmentCheckpointRestorationRequest(
        DevelopmentCheckpointRequestContext context,
        SimulationConfiguration clockConfiguration,
        SimulationEventBus clockEventBus,
        Supplier<SimulationClock> currentClock,
        Consumer<SimulationClock> publishClock,
        SimulationWorkHandlerRegistry schedulerHandlerRegistry,
        Supplier<SimulationSchedulerManager> currentScheduler,
        Consumer<SimulationSchedulerManager> publishScheduler
) {
    public DevelopmentCheckpointRestorationRequest {
        context = Objects.requireNonNull(context, "context");
        clockConfiguration = Objects.requireNonNull(clockConfiguration, "clockConfiguration");
        clockEventBus = Objects.requireNonNull(clockEventBus, "clockEventBus");
        currentClock = Objects.requireNonNull(currentClock, "currentClock");
        publishClock = Objects.requireNonNull(publishClock, "publishClock");
        schedulerHandlerRegistry = Objects.requireNonNull(schedulerHandlerRegistry, "schedulerHandlerRegistry");
        currentScheduler = Objects.requireNonNull(currentScheduler, "currentScheduler");
        publishScheduler = Objects.requireNonNull(publishScheduler, "publishScheduler");
    }
}
