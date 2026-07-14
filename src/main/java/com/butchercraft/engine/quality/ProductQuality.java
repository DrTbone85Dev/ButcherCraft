package com.butchercraft.engine.quality;

/**
 * Immutable quality value object for engine processing.
 *
 * <p>The stored score must be between 0 and 1000. Direct construction rejects out-of-range
 * values so invalid product data is found early. Operational adjustments clamp after applying
 * documented causes, preserving a valid product quality without hidden randomness. This type is
 * Minecraft-independent and can be stored or synchronized by integration code later.</p>
 */
public record ProductQuality(int score) {
    public static final int MIN_SCORE = 0;
    public static final int MAX_SCORE = 1000;

    public ProductQuality {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("Quality score must be between 0 and 1000: " + score);
        }
    }

    public static ProductQuality ofScore(int score) {
        return new ProductQuality(score);
    }

    public QualityGrade grade() {
        return QualityGrade.fromScore(score);
    }

    /**
     * Applies a documented quality delta and clamps the result into the valid 0-1000 range.
     *
     * @param delta deterministic signed adjustment
     * @return adjusted immutable quality
     */
    public ProductQuality adjustedByClamped(int delta) {
        long candidate = (long) score + delta;
        long clamped = Math.max(MIN_SCORE, Math.min(MAX_SCORE, candidate));
        return new ProductQuality((int) clamped);
    }
}
