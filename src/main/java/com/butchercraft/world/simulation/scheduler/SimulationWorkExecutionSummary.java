package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SimulationWorkExecutionSummary(
        SimulationWorkId workId,
        SimulationWorkTypeId workTypeId,
        SimulationWorkStatus finalStatus,
        Optional<SimulationWorkOutcome> outcome,
        Optional<WorkFailureCode> failureCode,
        List<String> diagnostics,
        int workUnitsConsumed,
        int generatedWorkCount
) {
    public SimulationWorkExecutionSummary {
        workId = Objects.requireNonNull(workId, "workId");
        workTypeId = Objects.requireNonNull(workTypeId, "workTypeId");
        finalStatus = Objects.requireNonNull(finalStatus, "finalStatus");
        outcome = Objects.requireNonNull(outcome, "outcome");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (workUnitsConsumed < 0 || generatedWorkCount < 0) {
            throw new IllegalArgumentException("Execution summary counts must not be negative");
        }
    }
}
