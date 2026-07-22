package com.butchercraft.world.simulation;

import java.util.List;
import java.util.Objects;

public record SimulationState(
        int schemaVersion,
        long simulationTick,
        SimulationCalendar calendar,
        List<ScheduledSimulationEvent> pendingEvents
) {
    public SimulationState {
        if (schemaVersion != SimulationSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported simulation schema version: " + schemaVersion);
        }
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Simulation tick must not be negative: " + simulationTick);
        }
        calendar = Objects.requireNonNull(calendar, "calendar");
        pendingEvents = List.copyOf(Objects.requireNonNull(pendingEvents, "pendingEvents"));
    }

    public static SimulationState initial(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return new SimulationState(
                SimulationSchema.CURRENT_VERSION,
                0L,
                SimulationCalendar.fromTick(0L, configuration),
                List.of()
        );
    }

    public void validate(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        SimulationCalendar expectedCalendar = SimulationCalendar.fromTick(simulationTick, configuration);
        if (!calendar.equals(expectedCalendar)) {
            throw new IllegalArgumentException("Simulation calendar does not match simulation tick: " + simulationTick);
        }
        calendar.validate(configuration);
        SimulationScheduler.of(pendingEvents, simulationTick);
    }
}
