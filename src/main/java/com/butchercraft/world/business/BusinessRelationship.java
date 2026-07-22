package com.butchercraft.world.business;

import java.util.Objects;

public record BusinessRelationship(
        BusinessId relatedBusinessId,
        BusinessRelationshipType relationshipType,
        String notes
) {
    public BusinessRelationship {
        relatedBusinessId = Objects.requireNonNull(relatedBusinessId, "relatedBusinessId");
        relationshipType = Objects.requireNonNull(relationshipType, "relationshipType");
        notes = requireNonBlank(notes, "notes");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business relationship " + fieldName + " must not be blank");
        }
        return value;
    }
}
