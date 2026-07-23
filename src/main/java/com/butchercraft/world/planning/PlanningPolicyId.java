package com.butchercraft.world.planning;
public record PlanningPolicyId(String value) implements Comparable<PlanningPolicyId> {
    public PlanningPolicyId { value = PlanningValidation.id(value, "Planning policy id"); }
    public static PlanningPolicyId of(String value) { return new PlanningPolicyId(value); }
    @Override public int compareTo(PlanningPolicyId other) { return value.compareTo(other.value); }
}
