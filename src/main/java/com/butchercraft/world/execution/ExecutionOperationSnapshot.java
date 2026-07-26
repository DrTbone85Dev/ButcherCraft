package com.butchercraft.world.execution;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ExecutionOperationSnapshot(
        int schemaVersion,
        ExecutionOperationId operationId,
        ExecutionAuthorizationEvidence authorizationEvidence,
        ExecutionDomainEffectIdentity domainEffectIdentity,
        ExecutionStatus status,
        long createdSimulationTick,
        long lastUpdatedSimulationTick,
        long revision,
        int attemptSequence,
        boolean schedulerInvocationStarted,
        Optional<ExecutionFailure> failure,
        Optional<ExecutionOwnerResultEvidence> ownerResultEvidence,
        Optional<ExecutionResultEvidence> resultEvidence,
        List<ExecutionAttemptRecord> attempts
) {
    public ExecutionOperationSnapshot {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution operation snapshot");
        operationId = Objects.requireNonNull(operationId, "operationId");
        authorizationEvidence = Objects.requireNonNull(authorizationEvidence, "authorizationEvidence");
        if (!authorizationEvidence.digestMatches()) {
            throw new IllegalArgumentException("Execution authorization evidence digest mismatch");
        }
        domainEffectIdentity = Objects.requireNonNull(domainEffectIdentity, "domainEffectIdentity");
        status = Objects.requireNonNull(status, "status");
        createdSimulationTick = ExecutionValidation.requireTick(createdSimulationTick, "Execution created tick");
        lastUpdatedSimulationTick = ExecutionValidation.requireTick(
                lastUpdatedSimulationTick,
                "Execution updated tick"
        );
        if (lastUpdatedSimulationTick < createdSimulationTick) {
            throw new IllegalArgumentException("Execution update tick precedes creation tick");
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Execution revision must not be negative");
        }
        if (attemptSequence < 0) {
            throw new IllegalArgumentException("Execution attempt sequence must not be negative");
        }
        failure = Objects.requireNonNull(failure, "failure");
        ownerResultEvidence = Objects.requireNonNull(ownerResultEvidence, "ownerResultEvidence");
        ownerResultEvidence.ifPresent(evidence -> {
            if (!evidence.digestMatches()) {
                throw new IllegalArgumentException("Owner result digest mismatch");
            }
        });
        resultEvidence = Objects.requireNonNull(resultEvidence, "resultEvidence");
        resultEvidence.ifPresent(evidence -> {
            if (!evidence.digestMatches()) {
                throw new IllegalArgumentException("Execution result evidence digest mismatch");
            }
        });
        attempts = Objects.requireNonNull(attempts, "attempts").stream()
                .map(attempt -> Objects.requireNonNull(attempt, "attempt"))
                .sorted(java.util.Comparator.comparingInt(ExecutionAttemptRecord::attemptSequence))
                .toList();
        int requiredAttemptRecords = (status == ExecutionStatus.DISPATCHED || status == ExecutionStatus.RUNNING)
                ? Math.max(0, attemptSequence - 1)
                : attemptSequence;
        if (attempts.size() != requiredAttemptRecords) {
            throw new IllegalArgumentException("Attempt record count must equal latest attempt sequence");
        }
        if (status == ExecutionStatus.SUCCEEDED && ownerResultEvidence.isEmpty()) {
            throw new IllegalArgumentException("Succeeded Execution operation requires owner result evidence");
        }
        if (status.terminal() && status != ExecutionStatus.SUCCEEDED && failure.isEmpty()) {
            throw new IllegalArgumentException("Unsuccessful terminal Execution operation requires failure evidence");
        }
    }
}
