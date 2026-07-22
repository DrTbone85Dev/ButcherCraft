package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.ScheduledSimulationEvent;
import com.butchercraft.world.simulation.SimulationCalendar;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationEventListener;
import com.butchercraft.world.simulation.SimulationEventType;
import com.butchercraft.world.simulation.SimulationTime;

import java.util.Objects;

public final class BusinessEventListener implements SimulationEventListener {
    private final BusinessRuntimeManager manager;
    private final SimulationConfiguration configuration;

    public BusinessEventListener(BusinessRuntimeManager manager, SimulationConfiguration configuration) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public void onSimulationEvent(ScheduledSimulationEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.eventType() == SimulationEventType.DAILY_ROLLOVER) {
            evaluateAt(event.scheduledSimulationTick());
        } else if (event.eventType() == SimulationEventType.WEEKLY_ROLLOVER) {
            evaluateAt(event.scheduledSimulationTick());
            manager.validateRuntimeState();
        }
    }

    private void evaluateAt(long simulationTick) {
        manager.evaluateAt(
                SimulationCalendar.fromTick(simulationTick, configuration),
                SimulationTime.fromTick(simulationTick, configuration),
                simulationTick
        );
    }
}
