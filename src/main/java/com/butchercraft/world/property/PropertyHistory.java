package com.butchercraft.world.property;

import java.util.List;
import java.util.Objects;

public record PropertyHistory(
        String historicalSummary,
        List<OwnershipRecord> ownershipHistory
) {
    public PropertyHistory {
        historicalSummary = requireNonBlank(historicalSummary, "historicalSummary");
        ownershipHistory = List.copyOf(Objects.requireNonNull(ownershipHistory, "ownershipHistory"));
        if (ownershipHistory.isEmpty()) {
            throw new IllegalArgumentException("Property ownership history must not be empty");
        }
        int previousEndYear = 0;
        boolean currentRecordSeen = false;
        for (int index = 0; index < ownershipHistory.size(); index++) {
            OwnershipRecord record = Objects.requireNonNull(ownershipHistory.get(index), "ownershipRecord");
            if (record.startYear() <= previousEndYear) {
                throw new IllegalArgumentException("Property ownership history must be chronological and non-overlapping");
            }
            if (record.isCurrent()) {
                if (index != ownershipHistory.size() - 1) {
                    throw new IllegalArgumentException("Only the final ownership record may be current");
                }
                currentRecordSeen = true;
            } else {
                previousEndYear = record.endYear().getAsInt();
            }
        }
        if (!currentRecordSeen) {
            throw new IllegalArgumentException("Property ownership history must include a current ownership record");
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Property history " + fieldName + " must not be blank");
        }
        return value;
    }
}
