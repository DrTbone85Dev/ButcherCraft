package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SimulationWorkId(String value) implements Comparable<SimulationWorkId> {
    public SimulationWorkId { value = SchedulerValidation.requireId(value, "Simulation work id"); }
    public static SimulationWorkId of(String value) { return new SimulationWorkId(value); }
    @Override public int compareTo(SimulationWorkId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
