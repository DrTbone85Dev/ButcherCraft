package com.butchercraft.world.simulation;

import java.util.Objects;

public record SimulationTime(
        long simulationTick,
        long elapsedSimulationMinutes,
        int hour,
        int minute
) {
    public SimulationTime {
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Simulation tick must not be negative: " + simulationTick);
        }
        if (elapsedSimulationMinutes < 0L) {
            throw new IllegalArgumentException("Simulation elapsed minutes must not be negative: " + elapsedSimulationMinutes);
        }
        if (hour < 0) {
            throw new IllegalArgumentException("Simulation hour must not be negative: " + hour);
        }
        if (minute < 0) {
            throw new IllegalArgumentException("Simulation minute must not be negative: " + minute);
        }
    }

    public static SimulationTime fromTick(long simulationTick, SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Simulation tick must not be negative: " + simulationTick);
        }
        long elapsedMinutes = simulationTick / configuration.ticksPerSimulationMinute();
        long minutesPerDay = Math.multiplyExact((long) configuration.minutesPerHour(), configuration.hoursPerDay());
        long minuteOfDay = elapsedMinutes % minutesPerDay;
        int hour = (int) (minuteOfDay / configuration.minutesPerHour());
        int minute = (int) (minuteOfDay % configuration.minutesPerHour());
        return new SimulationTime(simulationTick, elapsedMinutes, hour, minute);
    }

    public void validate(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (hour >= configuration.hoursPerDay()) {
            throw new IllegalArgumentException("Simulation hour exceeds configured day length: " + hour);
        }
        if (minute >= configuration.minutesPerHour()) {
            throw new IllegalArgumentException("Simulation minute exceeds configured hour length: " + minute);
        }
        if (!equals(fromTick(simulationTick, configuration))) {
            throw new IllegalArgumentException("Simulation time does not match configured tick: " + simulationTick);
        }
    }
}
