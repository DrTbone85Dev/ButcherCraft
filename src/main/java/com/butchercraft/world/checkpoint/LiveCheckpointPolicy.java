package com.butchercraft.world.checkpoint;

public final class LiveCheckpointPolicy {
    public static final long PERIODIC_INTERVAL_TICKS = 6_000L;
    public static final int MINIMUM_RETAINED_COMMITTED_GENERATIONS = 3;
    public static final long SHUTDOWN_WAIT_MILLIS = 10_000L;

    private LiveCheckpointPolicy() {
    }
}
