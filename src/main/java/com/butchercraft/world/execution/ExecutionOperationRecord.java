package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SchedulerEffectIdentity;
import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class ExecutionOperationRecord {
    private final ExecutionOperationId operationId;
    private final ExecutionAuthorizationEvidence authorizationEvidence;
    private final ExecutionDomainEffectIdentity domainEffectIdentity;
    private final long createdSimulationTick;
    private final List<ExecutionAttemptRecord> attempts;
    private ExecutionStatus status;
    private long lastUpdatedSimulationTick;
    private long revision;
    private int attemptSequence;
    private boolean schedulerInvocationStarted;
    private Optional<ExecutionFailure> failure;
    private Optional<ExecutionOwnerResultEvidence> ownerResultEvidence;
    private Optional<ExecutionResultEvidence> resultEvidence;

    private ExecutionOperationRecord(
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
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.authorizationEvidence = Objects.requireNonNull(authorizationEvidence, "authorizationEvidence");
        this.domainEffectIdentity = Objects.requireNonNull(domainEffectIdentity, "domainEffectIdentity");
        this.status = Objects.requireNonNull(status, "status");
        this.createdSimulationTick = ExecutionValidation.requireTick(createdSimulationTick, "Execution created tick");
        this.lastUpdatedSimulationTick = ExecutionValidation.requireTick(
                lastUpdatedSimulationTick,
                "Execution updated tick"
        );
        if (revision < 0L || attemptSequence < 0) {
            throw new IllegalArgumentException("Execution counters must not be negative");
        }
        this.revision = revision;
        this.attemptSequence = attemptSequence;
        this.schedulerInvocationStarted = schedulerInvocationStarted;
        this.failure = Objects.requireNonNull(failure, "failure");
        this.ownerResultEvidence = Objects.requireNonNull(ownerResultEvidence, "ownerResultEvidence");
        this.resultEvidence = Objects.requireNonNull(resultEvidence, "resultEvidence");
        this.attempts = new ArrayList<>(Objects.requireNonNull(attempts, "attempts"));
        snapshot();
    }

    static ExecutionOperationRecord create(ExecutionAuthorizationEvidence evidence, long tick) {
        ExecutionOperationId id = ExecutionOperationId.derive(evidence);
        return new ExecutionOperationRecord(
                id,
                evidence,
                ExecutionDomainEffectIdentity.derive(id, evidence),
                ExecutionStatus.AUTHORIZED,
                tick,
                tick,
                0L,
                0,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
    }

    static ExecutionOperationRecord fromSnapshot(ExecutionOperationSnapshot snapshot) {
        return new ExecutionOperationRecord(
                snapshot.operationId(),
                snapshot.authorizationEvidence(),
                snapshot.domainEffectIdentity(),
                restoredStatus(snapshot),
                snapshot.createdSimulationTick(),
                snapshot.lastUpdatedSimulationTick(),
                snapshot.revision(),
                snapshot.attemptSequence(),
                snapshot.schedulerInvocationStarted(),
                restoredFailure(snapshot),
                snapshot.ownerResultEvidence(),
                restoredResultEvidence(snapshot),
                snapshot.attempts()
        );
    }

    synchronized ExecutionOperationSnapshot snapshot() {
        return new ExecutionOperationSnapshot(
                ExecutionSchema.CURRENT_VERSION,
                operationId,
                authorizationEvidence,
                domainEffectIdentity,
                status,
                createdSimulationTick,
                lastUpdatedSimulationTick,
                revision,
                attemptSequence,
                schedulerInvocationStarted,
                failure,
                ownerResultEvidence,
                resultEvidence,
                attempts
        );
    }

    synchronized ExecutionOperationId operationId() {
        return operationId;
    }

    synchronized ExecutionAuthorizationEvidence authorizationEvidence() {
        return authorizationEvidence;
    }

    synchronized ExecutionStatus status() {
        return status;
    }

    synchronized void ready(long tick) {
        transition(ExecutionStatus.READY, tick);
    }

    synchronized void cancelBeforeStart(long tick, String reason) {
        if (schedulerInvocationStarted) {
            throw new IllegalStateException("Execution cancellation after invocation start is unsupported");
        }
        failure = Optional.of(ExecutionFailure.of(
                ExecutionFailureCode.CANCELLATION_REQUESTED,
                reason,
                operationId.value()
        ));
        transition(ExecutionStatus.CANCELLED_BEFORE_START, tick);
        resultEvidence = Optional.of(ExecutionResultEvidence.fromTerminal(
                snapshot(), Optional.empty(), Optional.empty(), Optional.empty(), failure
        ));
    }

    synchronized void terminalBeforeInvocation(long tick, ExecutionStatus terminalStatus, ExecutionFailure terminalFailure) {
        if (schedulerInvocationStarted) {
            throw new IllegalStateException("Pre-invocation terminal publication cannot follow invocation start");
        }
        if (terminalStatus != ExecutionStatus.REJECTED
                && terminalStatus != ExecutionStatus.FAILED
                && terminalStatus != ExecutionStatus.CANCELLED_BEFORE_START) {
            throw new IllegalArgumentException("Invalid pre-invocation terminal status: " + terminalStatus);
        }
        failure = Optional.of(Objects.requireNonNull(terminalFailure, "terminalFailure"));
        transition(terminalStatus, tick);
        resultEvidence = Optional.of(ExecutionResultEvidence.fromTerminal(
                snapshot(), Optional.empty(), Optional.empty(), Optional.empty(), failure
        ));
    }

    synchronized ExecutionAttemptId beginAttempt(
            long tick,
            SchedulerInvocationIdentity invocationIdentity,
            SchedulerEffectIdentity effectIdentity,
            String handlerId
    ) {
        transition(ExecutionStatus.DISPATCHED, tick);
        transition(ExecutionStatus.RUNNING, tick);
        schedulerInvocationStarted = true;
        attemptSequence = Math.incrementExact(attemptSequence);
        return ExecutionAttemptId.derive(operationId, attemptSequence, invocationIdentity, tick, handlerId);
    }

    synchronized void publishAttemptResult(
            ExecutionAttemptId attemptId,
            long tick,
            SchedulerInvocationIdentity invocationIdentity,
            SchedulerEffectIdentity effectIdentity,
            ExecutionStatus startingStatus,
            ExecutionStatus endingStatus,
            String handlerId,
            Optional<ExecutionOwnerResultEvidence> ownerResult,
            Optional<ExecutionFailure> terminalFailure,
            int workUnits
    ) {
        if (endingStatus == ExecutionStatus.SUCCEEDED && ownerResult.isEmpty()) {
            throw new IllegalArgumentException("Succeeded attempt requires owner result evidence");
        }
        if (endingStatus != ExecutionStatus.AWAITING_OWNER_RESULT
                && endingStatus != ExecutionStatus.SUCCEEDED
                && terminalFailure.isEmpty()) {
            throw new IllegalArgumentException("Terminal unsuccessful attempt requires failure evidence");
        }
        ownerResult.ifPresent(value -> ownerResultEvidence = Optional.of(value));
        terminalFailure.ifPresent(value -> failure = Optional.of(value));
        transition(endingStatus, tick);
        attempts.add(ExecutionAttemptRecord.completed(
                attemptId,
                operationId,
                attemptSequence,
                tick,
                invocationIdentity,
                effectIdentity,
                startingStatus,
                endingStatus,
                handlerId,
                ownerResult.map(ExecutionOwnerResultEvidence::ownerResultIdentity),
                terminalFailure,
                workUnits
        ));
        if (endingStatus.terminal()) {
            resultEvidence = Optional.of(ExecutionResultEvidence.fromTerminal(
                    snapshot(),
                    Optional.of(invocationIdentity),
                    Optional.of(effectIdentity),
                    ownerResultEvidence,
                    failure
            ));
        }
    }

    synchronized void unknownOutcome(
            long tick,
            String message,
            Optional<SchedulerInvocationIdentity> invocationIdentity,
            Optional<SchedulerEffectIdentity> effectIdentity
    ) {
        failure = Optional.of(ExecutionFailure.of(
                ExecutionFailureCode.HANDLER_EXCEPTION_UNKNOWN_OUTCOME,
                message,
                operationId.value()
        ));
        transition(ExecutionStatus.UNKNOWN_OUTCOME, tick);
        resultEvidence = Optional.of(ExecutionResultEvidence.fromTerminal(
                snapshot(),
                invocationIdentity,
                effectIdentity,
                Optional.empty(),
                failure
        ));
    }

    synchronized void validateForPersistence() {
        if (status == ExecutionStatus.DISPATCHED || status == ExecutionStatus.RUNNING) {
            throw new IllegalStateException("Cannot persist Execution while an operation is actively invoked");
        }
        snapshot();
    }

    private void transition(ExecutionStatus next, long tick) {
        ExecutionValidation.requireTick(tick, "Execution transition tick");
        if (tick < lastUpdatedSimulationTick) {
            throw new IllegalStateException("Execution tick cannot move backward");
        }
        if (!status.allowedNextStatuses().contains(next)) {
            throw new IllegalStateException("Invalid Execution transition: " + status + " -> " + next);
        }
        status = next;
        lastUpdatedSimulationTick = tick;
        revision = Math.incrementExact(revision);
    }

    private static ExecutionStatus restoredStatus(ExecutionOperationSnapshot snapshot) {
        if (snapshot.status() == ExecutionStatus.DISPATCHED
                || snapshot.status() == ExecutionStatus.RUNNING
                || snapshot.status() == ExecutionStatus.AWAITING_OWNER_RESULT) {
            return ExecutionStatus.UNKNOWN_OUTCOME;
        }
        return snapshot.status();
    }

    private static Optional<ExecutionFailure> restoredFailure(ExecutionOperationSnapshot snapshot) {
        if (snapshot.status() == ExecutionStatus.DISPATCHED
                || snapshot.status() == ExecutionStatus.RUNNING
                || snapshot.status() == ExecutionStatus.AWAITING_OWNER_RESULT) {
            return Optional.of(ExecutionFailure.of(
                    ExecutionFailureCode.HANDLER_EXCEPTION_UNKNOWN_OUTCOME,
                    "Recovered Execution operation had an unresolved invocation outcome",
                    snapshot.operationId().value()
            ));
        }
        return snapshot.failure();
    }

    private static Optional<ExecutionResultEvidence> restoredResultEvidence(ExecutionOperationSnapshot snapshot) {
        if (snapshot.status() == ExecutionStatus.DISPATCHED
                || snapshot.status() == ExecutionStatus.RUNNING
                || snapshot.status() == ExecutionStatus.AWAITING_OWNER_RESULT) {
            return Optional.empty();
        }
        return snapshot.resultEvidence();
    }
}
