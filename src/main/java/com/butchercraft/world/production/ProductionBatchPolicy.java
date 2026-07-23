package com.butchercraft.world.production;

public record ProductionBatchPolicy(
        long minimumBatchCount,
        long maximumBatchCount,
        long batchIncrement,
        boolean partialBatchAllowed,
        boolean exactInputScalingRequired
) {
    public ProductionBatchPolicy {
        if (minimumBatchCount <= 0L || maximumBatchCount < minimumBatchCount || batchIncrement <= 0L) {
            throw new IllegalArgumentException("Invalid production batch bounds");
        }
        if (partialBatchAllowed) {
            throw new IllegalArgumentException("Production schema 1 supports whole batches only");
        }
    }

    public static ProductionBatchPolicy wholeBatches(long minimum, long maximum, long increment) {
        return new ProductionBatchPolicy(minimum, maximum, increment, false, true);
    }

    public void validate(long batchCount) {
        if (batchCount < minimumBatchCount || batchCount > maximumBatchCount) {
            throw new IllegalArgumentException("Production batch count is outside the allowed range");
        }
        if (Math.floorMod(batchCount - minimumBatchCount, batchIncrement) != 0L) {
            throw new IllegalArgumentException("Production batch count violates the required increment");
        }
    }
}
