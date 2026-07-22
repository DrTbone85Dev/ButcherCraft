package com.butchercraft.world.ownership;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record OwnershipEntity(
        OwnershipEntityId id,
        String displayName,
        OwnershipEntityType type,
        int establishedYear,
        Optional<FamilyId> familyId,
        Optional<PersonId> personId,
        String historicalSummary,
        List<FamilyRelationship> familyRelationships
) {
    public OwnershipEntity {
        id = Objects.requireNonNull(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        type = Objects.requireNonNull(type, "type");
        if (establishedYear < 1850 || establishedYear > 2026) {
            throw new IllegalArgumentException("Ownership entity established year is outside the supported range: " + establishedYear);
        }
        familyId = Objects.requireNonNull(familyId, "familyId");
        personId = Objects.requireNonNull(personId, "personId");
        historicalSummary = requireSummary(historicalSummary);
        familyRelationships = List.copyOf(Objects.requireNonNull(familyRelationships, "familyRelationships"));
        validateTypedReferences(type, familyId, personId);
    }

    private static void validateTypedReferences(
            OwnershipEntityType type,
            Optional<FamilyId> familyId,
            Optional<PersonId> personId
    ) {
        if ((type == OwnershipEntityType.FAMILY || type == OwnershipEntityType.ESTATE) && familyId.isEmpty()) {
            throw new IllegalArgumentException("Family and estate ownership entities must reference a family");
        }
        if (type == OwnershipEntityType.INDIVIDUAL && personId.isEmpty()) {
            throw new IllegalArgumentException("Individual ownership entities must reference a person");
        }
        if (type == OwnershipEntityType.MUNICIPALITY && personId.isPresent()) {
            throw new IllegalArgumentException("Municipal ownership entities must not reference a person");
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
            throw new IllegalArgumentException("Ownership entity historical summary must contain at least two sentences");
        }
        return summary;
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Ownership entity " + fieldName + " must not be blank");
        }
        return value;
    }
}
