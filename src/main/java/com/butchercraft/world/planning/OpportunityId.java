package com.butchercraft.world.planning;
public record OpportunityId(String value) implements Comparable<OpportunityId> {
    public OpportunityId { value = PlanningValidation.id(value, "Opportunity id"); }
    public static OpportunityId of(String value) { return new OpportunityId(value); }
    @Override public int compareTo(OpportunityId other) { return value.compareTo(other.value); }
}
