package com.butchercraft.world.planning;
public record PlanningSubmissionAdapterId(String value) implements Comparable<PlanningSubmissionAdapterId> {
    public PlanningSubmissionAdapterId { value = PlanningValidation.id(value, "Planning adapter id"); }
    public static PlanningSubmissionAdapterId of(String value) { return new PlanningSubmissionAdapterId(value); }
    @Override public int compareTo(PlanningSubmissionAdapterId other) { return value.compareTo(other.value); }
}
