package com.butchercraft.world.manufacturer;

import java.util.Objects;

public record Headquarters(
        String regionId,
        String localityName
) {
    public Headquarters {
        regionId = requireNonBlank(regionId, "regionId");
        localityName = requireNonBlank(localityName, "localityName");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Headquarters " + fieldName + " must not be blank");
        }
        return value;
    }
}
