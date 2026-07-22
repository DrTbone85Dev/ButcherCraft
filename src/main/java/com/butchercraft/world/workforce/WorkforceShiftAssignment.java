package com.butchercraft.world.workforce;

import java.util.Objects;

public record WorkforceShiftAssignment(
        String shiftId,
        PositionId positionId,
        int minimumWorkers,
        int maximumWorkers
) {
    public WorkforceShiftAssignment {
        shiftId = WorkforcePosition.requireShiftId(shiftId);
        positionId = Objects.requireNonNull(positionId, "positionId");
        if (minimumWorkers < 0) {
            throw new IllegalArgumentException("Workforce assignment minimum workers must not be negative: " + positionId.value());
        }
        if (maximumWorkers < 0) {
            throw new IllegalArgumentException("Workforce assignment maximum workers must not be negative: " + positionId.value());
        }
        if (minimumWorkers > maximumWorkers) {
            throw new IllegalArgumentException("Workforce assignment minimum workers must not exceed maximum workers: "
                    + positionId.value());
        }
    }
}
