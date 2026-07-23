package com.butchercraft.world.planning;
public record ApprovedPlanId(String value) implements Comparable<ApprovedPlanId> {
    public ApprovedPlanId { value = PlanningValidation.id(value, "Approved plan id"); }
    public static ApprovedPlanId of(String value) { return new ApprovedPlanId(value); }
    @Override public int compareTo(ApprovedPlanId other) { return value.compareTo(other.value); }
}
