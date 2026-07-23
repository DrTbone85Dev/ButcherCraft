package com.butchercraft.world.production;

import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.transaction.TransactionId;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record ProductionRunSnapshot(
        ProductionRunId id,
        ProductionPlanId planId,
        ProductionRunStatus status,
        long lastUpdatedSimulationTick,
        OptionalLong startedTick,
        OptionalLong pausedTick,
        OptionalLong completedTick,
        long requiredWorkUnits,
        long currentWorkUnits,
        int executionAttemptCount,
        OptionalLong nextEligibleTick,
        Optional<SimulationWorkId> scheduledWorkId,
        Optional<TransactionId> completionTransactionId,
        Optional<ProductionFailureCode> failureCode,
        Optional<String> failureSummary,
        long revision,
        int schemaVersion
) {
    public ProductionRunSnapshot {
        id = Objects.requireNonNull(id, "id");
        planId = Objects.requireNonNull(planId, "planId");
        status = Objects.requireNonNull(status, "status");
        if (lastUpdatedSimulationTick < 0L || requiredWorkUnits <= 0L || currentWorkUnits < 0L
                || currentWorkUnits > requiredWorkUnits || executionAttemptCount < 0 || revision < 0L) {
            throw new IllegalArgumentException("Production run snapshot contains invalid numeric state");
        }
        startedTick = Objects.requireNonNull(startedTick, "startedTick");
        pausedTick = Objects.requireNonNull(pausedTick, "pausedTick");
        completedTick = Objects.requireNonNull(completedTick, "completedTick");
        nextEligibleTick = Objects.requireNonNull(nextEligibleTick, "nextEligibleTick");
        scheduledWorkId = Objects.requireNonNull(scheduledWorkId, "scheduledWorkId");
        completionTransactionId = Objects.requireNonNull(completionTransactionId, "completionTransactionId");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        failureSummary = Objects.requireNonNull(failureSummary, "failureSummary")
                .map(value -> ProductionValidation.requireText(value, "Production failure summary", 2_048));
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production run");
        validateConsistency(
                status,
                currentWorkUnits,
                requiredWorkUnits,
                pausedTick,
                completedTick,
                completionTransactionId,
                failureCode,
                failureSummary
        );
    }

    private static void validateConsistency(
            ProductionRunStatus status,
            long currentWorkUnits,
            long requiredWorkUnits,
            OptionalLong pausedTick,
            OptionalLong completedTick,
            Optional<TransactionId> completionTransactionId,
            Optional<ProductionFailureCode> failureCode,
            Optional<String> failureSummary
    ) {
        if (failureCode.isPresent() != failureSummary.isPresent()) {
            throw new IllegalArgumentException("Production run failure fields must be present together");
        }
        if (status == ProductionRunStatus.COMPLETED
                && (completedTick.isEmpty() || completionTransactionId.isEmpty()
                || currentWorkUnits != requiredWorkUnits)) {
            throw new IllegalArgumentException("Completed production run state is incomplete");
        }
        if (status != ProductionRunStatus.COMPLETED && completedTick.isPresent()) {
            throw new IllegalArgumentException("Only completed production runs may contain a completion tick");
        }
        if (status == ProductionRunStatus.PAUSED && pausedTick.isEmpty()) {
            throw new IllegalArgumentException("Paused production run requires a pause tick");
        }
        if (status != ProductionRunStatus.PAUSED && pausedTick.isPresent()) {
            throw new IllegalArgumentException("Only paused production runs may contain a pause tick");
        }
    }
}
