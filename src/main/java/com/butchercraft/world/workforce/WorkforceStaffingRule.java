package com.butchercraft.world.workforce;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record WorkforceStaffingRule(
        List<PositionId> requiredPositions,
        List<PositionId> optionalPositions,
        int minimumStaffing,
        int maximumStaffing
) {
    public WorkforceStaffingRule {
        requiredPositions = copyPositionIds(requiredPositions, "requiredPositions");
        optionalPositions = copyPositionIds(optionalPositions, "optionalPositions");
        if (minimumStaffing < 0) {
            throw new IllegalArgumentException("Workforce minimum staffing must not be negative: " + minimumStaffing);
        }
        if (maximumStaffing < 0) {
            throw new IllegalArgumentException("Workforce maximum staffing must not be negative: " + maximumStaffing);
        }
        if (minimumStaffing > maximumStaffing) {
            throw new IllegalArgumentException("Workforce minimum staffing must not exceed maximum staffing");
        }
        Set<PositionId> overlap = new LinkedHashSet<>(requiredPositions);
        overlap.retainAll(optionalPositions);
        if (!overlap.isEmpty()) {
            throw new IllegalArgumentException("Workforce positions cannot be both required and optional: " + overlap);
        }
        if (requiredPositions.isEmpty() && minimumStaffing > 0) {
            throw new IllegalArgumentException("Workforce minimum staffing requires at least one required position");
        }
    }

    private static List<PositionId> copyPositionIds(List<PositionId> positionIds, String fieldName) {
        Objects.requireNonNull(positionIds, fieldName);
        Set<PositionId> copied = new LinkedHashSet<>();
        for (PositionId positionId : positionIds) {
            copied.add(Objects.requireNonNull(positionId, "positionId"));
        }
        if (copied.size() != positionIds.size()) {
            throw new IllegalArgumentException("Workforce " + fieldName + " must not contain duplicates");
        }
        return List.copyOf(copied);
    }
}
