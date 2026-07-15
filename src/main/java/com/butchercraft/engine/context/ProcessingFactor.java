package com.butchercraft.engine.context;

/**
 * Immutable bounded processing factor.
 *
 * <p>Factors use an exact 0-1000 scale. For cleanliness and equipment condition, 1000 is neutral
 * and ideal. For operator skill, 500 is neutral. Future Minecraft systems supply these facts; the
 * engine does not determine where they came from.</p>
 */
public record ProcessingFactor(int value) {
    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 1000;
    public static final ProcessingFactor MINIMUM = new ProcessingFactor(MIN_VALUE);
    public static final ProcessingFactor NEUTRAL_SKILL = new ProcessingFactor(500);
    public static final ProcessingFactor IDEAL = new ProcessingFactor(MAX_VALUE);

    public ProcessingFactor {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException("Processing factor must be between 0 and 1000: " + value);
        }
    }

    public static ProcessingFactor of(int value) {
        return new ProcessingFactor(value);
    }
}
