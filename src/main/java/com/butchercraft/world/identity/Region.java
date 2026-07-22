package com.butchercraft.world.identity;

import java.util.Objects;

public record Region(
        String id,
        String displayName,
        String agriculturalIdentity,
        String economicIdentity,
        String namingConvention
) {
    public Region {
        id = requireNonBlank(id, "id");
        displayName = requireNonBlank(displayName, "displayName");
        agriculturalIdentity = requireNonBlank(agriculturalIdentity, "agriculturalIdentity");
        economicIdentity = requireNonBlank(economicIdentity, "economicIdentity");
        namingConvention = requireNonBlank(namingConvention, "namingConvention");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Region " + fieldName + " must not be blank");
        }
        return value;
    }
}
