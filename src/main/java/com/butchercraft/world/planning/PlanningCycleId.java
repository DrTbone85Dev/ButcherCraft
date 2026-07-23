package com.butchercraft.world.planning;
public record PlanningCycleId(String value) implements Comparable<PlanningCycleId> {
    public PlanningCycleId { value = PlanningValidation.id(value, "Planning cycle id"); }
    public static PlanningCycleId of(String value) { return new PlanningCycleId(value); }
    public static PlanningCycleId forTick(long tick) {
        return of("butchercraft:planning_cycle/tick_" + PlanningValidation.tick(tick));
    }
    @Override public int compareTo(PlanningCycleId other) { return value.compareTo(other.value); }
}
