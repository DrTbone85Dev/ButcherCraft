package com.butchercraft.world.allocation;

public record AllocationCycleId(String value) implements Comparable<AllocationCycleId> {
    public AllocationCycleId {
        value = AllocationValidation.id(value, "allocationCycleId");
    }

    public static AllocationCycleId of(String value) {
        return new AllocationCycleId(value);
    }

    public static AllocationCycleId forTick(long tick) {
        return of("butchercraft:allocation_cycle/tick_" + AllocationValidation.tick(tick, "tick"));
    }

    @Override
    public int compareTo(AllocationCycleId other) {
        return value.compareTo(other.value);
    }
}
