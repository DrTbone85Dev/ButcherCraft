package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SchedulerEffectIdentity;
import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;

import java.util.Objects;
import java.util.Optional;

public record ExecutionAttemptRecord(
        int schemaVersion,
        ExecutionAttemptId attemptId,
        ExecutionOperationId operationId,
        int attemptSequence,
        long simulationTick,
        SchedulerInvocationIdentity schedulerInvocationIdentity,
        SchedulerEffectIdentity schedulerEffectIdentity,
        ExecutionStatus startingStatus,
        ExecutionStatus endingStatus,
        String handlerId,
        Optional<String> ownerResultIdentity,
        Optional<ExecutionFailure> failure,
        int workUnits,
        String attemptContentDigest
) {
    public ExecutionAttemptRecord {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution attempt record");
        attemptId = Objects.requireNonNull(attemptId, "attemptId");
        operationId = Objects.requireNonNull(operationId, "operationId");
        attemptSequence = ExecutionValidation.requirePositive(attemptSequence, "Execution attempt sequence");
        simulationTick = ExecutionValidation.requireTick(simulationTick, "Execution attempt tick");
        schedulerInvocationIdentity = Objects.requireNonNull(
                schedulerInvocationIdentity,
                "schedulerInvocationIdentity"
        );
        schedulerEffectIdentity = Objects.requireNonNull(schedulerEffectIdentity, "schedulerEffectIdentity");
        startingStatus = Objects.requireNonNull(startingStatus, "startingStatus");
        endingStatus = Objects.requireNonNull(endingStatus, "endingStatus");
        handlerId = ExecutionValidation.requireId(handlerId, "Execution attempt handler id");
        ownerResultIdentity = Objects.requireNonNull(ownerResultIdentity, "ownerResultIdentity")
                .map(value -> ExecutionValidation.requireId(value, "Owner result identity"));
        failure = Objects.requireNonNull(failure, "failure");
        if (workUnits < 0) {
            throw new IllegalArgumentException("Execution attempt work units must not be negative");
        }
        attemptContentDigest = ExecutionValidation.requireDigest(attemptContentDigest, "Attempt content digest");
    }

    static ExecutionAttemptRecord completed(
            ExecutionAttemptId attemptId,
            ExecutionOperationId operationId,
            int attemptSequence,
            long tick,
            SchedulerInvocationIdentity invocationIdentity,
            SchedulerEffectIdentity effectIdentity,
            ExecutionStatus startingStatus,
            ExecutionStatus endingStatus,
            String handlerId,
            Optional<String> ownerResultIdentity,
            Optional<ExecutionFailure> failure,
            int workUnits
    ) {
        ExecutionAttemptRecord seed = new ExecutionAttemptRecord(
                ExecutionSchema.CURRENT_VERSION,
                attemptId,
                operationId,
                attemptSequence,
                tick,
                invocationIdentity,
                effectIdentity,
                startingStatus,
                endingStatus,
                handlerId,
                ownerResultIdentity,
                failure,
                workUnits,
                ExecutionValidation.zeroDigest()
        );
        return seed.withCalculatedDigest();
    }

    public ExecutionAttemptRecord withCalculatedDigest() {
        return new ExecutionAttemptRecord(
                schemaVersion,
                attemptId,
                operationId,
                attemptSequence,
                simulationTick,
                schedulerInvocationIdentity,
                schedulerEffectIdentity,
                startingStatus,
                endingStatus,
                handlerId,
                ownerResultIdentity,
                failure,
                workUnits,
                calculateDigest()
        );
    }

    public boolean digestMatches() {
        return attemptContentDigest.equals(calculateDigest());
    }

    public String calculateDigest() {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_attempt_record");
        digest.add(schemaVersion)
                .add(attemptId.value())
                .add(operationId.value())
                .add(attemptSequence)
                .add(simulationTick)
                .add(schedulerInvocationIdentity.value())
                .add(schedulerEffectIdentity.value())
                .add(startingStatus.name())
                .add(endingStatus.name())
                .add(handlerId)
                .add(ownerResultIdentity.isPresent());
        ownerResultIdentity.ifPresent(digest::add);
        digest.add(failure.isPresent());
        failure.ifPresent(value -> digest.add(value.code().name())
                .add(value.message())
                .add(value.referenceIdentity()));
        digest.add(workUnits);
        return digest.finish();
    }
}
