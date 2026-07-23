package com.butchercraft.world.planning;
public record ConstraintId(String value) implements Comparable<ConstraintId> {
    public ConstraintId { value = PlanningValidation.id(value, "Constraint id"); }
    public static ConstraintId of(String value) { return new ConstraintId(value); }
    @Override public int compareTo(ConstraintId other) { return value.compareTo(other.value); }
}
