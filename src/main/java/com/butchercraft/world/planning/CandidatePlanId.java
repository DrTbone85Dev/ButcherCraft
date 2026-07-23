package com.butchercraft.world.planning;
public record CandidatePlanId(String value) implements Comparable<CandidatePlanId> {
    public CandidatePlanId { value = PlanningValidation.id(value, "Candidate plan id"); }
    public static CandidatePlanId of(String value) { return new CandidatePlanId(value); }
    @Override public int compareTo(CandidatePlanId other) { return value.compareTo(other.value); }
}
