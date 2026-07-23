package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SimulationTickReport(
        long authoritativeSimulationTick,
        PipelineStatus status,
        Optional<WorkFailureCode> failureCode,
        int promotedWorkItems,
        int expiredWorkItems,
        int attemptedWorkItems,
        int generatedWorkItems,
        int sameTickGeneratedWorkItems,
        int retryTransitions,
        long handlerWorkUnitsConsumed,
        List<SimulationStageReport> stageReports,
        List<String> diagnostics
) {
    public SimulationTickReport {
        authoritativeSimulationTick = SchedulerValidation.requireTick(
                authoritativeSimulationTick, "Pipeline report tick"
        );
        status = Objects.requireNonNull(status, "status");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        stageReports = List.copyOf(Objects.requireNonNull(stageReports, "stageReports"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (promotedWorkItems < 0 || expiredWorkItems < 0 || attemptedWorkItems < 0
                || generatedWorkItems < 0 || sameTickGeneratedWorkItems < 0
                || retryTransitions < 0 || handlerWorkUnitsConsumed < 0L) {
            throw new IllegalArgumentException("Pipeline report counts must not be negative");
        }
    }

    public static SimulationTickReport rejected(long tick, WorkFailureCode code, String diagnostic) {
        return new SimulationTickReport(
                tick, PipelineStatus.REJECTED, Optional.of(code), 0, 0, 0, 0, 0, 0, 0L,
                List.of(), List.of(diagnostic)
        );
    }
}
