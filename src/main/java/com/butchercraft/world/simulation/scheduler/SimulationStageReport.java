package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;

public record SimulationStageReport(
        SimulationStageId stageId,
        int attemptedWorkItems,
        int completedWorkItems,
        int deferredWorkItems,
        int retryingWorkItems,
        int failedWorkItems,
        long workUnitsConsumed,
        boolean budgetExhausted,
        boolean stoppedByFailurePolicy,
        List<SimulationWorkExecutionSummary> workResults
) {
    public SimulationStageReport {
        stageId = Objects.requireNonNull(stageId, "stageId");
        workResults = List.copyOf(Objects.requireNonNull(workResults, "workResults"));
        if (attemptedWorkItems < 0 || completedWorkItems < 0 || deferredWorkItems < 0
                || retryingWorkItems < 0 || failedWorkItems < 0 || workUnitsConsumed < 0L) {
            throw new IllegalArgumentException("Stage report counts must not be negative");
        }
    }
}
