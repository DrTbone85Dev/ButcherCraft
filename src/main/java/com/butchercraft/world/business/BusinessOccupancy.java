package com.butchercraft.world.business;

import com.butchercraft.world.property.CommercialPropertyId;

import java.util.Objects;
import java.util.OptionalInt;

public record BusinessOccupancy(
        CommercialPropertyId propertyId,
        int startYear,
        OptionalInt endYear,
        BusinessOccupancyReason reason,
        String notes
) {
    private static final int MIN_YEAR = 1850;
    private static final int MAX_YEAR = 2026;

    public BusinessOccupancy {
        propertyId = Objects.requireNonNull(propertyId, "propertyId");
        if (startYear < MIN_YEAR || startYear > MAX_YEAR) {
            throw new IllegalArgumentException("Business occupancy start year is outside the supported range: " + startYear);
        }
        endYear = Objects.requireNonNull(endYear, "endYear");
        if (endYear.isPresent()) {
            int value = endYear.getAsInt();
            if (value < startYear || value > MAX_YEAR) {
                throw new IllegalArgumentException("Business occupancy end year is outside the supported range: " + value);
            }
        }
        reason = Objects.requireNonNull(reason, "reason");
        notes = requireNonBlank(notes, "notes");
    }

    public boolean isCurrent() {
        return endYear.isEmpty();
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business occupancy " + fieldName + " must not be blank");
        }
        return value;
    }
}
