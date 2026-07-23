package com.butchercraft.world.planning;
public record ObservationId(String value) implements Comparable<ObservationId> {
    public ObservationId { value = PlanningValidation.id(value, "Observation id"); }
    public static ObservationId of(String value) { return new ObservationId(value); }
    @Override public int compareTo(ObservationId other) { return value.compareTo(other.value); }
}
