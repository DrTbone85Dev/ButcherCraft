package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class SimulationWorkRuntime {
    private final SimulationWorkId workId;
    private SimulationWorkStatus status;
    private int attemptCount;
    private long lastUpdatedSimulationTick;
    private OptionalLong startedTick;
    private OptionalLong completedTick;
    private OptionalLong nextEligibleTick;
    private Optional<WorkFailureCode> lastFailureCode;
    private Optional<String> diagnosticSummary;
    private WorkPayload resultSummary;
    private Optional<SchedulerInvocationIdentity> lastInvocationIdentity;
    private Optional<SchedulerEffectIdentity> lastEffectIdentity;
    private Optional<HandlerEffectType> lastEffectType;
    private Optional<String> effectPolicyIdentity;
    private Optional<String> effectOwnerSubsystemId;
    private Optional<String> ownerResultIdentity;
    private Optional<String> effectContentDigest;
    private long revision;
    private final int schemaVersion;

    public SimulationWorkRuntime(
            SimulationWorkId workId,
            SimulationWorkStatus status,
            int attemptCount,
            long lastUpdatedSimulationTick,
            OptionalLong startedTick,
            OptionalLong completedTick,
            OptionalLong nextEligibleTick,
            Optional<WorkFailureCode> lastFailureCode,
            Optional<String> diagnosticSummary,
            WorkPayload resultSummary,
            Optional<SchedulerInvocationIdentity> lastInvocationIdentity,
            Optional<SchedulerEffectIdentity> lastEffectIdentity,
            Optional<HandlerEffectType> lastEffectType,
            Optional<String> effectPolicyIdentity,
            Optional<String> effectOwnerSubsystemId,
            Optional<String> ownerResultIdentity,
            Optional<String> effectContentDigest,
            long revision,
            int schemaVersion
    ) {
        this.workId = Objects.requireNonNull(workId, "workId");
        this.status = Objects.requireNonNull(status, "status");
        if (attemptCount < 0) throw new IllegalArgumentException("Attempt count must not be negative");
        this.attemptCount = attemptCount;
        this.lastUpdatedSimulationTick = SchedulerValidation.requireTick(
                lastUpdatedSimulationTick, "Work runtime update tick"
        );
        this.startedTick = Objects.requireNonNull(startedTick, "startedTick");
        this.completedTick = Objects.requireNonNull(completedTick, "completedTick");
        this.nextEligibleTick = Objects.requireNonNull(nextEligibleTick, "nextEligibleTick");
        this.lastFailureCode = Objects.requireNonNull(lastFailureCode, "lastFailureCode");
        this.diagnosticSummary = Objects.requireNonNull(diagnosticSummary, "diagnosticSummary")
                .map(value -> SchedulerValidation.requireText(value, "Work diagnostic summary", 2_048));
        this.resultSummary = Objects.requireNonNull(resultSummary, "resultSummary");
        this.lastInvocationIdentity = Objects.requireNonNull(
                lastInvocationIdentity,
                "lastInvocationIdentity"
        );
        this.lastEffectIdentity = Objects.requireNonNull(lastEffectIdentity, "lastEffectIdentity");
        this.lastEffectType = Objects.requireNonNull(lastEffectType, "lastEffectType");
        this.effectPolicyIdentity = Objects.requireNonNull(effectPolicyIdentity, "effectPolicyIdentity")
                .map(value -> SchedulerValidation.requireId(value, "Effect policy identity"));
        this.effectOwnerSubsystemId = Objects.requireNonNull(effectOwnerSubsystemId, "effectOwnerSubsystemId")
                .map(value -> SchedulerValidation.requireId(value, "Effect owner subsystem id"));
        this.ownerResultIdentity = Objects.requireNonNull(ownerResultIdentity, "ownerResultIdentity")
                .map(value -> SchedulerValidation.requireId(value, "Owner result identity"));
        this.effectContentDigest = Objects.requireNonNull(effectContentDigest, "effectContentDigest")
                .map(value -> SchedulerIdentityDigest.requireDigest(value, "Effect content digest"));
        if (revision < 0L) throw new IllegalArgumentException("Work runtime revision must not be negative");
        this.revision = revision;
        this.schemaVersion = SchedulerValidation.requireSchema(schemaVersion, "work runtime");
        validateState();
    }

    public SimulationWorkRuntime(
            SimulationWorkId workId,
            SimulationWorkStatus status,
            int attemptCount,
            long lastUpdatedSimulationTick,
            OptionalLong startedTick,
            OptionalLong completedTick,
            OptionalLong nextEligibleTick,
            Optional<WorkFailureCode> lastFailureCode,
            Optional<String> diagnosticSummary,
            WorkPayload resultSummary,
            long revision,
            int schemaVersion
    ) {
        this(
                workId, status, attemptCount, lastUpdatedSimulationTick, startedTick, completedTick,
                nextEligibleTick, lastFailureCode, diagnosticSummary, resultSummary,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), revision, schemaVersion
        );
    }

    public static SimulationWorkRuntime scheduled(ScheduledSimulationWork work) {
        return new SimulationWorkRuntime(
                work.id(), SimulationWorkStatus.SCHEDULED, 0, work.origin().submissionTick(),
                OptionalLong.empty(), OptionalLong.empty(), OptionalLong.of(work.scheduledTick()),
                Optional.empty(), Optional.empty(), WorkPayload.empty(), 0L, SchedulerSchema.CURRENT_VERSION
        );
    }

    public synchronized SimulationWorkId workId() { return workId; }
    public synchronized SimulationWorkStatus status() { return status; }
    public synchronized int attemptCount() { return attemptCount; }
    public synchronized long lastUpdatedSimulationTick() { return lastUpdatedSimulationTick; }
    public synchronized OptionalLong startedTick() { return startedTick; }
    public synchronized OptionalLong completedTick() { return completedTick; }
    public synchronized OptionalLong nextEligibleTick() { return nextEligibleTick; }
    public synchronized Optional<WorkFailureCode> lastFailureCode() { return lastFailureCode; }
    public synchronized Optional<String> diagnosticSummary() { return diagnosticSummary; }
    public synchronized WorkPayload resultSummary() { return resultSummary; }
    public synchronized Optional<SchedulerInvocationIdentity> lastInvocationIdentity() {
        return lastInvocationIdentity;
    }
    public synchronized Optional<SchedulerEffectIdentity> lastEffectIdentity() { return lastEffectIdentity; }
    public synchronized Optional<HandlerEffectType> lastEffectType() { return lastEffectType; }
    public synchronized Optional<String> effectPolicyIdentity() { return effectPolicyIdentity; }
    public synchronized Optional<String> effectOwnerSubsystemId() { return effectOwnerSubsystemId; }
    public synchronized Optional<String> ownerResultIdentity() { return ownerResultIdentity; }
    public synchronized Optional<String> effectContentDigest() { return effectContentDigest; }
    public synchronized long revision() { return revision; }
    public int schemaVersion() { return schemaVersion; }

    public synchronized SimulationWorkRuntime snapshot() {
        return new SimulationWorkRuntime(
                workId, status, attemptCount, lastUpdatedSimulationTick, startedTick, completedTick,
                nextEligibleTick, lastFailureCode, diagnosticSummary, resultSummary,
                lastInvocationIdentity, lastEffectIdentity, lastEffectType, effectPolicyIdentity,
                effectOwnerSubsystemId, ownerResultIdentity, effectContentDigest, revision, schemaVersion
        );
    }

    synchronized void makeEligible(long tick) {
        transition(SimulationWorkStatus.ELIGIBLE, tick);
        nextEligibleTick = OptionalLong.empty();
    }

    synchronized void start(long tick) {
        transition(SimulationWorkStatus.RUNNING, tick);
        attemptCount = Math.incrementExact(attemptCount);
        startedTick = OptionalLong.of(tick);
        completedTick = OptionalLong.empty();
    }

    synchronized void start(
            long tick,
            SchedulerInvocationIdentity invocationIdentity,
            Optional<SchedulerEffectIdentity> effectIdentity,
            SchedulerEffectPolicy policy
    ) {
        Objects.requireNonNull(invocationIdentity, "invocationIdentity");
        Objects.requireNonNull(effectIdentity, "effectIdentity");
        Objects.requireNonNull(policy, "policy");
        if (policy.requiresEffectIdentity() && effectIdentity.isEmpty()) {
            throw new IllegalArgumentException("Consequential Work start requires Effect Identity");
        }
        if (!policy.requiresEffectIdentity() && effectIdentity.isPresent()) {
            throw new IllegalArgumentException("Read-only Work start cannot include Effect Identity");
        }
        transition(SimulationWorkStatus.RUNNING, tick);
        attemptCount = Math.incrementExact(attemptCount);
        startedTick = OptionalLong.of(tick);
        completedTick = OptionalLong.empty();
        lastInvocationIdentity = Optional.of(invocationIdentity);
        lastEffectIdentity = effectIdentity;
        lastEffectType = effectIdentity.isPresent() ? Optional.of(policy.effectType()) : Optional.empty();
        effectPolicyIdentity = Optional.of(policy.policyIdentity());
    }

    synchronized void observeEffect(SchedulerEffectObservation observation) {
        SchedulerEffectObservation value = Objects.requireNonNull(observation, "observation");
        if (lastEffectIdentity.isPresent() && !lastEffectIdentity.orElseThrow().equals(value.effectIdentity())) {
            throw new IllegalStateException("Observed effect identity differs from the active Work effect identity");
        }
        lastEffectIdentity = Optional.of(value.effectIdentity());
        lastEffectType = Optional.of(value.effectType());
        effectOwnerSubsystemId = Optional.of(value.ownerSubsystemId());
        ownerResultIdentity = Optional.of(value.ownerResultIdentity());
        effectContentDigest = Optional.of(value.resultContentDigest());
        revision = Math.incrementExact(revision);
    }

    synchronized void complete(long tick, WorkPayload summary) {
        transition(SimulationWorkStatus.COMPLETED, tick);
        completedTick = OptionalLong.of(tick);
        nextEligibleTick = OptionalLong.empty();
        lastFailureCode = Optional.empty();
        diagnosticSummary = Optional.empty();
        resultSummary = Objects.requireNonNull(summary, "summary");
    }

    synchronized void defer(long tick, long eligibleTick, String reason) {
        if (eligibleTick <= tick) throw new IllegalArgumentException("Deferred tick must follow current tick");
        transition(SimulationWorkStatus.DEFERRED, tick);
        nextEligibleTick = OptionalLong.of(eligibleTick);
        diagnosticSummary = Optional.of(SchedulerValidation.requireText(reason, "Deferral reason", 2_048));
    }

    synchronized void reschedulePending(long tick, long eligibleTick, String reason) {
        SchedulerValidation.requireTick(tick, "Work reschedule tick");
        SchedulerValidation.requireTick(eligibleTick, "Work reschedule eligible tick");
        if (eligibleTick <= tick) {
            throw new IllegalArgumentException("Rescheduled eligible tick must follow current tick");
        }
        if (tick < lastUpdatedSimulationTick) throw new IllegalStateException("Work tick cannot move backward");
        if (status != SimulationWorkStatus.SCHEDULED
                && status != SimulationWorkStatus.DEFERRED
                && status != SimulationWorkStatus.RETRY_WAIT) {
            throw new IllegalStateException("Work status cannot be rescheduled: " + status);
        }
        lastUpdatedSimulationTick = tick;
        nextEligibleTick = OptionalLong.of(eligibleTick);
        diagnosticSummary = Optional.of(SchedulerValidation.requireText(reason, "Reschedule reason", 2_048));
        revision = Math.incrementExact(revision);
    }

    synchronized void retry(long tick, long eligibleTick, WorkFailureCode failureCode, String reason) {
        if (eligibleTick <= tick) throw new IllegalArgumentException("Retry tick must follow current tick");
        transition(SimulationWorkStatus.RETRY_WAIT, tick);
        nextEligibleTick = OptionalLong.of(eligibleTick);
        lastFailureCode = Optional.of(Objects.requireNonNull(failureCode, "failureCode"));
        diagnosticSummary = Optional.of(SchedulerValidation.requireText(reason, "Retry reason", 2_048));
    }

    synchronized void fail(long tick, WorkFailureCode failureCode, String reason) {
        transition(SimulationWorkStatus.FAILED, tick);
        completedTick = OptionalLong.of(tick);
        nextEligibleTick = OptionalLong.empty();
        lastFailureCode = Optional.of(Objects.requireNonNull(failureCode, "failureCode"));
        diagnosticSummary = Optional.of(SchedulerValidation.requireText(reason, "Failure reason", 2_048));
    }

    synchronized void unknownOutcome(long tick, WorkFailureCode failureCode, String reason) {
        transition(SimulationWorkStatus.UNKNOWN_OUTCOME, tick);
        completedTick = OptionalLong.of(tick);
        nextEligibleTick = OptionalLong.empty();
        lastFailureCode = Optional.of(Objects.requireNonNull(failureCode, "failureCode"));
        diagnosticSummary = Optional.of(SchedulerValidation.requireText(reason, "Unknown outcome reason", 2_048));
    }

    synchronized void cancel(long tick, String reason) {
        transition(SimulationWorkStatus.CANCELLED, tick);
        completedTick = OptionalLong.of(tick);
        nextEligibleTick = OptionalLong.empty();
        diagnosticSummary = Optional.of(SchedulerValidation.requireText(reason, "Cancellation reason", 2_048));
    }

    synchronized void expire(long tick) {
        transition(SimulationWorkStatus.EXPIRED, tick);
        completedTick = OptionalLong.of(tick);
        nextEligibleTick = OptionalLong.empty();
        lastFailureCode = Optional.of(WorkFailureCode.WORK_EXPIRED);
        diagnosticSummary = Optional.of("Work expired before execution");
    }

    public synchronized void validateAgainst(ScheduledSimulationWork work) {
        Objects.requireNonNull(work, "work");
        if (!workId.equals(work.id())) throw new IllegalArgumentException("Work runtime id mismatch");
        if (attemptCount > work.maximumAttempts()) throw new IllegalArgumentException("Attempt count exceeds maximum");
        if (!status.isTerminal() && attemptCount > 0 && attemptCount >= work.maximumAttempts()) {
            throw new IllegalArgumentException("Pending work has no remaining execution attempt");
        }
        if (lastUpdatedSimulationTick < work.origin().submissionTick()) {
            throw new IllegalArgumentException("Runtime update tick precedes submission");
        }
        if (status == SimulationWorkStatus.RUNNING) {
            throw new IllegalArgumentException("Persisted RUNNING work is unsupported");
        }
        if (status == SimulationWorkStatus.UNKNOWN_OUTCOME && lastEffectIdentity.isEmpty()) {
            throw new IllegalArgumentException("Unknown Outcome requires the active Effect Identity");
        }
        if (schemaVersion == SchedulerSchema.CURRENT_VERSION
                && attemptCount > 0
                && lastInvocationIdentity.isEmpty()) {
            throw new IllegalArgumentException("Schema 2 attempted Work requires Invocation Identity");
        }
        if ((status == SimulationWorkStatus.SCHEDULED || status == SimulationWorkStatus.DEFERRED
                || status == SimulationWorkStatus.RETRY_WAIT) && nextEligibleTick.isEmpty()) {
            throw new IllegalArgumentException("Pending work requires a next eligible tick");
        }
        if ((status == SimulationWorkStatus.ELIGIBLE || status.isTerminal()) && nextEligibleTick.isPresent()) {
            throw new IllegalArgumentException("Eligible or terminal work cannot retain a next eligible tick");
        }
    }

    synchronized Optional<SchedulerEffectObservation> effectObservation() {
        if (lastEffectIdentity.isEmpty()
                || lastEffectType.isEmpty()
                || effectOwnerSubsystemId.isEmpty()
                || ownerResultIdentity.isEmpty()
                || effectContentDigest.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SchedulerEffectObservation(
                SchedulerSchema.CURRENT_VERSION,
                lastEffectIdentity.orElseThrow(),
                lastEffectType.orElseThrow(),
                effectOwnerSubsystemId.orElseThrow(),
                ownerResultIdentity.orElseThrow(),
                effectContentDigest.orElseThrow()
        ));
    }

    private void transition(SimulationWorkStatus next, long tick) {
        SchedulerValidation.requireTick(tick, "Work transition tick");
        if (tick < lastUpdatedSimulationTick) throw new IllegalStateException("Work tick cannot move backward");
        if (!status.allowedNextStatuses().contains(next)) {
            throw new IllegalStateException("Invalid work transition: " + status + " -> " + next);
        }
        status = next;
        lastUpdatedSimulationTick = tick;
        revision = Math.incrementExact(revision);
    }

    private void validateState() {
        startedTick.ifPresent(tick -> {
            SchedulerValidation.requireTick(tick, "Work started tick");
            if (tick > lastUpdatedSimulationTick) throw new IllegalArgumentException("Started tick follows update tick");
        });
        completedTick.ifPresent(tick -> {
            SchedulerValidation.requireTick(tick, "Work completed tick");
            if (tick > lastUpdatedSimulationTick) throw new IllegalArgumentException("Completed tick follows update tick");
        });
        nextEligibleTick.ifPresent(tick -> SchedulerValidation.requireTick(tick, "Next eligible tick"));
        if (status.isTerminal() != completedTick.isPresent()) {
            throw new IllegalArgumentException("Terminal work status and completed tick are inconsistent");
        }
        if (lastEffectIdentity.isPresent() != lastEffectType.isPresent()) {
            throw new IllegalArgumentException("Effect identity and effect type must be recorded together");
        }
        boolean hasAnyObservationField = effectOwnerSubsystemId.isPresent()
                || ownerResultIdentity.isPresent()
                || effectContentDigest.isPresent();
        boolean hasAllObservationFields = effectOwnerSubsystemId.isPresent()
                && ownerResultIdentity.isPresent()
                && effectContentDigest.isPresent();
        if (hasAnyObservationField && (!hasAllObservationFields || lastEffectIdentity.isEmpty())) {
            throw new IllegalArgumentException("Effect observation metadata is incomplete");
        }
    }
}
