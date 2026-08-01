package com.butchercraft.world.simulation.time;

import java.util.Objects;

public record WorldTimeAdvance(long dayTimeUnits, WorldTimeAccumulator accumulator) {
    public WorldTimeAdvance {
        if (dayTimeUnits < 0L) {
            throw new IllegalArgumentException("Day-time advancement must not be negative: " + dayTimeUnits);
        }
        accumulator = Objects.requireNonNull(accumulator, "accumulator");
    }
}
