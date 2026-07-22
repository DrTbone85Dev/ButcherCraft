package com.butchercraft.world.player;

import java.util.List;
import java.util.Objects;

public record PlayerIdentity(
        PlayerIdentityId id,
        String fullNamePlaceholder,
        CareerProfile careerProfile,
        StartingScenarioId startingScenarioId,
        String startingSettlementId,
        String legacySummary,
        PlayerBackground background,
        InitialReputation initialReputation,
        List<StartingRelationship> initialRelationships
) {
    public PlayerIdentity {
        id = Objects.requireNonNull(id, "id");
        fullNamePlaceholder = PlayerValidation.requireNonBlank(fullNamePlaceholder, "player identity fullNamePlaceholder");
        careerProfile = Objects.requireNonNull(careerProfile, "careerProfile");
        startingScenarioId = Objects.requireNonNull(startingScenarioId, "startingScenarioId");
        startingSettlementId = PlayerValidation.requireNonBlank(startingSettlementId, "player identity startingSettlementId");
        legacySummary = PlayerValidation.requireNonBlank(legacySummary, "player identity legacySummary");
        background = Objects.requireNonNull(background, "background");
        initialReputation = Objects.requireNonNull(initialReputation, "initialReputation");
        initialRelationships = PlayerValidation.copyNonEmptyDistinct(initialRelationships, "player identity initialRelationships");
    }
}
