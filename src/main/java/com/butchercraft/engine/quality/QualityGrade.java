package com.butchercraft.engine.quality;

/**
 * Player-facing quality grade derived deterministically from a 0-1000 quality score.
 *
 * <p>These labels are fictional gameplay grades and intentionally avoid real regulatory
 * terminology. The grade boundaries are inclusive.</p>
 */
public enum QualityGrade {
    POOR(0, 199, "Poor"),
    FAIR(200, 399, "Fair"),
    GOOD(400, 699, "Good"),
    EXCELLENT(700, 899, "Excellent"),
    PREMIUM(900, 1000, "Premium");

    private final int minimumScore;
    private final int maximumScore;
    private final String displayName;

    QualityGrade(int minimumScore, int maximumScore, String displayName) {
        this.minimumScore = minimumScore;
        this.maximumScore = maximumScore;
        this.displayName = displayName;
    }

    public int minimumScore() {
        return minimumScore;
    }

    public int maximumScore() {
        return maximumScore;
    }

    public String displayName() {
        return displayName;
    }

    static QualityGrade fromScore(int score) {
        for (QualityGrade grade : values()) {
            if (score >= grade.minimumScore && score <= grade.maximumScore) {
                return grade;
            }
        }
        throw new IllegalArgumentException("Quality score is outside the supported range: " + score);
    }
}
