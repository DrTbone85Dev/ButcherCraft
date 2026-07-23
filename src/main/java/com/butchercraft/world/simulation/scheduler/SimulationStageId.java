package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

public record SimulationStageId(String value) implements Comparable<SimulationStageId> {
    public SimulationStageId { value = SchedulerValidation.requireId(value, "Simulation stage id"); }
    public static SimulationStageId of(String value) { return new SimulationStageId(value); }
    @Override public int compareTo(SimulationStageId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
