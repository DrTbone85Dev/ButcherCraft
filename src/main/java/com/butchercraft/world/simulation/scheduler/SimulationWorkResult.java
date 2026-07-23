package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record SimulationWorkResult(
        SimulationWorkOutcome outcome,
        Optional<WorkFailureCode> failureCode,
        List<String> diagnosticMessages,
        OptionalLong nextEligibleTick,
        List<SimulationWorkRequest> generatedWork,
        WorkPayload resultMetadata,
        int workUnitsConsumed,
        long executionTick
) {
    public SimulationWorkResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        diagnosticMessages = Objects.requireNonNull(diagnosticMessages, "diagnosticMessages").stream()
                .map(message -> SchedulerValidation.requireText(message, "Work result diagnostic", 2_048)).toList();
        nextEligibleTick = Objects.requireNonNull(nextEligibleTick, "nextEligibleTick");
        generatedWork = List.copyOf(Objects.requireNonNull(generatedWork, "generatedWork"));
        resultMetadata = Objects.requireNonNull(resultMetadata, "resultMetadata");
        if (workUnitsConsumed < 0) throw new IllegalArgumentException("Work units must not be negative");
        executionTick = SchedulerValidation.requireTick(executionTick, "Work result execution tick");
        if ((outcome == SimulationWorkOutcome.FAILED) != failureCode.isPresent()) {
            throw new IllegalArgumentException("Failure code presence does not match work outcome");
        }
        if (outcome != SimulationWorkOutcome.RETRY && outcome != SimulationWorkOutcome.DEFERRED
                && nextEligibleTick.isPresent()) {
            throw new IllegalArgumentException("Only retry or deferred outcomes may include a next eligible tick");
        }
    }

    public static SimulationWorkResult completed(long tick, int units) {
        return new SimulationWorkResult(
                SimulationWorkOutcome.COMPLETED, Optional.empty(), List.of(), OptionalLong.empty(),
                List.of(), WorkPayload.empty(), units, tick
        );
    }
    public static SimulationWorkResult failed(long tick, WorkFailureCode code, String message, int units) {
        return new SimulationWorkResult(
                SimulationWorkOutcome.FAILED, Optional.of(code), List.of(message), OptionalLong.empty(),
                List.of(), WorkPayload.empty(), units, tick
        );
    }
}
