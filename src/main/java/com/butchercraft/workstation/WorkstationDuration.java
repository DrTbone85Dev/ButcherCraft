package com.butchercraft.workstation;

public final class WorkstationDuration {
    public static final long MILLIS_PER_TICK = 50L;

    private WorkstationDuration() {
    }

    public static int millisecondsToTicks(long milliseconds) {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Processing duration must be positive milliseconds");
        }
        long ticks;
        try {
            ticks = Math.addExact(milliseconds, MILLIS_PER_TICK - 1L) / MILLIS_PER_TICK;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Processing duration is too large for workstation ticking: " + milliseconds, exception);
        }
        if (ticks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Processing duration is too large for workstation ticking: " + milliseconds);
        }
        return (int) ticks;
    }
}
