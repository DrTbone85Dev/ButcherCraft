package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SimulationWorkTypeId(String value) implements Comparable<SimulationWorkTypeId> {
    public SimulationWorkTypeId { value = SchedulerValidation.requireId(value, "Simulation work type id"); }
    public static SimulationWorkTypeId of(String value) { return new SimulationWorkTypeId(value); }
    @Override public int compareTo(SimulationWorkTypeId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
