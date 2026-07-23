package com.butchercraft.world.planning;
public record NeedId(String value) implements Comparable<NeedId> {
    public NeedId { value = PlanningValidation.id(value, "Need id"); }
    public static NeedId of(String value) { return new NeedId(value); }
    @Override public int compareTo(NeedId other) { return value.compareTo(other.value); }
}
