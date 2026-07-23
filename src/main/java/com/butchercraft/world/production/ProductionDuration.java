package com.butchercraft.world.production;

public record ProductionDuration(long baseDurationTicks, long progressQuantumTicks) {
    public ProductionDuration {
        if (baseDurationTicks <= 0L) {
            throw new IllegalArgumentException("Production duration must be positive");
        }
        if (progressQuantumTicks <= 0L || progressQuantumTicks > baseDurationTicks) {
            throw new IllegalArgumentException("Production progress quantum must be positive and within duration");
        }
    }

    public static ProductionDuration ofTicks(long ticks) {
        return new ProductionDuration(ticks, ticks);
    }

    public long requiredWorkUnits(long batchCount) {
        if (batchCount <= 0L) {
            throw new IllegalArgumentException("Production batch count must be positive");
        }
        return Math.multiplyExact(baseDurationTicks, batchCount);
    }
}
