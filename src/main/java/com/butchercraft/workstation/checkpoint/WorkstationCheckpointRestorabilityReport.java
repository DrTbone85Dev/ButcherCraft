package com.butchercraft.workstation.checkpoint;

import java.util.List;
import java.util.Objects;

public record WorkstationCheckpointRestorabilityReport(
        WorkstationCheckpointCompletenessStatus status,
        int requiredProjectionCount,
        int availableProjectionCount,
        int loadedProjectionCount,
        int unloadedProjectionCount,
        long participantBytes,
        List<String> blockers
) {
    public WorkstationCheckpointRestorabilityReport {
        status = Objects.requireNonNull(status, "status");
        if (requiredProjectionCount < 0 || availableProjectionCount < 0
                || loadedProjectionCount < 0 || unloadedProjectionCount < 0 || participantBytes < 0L) {
            throw new IllegalArgumentException("Workstation restorability metrics must not be negative");
        }
        blockers = Objects.requireNonNull(blockers, "blockers").stream().sorted().toList();
    }

    public boolean restorable() {
        return status == WorkstationCheckpointCompletenessStatus.COMPLETE_RESTORABLE;
    }
}
