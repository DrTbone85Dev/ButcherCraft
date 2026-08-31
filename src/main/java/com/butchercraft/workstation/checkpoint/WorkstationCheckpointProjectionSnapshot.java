package com.butchercraft.workstation.checkpoint;

import java.util.List;
import java.util.Objects;

public record WorkstationCheckpointProjectionSnapshot(
        String json,
        WorkstationCheckpointCompletenessStatus status,
        int requiredProjectionCount,
        int availableProjectionCount,
        int loadedProjectionCount,
        int unloadedProjectionCount,
        long participantBytes,
        long requiredSetDurationNanos,
        long projectionFreezeDurationNanos,
        long serializationDurationNanos,
        List<WorkstationCheckpointBlocker> blockers
) {
    public WorkstationCheckpointProjectionSnapshot {
        json = Objects.requireNonNull(json, "json");
        status = Objects.requireNonNull(status, "status");
        if (requiredProjectionCount < 0 || availableProjectionCount < 0
                || loadedProjectionCount < 0 || unloadedProjectionCount < 0) {
            throw new IllegalArgumentException("Workstation checkpoint projection counts must not be negative");
        }
        if (availableProjectionCount != loadedProjectionCount + unloadedProjectionCount
                || availableProjectionCount > requiredProjectionCount) {
            throw new IllegalArgumentException("Workstation checkpoint projection counts are inconsistent");
        }
        if (participantBytes < 0L || requiredSetDurationNanos < 0L
                || projectionFreezeDurationNanos < 0L || serializationDurationNanos < 0L) {
            throw new IllegalArgumentException("Workstation checkpoint projection metrics must not be negative");
        }
        blockers = Objects.requireNonNull(blockers, "blockers").stream().sorted().toList();
        if ((status == WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE) != blockers.isEmpty()) {
            throw new IllegalArgumentException("Workstation checkpoint completeness must agree with blockers");
        }
    }

    public boolean restorable() {
        return status == WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE;
    }
}
