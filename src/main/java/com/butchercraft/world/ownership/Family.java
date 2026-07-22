package com.butchercraft.world.ownership;

import java.util.Objects;

public record Family(
        FamilyId id,
        String surname,
        String foundingSettlementId,
        String historicalSummary,
        int legacyScore,
        FamilyReputation reputation,
        int approximateFoundingYear
) {
    public Family {
        id = Objects.requireNonNull(id, "id");
        surname = requireNonBlank(surname, "surname");
        foundingSettlementId = requireNonBlank(foundingSettlementId, "foundingSettlementId");
        historicalSummary = requireSummary(historicalSummary);
        if (legacyScore < 0 || legacyScore > 100) {
            throw new IllegalArgumentException("Family legacy score must be between 0 and 100: " + legacyScore);
        }
        reputation = Objects.requireNonNull(reputation, "reputation");
        if (approximateFoundingYear < 1850 || approximateFoundingYear > 2026) {
            throw new IllegalArgumentException("Family founding year is outside the supported range: " + approximateFoundingYear);
        }
    }

    private static String requireSummary(String value) {
        String summary = requireNonBlank(value, "historicalSummary");
        int sentences = 0;
        for (String part : summary.split("[.!?]")) {
            if (!part.isBlank()) {
                sentences++;
            }
        }
        if (sentences < 2) {
            throw new IllegalArgumentException("Family historical summary must contain at least two sentences");
        }
        return summary;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Family " + fieldName + " must not be blank");
        }
        return value;
    }
}
