package com.butchercraft.world.business;

import java.util.Objects;

public record BusinessOwnershipModel(
        String ownerDisplayName,
        BusinessOwnershipType ownershipType,
        int recordedSinceYear,
        String notes
) {
    public BusinessOwnershipModel {
        ownerDisplayName = requireNonBlank(ownerDisplayName, "ownerDisplayName");
        ownershipType = Objects.requireNonNull(ownershipType, "ownershipType");
        if (recordedSinceYear < 1850 || recordedSinceYear > 2026) {
            throw new IllegalArgumentException("Business ownership recorded year is outside the supported range: " + recordedSinceYear);
        }
        notes = requireNonBlank(notes, "notes");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business ownership " + fieldName + " must not be blank");
        }
        return value;
    }
}
