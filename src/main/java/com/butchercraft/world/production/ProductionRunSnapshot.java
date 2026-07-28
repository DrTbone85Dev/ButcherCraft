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
        Optional<ProductionWorkstationAssignment> workstationAssignment,
        Optional<ProductionWorkstationChain> workstationChain,
        Optional<ProductionDeadline> deadline,
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
        workstationAssignment = Objects.requireNonNull(workstationAssignment, "workstationAssignment");
        workstationChain = Objects.requireNonNull(workstationChain, "workstationChain");
        deadline = Objects.requireNonNull(deadline, "deadline");
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        failureSummary = Objects.requireNonNull(failureSummary, "failureSummary")
                .map(value -> ProductionValidation.requireText(value, "Production failure summary", 2_048));
        ProductionRunId normalizedId = id;
        deadline.ifPresent(value -> {
            if (!value.runId().equals(normalizedId)) {
                throw new IllegalArgumentException("Production deadline references the wrong Run");
            }
        });
        schemaVersion = ProductionValidation.requireSchema(schemaVersion, "production run");
        validateConsistency(
                status,
                currentWorkUnits,
                requiredWorkUnits,
                pausedTick,
                completedTick,
                completionTransactionId,
                workstationAssignment,
                workstationChain,
                scheduledWorkId,
                failureCode,
                failureSummary
        );
    }

    public ProductionRunSnapshot(
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
            Optional<ProductionWorkstationAssignment> workstationAssignment,
            Optional<ProductionFailureCode> failureCode,
            Optional<String> failureSummary,
            long revision,
            int schemaVersion
    ) {
        this(
                id,
                planId,
                status,
                lastUpdatedSimulationTick,
                startedTick,
                pausedTick,
                completedTick,
                requiredWorkUnits,
                currentWorkUnits,
                executionAttemptCount,
                nextEligibleTick,
                scheduledWorkId,
                completionTransactionId,
                workstationAssignment,
                Optional.empty(),
                Optional.empty(),
                failureCode,
                failureSummary,
                revision,
                schemaVersion
        );
    }

    public ProductionRunSnapshot(
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
            Optional<ProductionWorkstationAssignment> workstationAssignment,
            Optional<ProductionWorkstationChain> workstationChain,
            Optional<ProductionFailureCode> failureCode,
            Optional<String> failureSummary,
            long revision,
            int schemaVersion
    ) {
        this(
                id,
                planId,
                status,
                lastUpdatedSimulationTick,
                startedTick,
                pausedTick,
                completedTick,
                requiredWorkUnits,
                currentWorkUnits,
                executionAttemptCount,
                nextEligibleTick,
                scheduledWorkId,
                completionTransactionId,
                workstationAssignment,
                workstationChain,
                Optional.empty(),
                failureCode,
                failureSummary,
                revision,
                schemaVersion
        );
    }

    private static void validateConsistency(
            ProductionRunStatus status,
            long currentWorkUnits,
            long requiredWorkUnits,
            OptionalLong pausedTick,
            OptionalLong completedTick,
            Optional<TransactionId> completionTransactionId,
            Optional<ProductionWorkstationAssignment> workstationAssignment,
            Optional<ProductionWorkstationChain> workstationChain,
            Optional<SimulationWorkId> scheduledWorkId,
            Optional<ProductionFailureCode> failureCode,
            Optional<String> failureSummary
    ) {
        if (failureCode.isPresent() != failureSummary.isPresent()) {
            throw new IllegalArgumentException("Production run failure fields must be present together");
        }
        boolean hasWorkstationCompletion = workstationAssignment
                .flatMap(ProductionWorkstationAssignment::completionEvidence)
                .isPresent();
        boolean hasChainCompletion = workstationChain
                .flatMap(ProductionWorkstationChain::completionEvidence)
                .isPresent();
        int completionAuthorityCount = (completionTransactionId.isPresent() ? 1 : 0)
                + (hasWorkstationCompletion ? 1 : 0)
                + (hasChainCompletion ? 1 : 0);
        if (status == ProductionRunStatus.COMPLETED
                && (completedTick.isEmpty() || completionAuthorityCount != 1
                || currentWorkUnits != requiredWorkUnits)) {
            throw new IllegalArgumentException("Completed production run state is incomplete");
        }
        if (status != ProductionRunStatus.COMPLETED && completionAuthorityCount > 0) {
            throw new IllegalArgumentException("Only completed production runs may contain completion authority");
        }
        if (status != ProductionRunStatus.COMPLETED && completedTick.isPresent()) {
            throw new IllegalArgumentException("Only completed production runs may contain a completion tick");
        }
        if (scheduledWorkId.isPresent() && workstationAssignment.isPresent()) {
            throw new IllegalArgumentException("Production Scheduler Work and workstation assignment are mutually exclusive");
        }
        if (scheduledWorkId.isPresent() && workstationChain.isPresent()) {
            throw new IllegalArgumentException("Production Scheduler Work and workstation chains are mutually exclusive");
        }
        if (workstationAssignment.isPresent() && workstationChain.isPresent()) {
            throw new IllegalArgumentException("Production run cannot use both single and chain workstation assignment");
        }
        if (status == ProductionRunStatus.AWAITING_TRANSACTION
                && (workstationAssignment.isPresent() || workstationChain.isPresent())) {
            throw new IllegalArgumentException("Workstation-assigned Production runs do not await Transactions");
        }
        workstationChain.ifPresent(chain -> {
            if (status == ProductionRunStatus.COMPLETED && chain.status() != ProductionWorkstationChainStatus.COMPLETE) {
                throw new IllegalArgumentException("Completed Production chain Run requires complete chain status");
            }
            if (chain.completionEvidence().isPresent()) {
                ProductionChainCompletionEvidence evidence = chain.completionEvidence().orElseThrow();
                if (!evidence.digestMatches()) {
                    throw new IllegalArgumentException("Production chain completion evidence digest mismatch");
                }
            }
        });
        if (status == ProductionRunStatus.PAUSED && pausedTick.isEmpty()) {
            throw new IllegalArgumentException("Paused production run requires a pause tick");
        }
        if (status != ProductionRunStatus.PAUSED && pausedTick.isPresent()) {
            throw new IllegalArgumentException("Only paused production runs may contain a pause tick");
        }
    }
}
