package com.butchercraft.engine.operation;

/**
 * Immutable exact duration for a processing operation.
 *
 * <p>The engine stores milliseconds and does not assume Minecraft ticks. Future Minecraft
 * integration can convert game ticks to milliseconds at the boundary, commonly 50 milliseconds
 * per tick for vanilla timing, without making the domain model depend on Minecraft.</p>
 */
public record ProcessingDuration(long milliseconds) {
    public ProcessingDuration {
        if (milliseconds <= 0) {
            throw new IllegalArgumentException("Processing duration must be positive milliseconds");
        }
    }

    public static ProcessingDuration milliseconds(long milliseconds) {
        return new ProcessingDuration(milliseconds);
    }
}
