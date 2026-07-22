package com.butchercraft.world.identity;

import java.util.Objects;

public record Region(
        String id,
        String displayName,
        String description,
        String agriculturalIdentity,
        String economicIdentity,
        String culturalIdentity,
        String namingProfileId
) {
    public Region {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        description = requireNonBlank(description, "description");
        agriculturalIdentity = requireNonBlank(agriculturalIdentity, "agriculturalIdentity");
        economicIdentity = requireNonBlank(economicIdentity, "economicIdentity");
        culturalIdentity = requireNonBlank(culturalIdentity, "culturalIdentity");
        namingProfileId = requireNonBlank(namingProfileId, "namingProfileId");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Region " + fieldName + " must not be blank");
        }
        return value;
    }
}
