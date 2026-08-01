package com.butchercraft.world.simulation.time;

import java.util.Objects;

public record WorldTimeAccumulator(WorldTimeScale scale, long remainderNumerator) {
    public WorldTimeAccumulator {
        scale = Objects.requireNonNull(scale, "scale");
        if (remainderNumerator < 0L || remainderNumerator >= scale.denominator()) {
            throw new IllegalArgumentException("Accumulator remainder is out of range: " + remainderNumerator);
        }
    }

    public static WorldTimeAccumulator empty(WorldTimeScale scale) {
        return new WorldTimeAccumulator(scale, 0L);
    }

    public WorldTimeAdvance advanceOneServerTick() {
        long total = Math.addExact(remainderNumerator, scale.numerator());
        long dayTimeUnits = total / scale.denominator();
        long nextRemainder = total % scale.denominator();
        return new WorldTimeAdvance(dayTimeUnits, new WorldTimeAccumulator(scale, nextRemainder));
    }

    public WorldTimeAccumulator normalizeTo(WorldTimeScale newScale) {
        Objects.requireNonNull(newScale, "newScale");
        return new WorldTimeAccumulator(newScale, Math.floorMod(remainderNumerator, newScale.denominator()));
    }
}
