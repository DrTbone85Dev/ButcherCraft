package com.butchercraft.world.identity;

import java.util.Objects;

public record RegionDefinition(
        String id,
        String displayName,
        String description,
        String agriculturalTendencies,
        String economicProfile,
        String culturalProfile,
        String namingProfileId
) {
    public RegionDefinition {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        description = requireNonBlank(description, "description");
        agriculturalTendencies = requireNonBlank(agriculturalTendencies, "agriculturalTendencies");
        economicProfile = requireNonBlank(economicProfile, "economicProfile");
        culturalProfile = requireNonBlank(culturalProfile, "culturalProfile");
        namingProfileId = requireNonBlank(namingProfileId, "namingProfileId");
    }

    public Region toRegion() {
        return new Region(
                id,
                displayName,
                description,
                agriculturalTendencies,
                economicProfile,
                culturalProfile,
                namingProfileId
        );
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Region definition " + fieldName + " must not be blank");
        }
        return value;
    }
}
