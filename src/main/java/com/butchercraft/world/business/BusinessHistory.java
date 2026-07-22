package com.butchercraft.world.business;

import com.butchercraft.world.property.CommercialPropertyId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public record BusinessHistory(
        String historicalSummary,
        List<BusinessOccupancy> occupancyHistory
) {
    public BusinessHistory {
        historicalSummary = requireSummary(historicalSummary);
        occupancyHistory = List.copyOf(Objects.requireNonNull(occupancyHistory, "occupancyHistory"));
        if (occupancyHistory.isEmpty()) {
            throw new IllegalArgumentException("Business occupancy history must not be empty");
        }
        validateTimeline(occupancyHistory);
    }

    private static void validateTimeline(List<BusinessOccupancy> occupancies) {
        Map<CommercialPropertyId, List<BusinessOccupancy>> byProperty = occupancies.stream()
                .collect(Collectors.groupingBy(BusinessOccupancy::propertyId));
        for (Map.Entry<CommercialPropertyId, List<BusinessOccupancy>> entry : byProperty.entrySet()) {
            List<BusinessOccupancy> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(BusinessOccupancy::startYear))
                    .toList();
            OptionalInt previousEnd = OptionalInt.empty();
            for (int index = 0; index < sorted.size(); index++) {
                BusinessOccupancy occupancy = sorted.get(index);
                if (index > 0 && previousEnd.isEmpty()) {
                    throw new IllegalArgumentException("Business occupancy history has a current record before the final record");
                }
                if (previousEnd.isPresent() && occupancy.startYear() <= previousEnd.getAsInt()) {
                    throw new IllegalArgumentException("Business occupancy history overlaps for property " + entry.getKey().value());
                }
                previousEnd = occupancy.endYear();
            }
        }
    }

    private static String requireSummary(String value) {
        Objects.requireNonNull(value, "historicalSummary");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Business historical summary must not be blank");
        }
        int sentences = 0;
        for (String part : value.split("[.!?]")) {
            if (!part.isBlank()) {
                sentences++;
            }
        }
        if (sentences < 2) {
            throw new IllegalArgumentException("Business historical summary must contain at least two sentences");
        }
        return value;
    }
}
