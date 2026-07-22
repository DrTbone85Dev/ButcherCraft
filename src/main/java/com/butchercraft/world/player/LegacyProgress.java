package com.butchercraft.world.player;

import java.util.List;
import java.util.Objects;

public record LegacyProgress(
        int generationsOperated,
        int businessesOwned,
        List<String> historicalMilestones,
        InitialReputation communityReputation,
        String legacySummary
) {
    public LegacyProgress {
        generationsOperated = PlayerValidation.requireNonNegative(generationsOperated, "legacy progress generationsOperated");
        businessesOwned = PlayerValidation.requireNonNegative(businessesOwned, "legacy progress businessesOwned");
        historicalMilestones = PlayerValidation.copyNonEmptyDistinct(historicalMilestones, "legacy progress historicalMilestones");
        communityReputation = Objects.requireNonNull(communityReputation, "communityReputation");
        legacySummary = PlayerValidation.requireNonBlank(legacySummary, "legacy progress legacySummary");
    }
}
