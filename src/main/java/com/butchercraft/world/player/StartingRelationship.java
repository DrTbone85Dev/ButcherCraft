package com.butchercraft.world.player;

import java.util.Objects;

public record StartingRelationship(
        StartingRelationshipType relationshipType,
        String reference,
        String historicalNotes
) {
    public StartingRelationship {
        relationshipType = Objects.requireNonNull(relationshipType, "relationshipType");
        reference = PlayerValidation.requireNonBlank(reference, "starting relationship reference");
        historicalNotes = PlayerValidation.requireNonBlank(historicalNotes, "starting relationship historicalNotes");
    }
}
