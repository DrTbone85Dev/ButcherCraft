package com.butchercraft.world.ownership;

import java.util.Objects;

public record FamilyRelationship(
        FamilyId relatedFamilyId,
        FamilyRelationshipType relationshipType,
        String notes
) {
    public FamilyRelationship {
        relatedFamilyId = Objects.requireNonNull(relatedFamilyId, "relatedFamilyId");
        relationshipType = Objects.requireNonNull(relationshipType, "relationshipType");
        notes = requireNonBlank(notes, "notes");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Family relationship " + fieldName + " must not be blank");
        }
        return value;
    }
}
