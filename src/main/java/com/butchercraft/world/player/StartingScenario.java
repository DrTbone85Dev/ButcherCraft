package com.butchercraft.world.player;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record StartingScenario(
        StartingScenarioId id,
        String displayName,
        StartingScenarioType scenarioType,
        List<CareerProfile> careerProfiles,
        String startingSettlementReference,
        InitialReputation initialReputation,
        StartingAssets startingAssets,
        Optional<InheritanceRecord> inheritanceRecord,
        List<StartingRelationship> startingRelationships,
        LegacyProgress initialLegacyProgress,
        String scenarioSummary,
        String backgroundSummary
) {
    public StartingScenario {
        id = Objects.requireNonNull(id, "id");
        displayName = PlayerValidation.requireNonBlank(displayName, "starting scenario displayName");
        scenarioType = Objects.requireNonNull(scenarioType, "scenarioType");
        careerProfiles = PlayerValidation.copyNonEmptyDistinct(careerProfiles, "starting scenario careerProfiles");
        startingSettlementReference = PlayerValidation.requireNonBlank(startingSettlementReference, "starting scenario startingSettlementReference");
        initialReputation = Objects.requireNonNull(initialReputation, "initialReputation");
        startingAssets = Objects.requireNonNull(startingAssets, "startingAssets");
        inheritanceRecord = Objects.requireNonNull(inheritanceRecord, "inheritanceRecord");
        if (scenarioType.inheritanceRecordRequired() && inheritanceRecord.isEmpty()) {
            throw new IllegalArgumentException("Starting scenario requires an inheritance record: " + id.value());
        }
        startingRelationships = PlayerValidation.copyNonEmptyDistinct(startingRelationships, "starting scenario startingRelationships");
        initialLegacyProgress = Objects.requireNonNull(initialLegacyProgress, "initialLegacyProgress");
        scenarioSummary = PlayerValidation.requireNonBlank(scenarioSummary, "starting scenario scenarioSummary");
        backgroundSummary = PlayerValidation.requireNonBlank(backgroundSummary, "starting scenario backgroundSummary");
    }
}
