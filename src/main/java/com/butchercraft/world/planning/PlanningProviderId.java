package com.butchercraft.world.planning;
public record PlanningProviderId(String value) implements Comparable<PlanningProviderId> {
    public PlanningProviderId { value = PlanningValidation.id(value, "Planning provider id"); }
    public static PlanningProviderId of(String value) { return new PlanningProviderId(value); }
    @Override public int compareTo(PlanningProviderId other) { return value.compareTo(other.value); }
}
