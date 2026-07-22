package com.butchercraft.world.property;

import java.util.Objects;
import java.util.OptionalInt;

public record OwnershipRecord(
        String ownerName,
        int startYear,
        OptionalInt endYear,
        PropertyAcquisitionMethod acquisitionMethod,
        String historicalNotes
) {
    public OwnershipRecord {
        ownerName = requireNonBlank(ownerName, "ownerName");
        if (startYear < 1850 || startYear > 2026) {
            throw new IllegalArgumentException("Ownership start year is outside the supported range: " + startYear);
        }
        endYear = Objects.requireNonNull(endYear, "endYear");
        if (endYear.isPresent() && endYear.getAsInt() < startYear) {
            throw new IllegalArgumentException("Ownership end year must not be before the start year");
        }
        acquisitionMethod = Objects.requireNonNull(acquisitionMethod, "acquisitionMethod");
        historicalNotes = requireNonBlank(historicalNotes, "historicalNotes");
    }

    public boolean isCurrent() {
        return endYear.isEmpty();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Ownership record " + fieldName + " must not be blank");
        }
        return value;
    }
}
