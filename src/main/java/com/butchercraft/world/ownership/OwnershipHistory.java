package com.butchercraft.world.ownership;

import com.butchercraft.world.business.BusinessId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public record OwnershipHistory(
        BusinessId businessId,
        List<OwnershipRecord> ownershipRecords
) {
    public OwnershipHistory {
        businessId = Objects.requireNonNull(businessId, "businessId");
        ownershipRecords = List.copyOf(Objects.requireNonNull(ownershipRecords, "ownershipRecords"));
        if (ownershipRecords.isEmpty()) {
            throw new IllegalArgumentException("Ownership history must not be empty");
        }
        for (OwnershipRecord record : ownershipRecords) {
            if (!record.businessId().equals(businessId)) {
                throw new IllegalArgumentException("Ownership history contains a record for a different business");
            }
        }
        validateEntityTimelines(ownershipRecords);
        validateShareTotals(ownershipRecords);
    }

    private static void validateEntityTimelines(List<OwnershipRecord> records) {
        Map<OwnershipEntityId, List<OwnershipRecord>> byEntity = records.stream()
                .collect(Collectors.groupingBy(OwnershipRecord::ownershipEntityId));
        for (Map.Entry<OwnershipEntityId, List<OwnershipRecord>> entry : byEntity.entrySet()) {
            List<OwnershipRecord> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(OwnershipRecord::startYear))
                    .toList();
            OptionalInt previousEnd = OptionalInt.empty();
            for (int index = 0; index < sorted.size(); index++) {
                OwnershipRecord record = sorted.get(index);
                if (index > 0 && previousEnd.isEmpty()) {
                    throw new IllegalArgumentException("Ownership history has a current record before the final record");
                }
                if (previousEnd.isPresent() && record.startYear() <= previousEnd.getAsInt()) {
                    throw new IllegalArgumentException("Ownership history overlaps for ownership entity " + entry.getKey().value());
                }
                previousEnd = record.endYear();
            }
        }
    }

    private static void validateShareTotals(List<OwnershipRecord> records) {
        List<Integer> boundaryYears = records.stream()
                .flatMap(record -> record.endYear().isPresent()
                        ? List.of(record.startYear(), record.endYear().getAsInt() + 1).stream()
                        : List.of(record.startYear()).stream())
                .distinct()
                .sorted()
                .toList();
        for (int year : boundaryYears) {
            int total = records.stream()
                    .filter(record -> record.startYear() <= year)
                    .filter(record -> record.endYear().isEmpty() || record.endYear().getAsInt() >= year)
                    .mapToInt(record -> record.ownershipShare().basisPoints())
                    .sum();
            if (total > OwnershipShare.FULL_OWNERSHIP) {
                throw new IllegalArgumentException("Ownership share exceeds 100 percent for year " + year);
            }
        }
    }
}
