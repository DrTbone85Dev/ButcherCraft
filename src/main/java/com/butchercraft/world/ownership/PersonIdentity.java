package com.butchercraft.world.ownership;

import java.util.Objects;
import java.util.OptionalInt;

public record PersonIdentity(
        PersonId id,
        String fullName,
        int birthYear,
        OptionalInt deathYear,
        FamilyId primaryFamilyId,
        String historicalSummary
) {
    public PersonIdentity {
        id = Objects.requireNonNull(id, "id");
        fullName = requireNonBlank(fullName, "fullName");
        if (birthYear < 1850 || birthYear > 2026) {
            throw new IllegalArgumentException("Person birth year is outside the supported range: " + birthYear);
        }
        deathYear = Objects.requireNonNull(deathYear, "deathYear");
        if (deathYear.isPresent()) {
            int value = deathYear.getAsInt();
            if (value < birthYear || value > 2026) {
                throw new IllegalArgumentException("Person death year is outside the supported range: " + value);
            }
        }
        primaryFamilyId = Objects.requireNonNull(primaryFamilyId, "primaryFamilyId");
        historicalSummary = requireSummary(historicalSummary);
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
            throw new IllegalArgumentException("Person historical summary must contain at least two sentences");
        }
        return summary;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Person " + fieldName + " must not be blank");
        }
        return value;
    }
}
