package com.butchercraft.world.ownership;

import com.butchercraft.world.business.BusinessId;

import java.util.Objects;
import java.util.OptionalInt;

public record OwnershipRecord(
        OwnershipEntityId ownershipEntityId,
        BusinessId businessId,
        OwnershipShare ownershipShare,
        int startYear,
        OptionalInt endYear,
        OwnershipAcquisitionMethod acquisitionMethod,
        String notes
) {
    public OwnershipRecord {
        ownershipEntityId = Objects.requireNonNull(ownershipEntityId, "ownershipEntityId");
        businessId = Objects.requireNonNull(businessId, "businessId");
        ownershipShare = Objects.requireNonNull(ownershipShare, "ownershipShare");
        if (startYear < 1850 || startYear > 2026) {
            throw new IllegalArgumentException("Ownership record start year is outside the supported range: " + startYear);
        }
        endYear = Objects.requireNonNull(endYear, "endYear");
        if (endYear.isPresent()) {
            int value = endYear.getAsInt();
            if (value < startYear || value > 2026) {
                throw new IllegalArgumentException("Ownership record end year is outside the supported range: " + value);
            }
        }
        acquisitionMethod = Objects.requireNonNull(acquisitionMethod, "acquisitionMethod");
        notes = requireNonBlank(notes, "notes");
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
