package com.butchercraft.world.execution;

import com.butchercraft.world.simulation.scheduler.SchedulerEffectIdentity;
import com.butchercraft.world.simulation.scheduler.SchedulerInvocationIdentity;

import java.util.Objects;
import java.util.Optional;

public record ExecutionResultEvidence(
        int schemaVersion,
        String evidenceIdentity,
        ExecutionOperationId operationId,
        ExecutionStatus terminalStatus,
        String authorizationIdentity,
        String authorizationContentDigest,
        String frozenInputIdentity,
        ExecutionDomainEffectIdentity domainEffectIdentity,
        Optional<SchedulerInvocationIdentity> schedulerInvocationIdentity,
        Optional<SchedulerEffectIdentity> schedulerEffectIdentity,
        Optional<ExecutionOwnerResultEvidence> ownerResultEvidence,
        Optional<ExecutionFailure> failure,
        String resultContentDigest
) {
    public ExecutionResultEvidence {
        schemaVersion = ExecutionValidation.requireSchema(schemaVersion, "Execution result evidence");
        evidenceIdentity = ExecutionValidation.requireId(evidenceIdentity, "Execution result evidence identity");
        operationId = Objects.requireNonNull(operationId, "operationId");
        terminalStatus = Objects.requireNonNull(terminalStatus, "terminalStatus");
        if (!terminalStatus.terminal()) {
            throw new IllegalArgumentException("Execution result evidence requires a terminal status");
        }
        authorizationIdentity = ExecutionValidation.requireId(authorizationIdentity, "Authorization identity");
        authorizationContentDigest = ExecutionValidation.requireDigest(
                authorizationContentDigest,
                "Authorization content digest"
        );
        frozenInputIdentity = ExecutionValidation.requireId(frozenInputIdentity, "Frozen input identity");
        domainEffectIdentity = Objects.requireNonNull(domainEffectIdentity, "domainEffectIdentity");
        schedulerInvocationIdentity = Objects.requireNonNull(schedulerInvocationIdentity, "schedulerInvocationIdentity");
        schedulerEffectIdentity = Objects.requireNonNull(schedulerEffectIdentity, "schedulerEffectIdentity");
        ownerResultEvidence = Objects.requireNonNull(ownerResultEvidence, "ownerResultEvidence");
        ownerResultEvidence.ifPresent(result -> {
            if (!result.digestMatches()) {
                throw new IllegalArgumentException("Owner result evidence digest mismatch");
            }
        });
        failure = Objects.requireNonNull(failure, "failure");
        resultContentDigest = ExecutionValidation.requireDigest(resultContentDigest, "Execution result content digest");
        if (terminalStatus == ExecutionStatus.SUCCEEDED && ownerResultEvidence.isEmpty()) {
            throw new IllegalArgumentException("Successful Execution result requires owner result evidence");
        }
        if (terminalStatus != ExecutionStatus.SUCCEEDED && failure.isEmpty()) {
            throw new IllegalArgumentException("Unsuccessful Execution result requires failure evidence");
        }
    }

    static ExecutionResultEvidence fromTerminal(
            ExecutionOperationSnapshot operation,
            Optional<SchedulerInvocationIdentity> schedulerInvocationIdentity,
            Optional<SchedulerEffectIdentity> schedulerEffectIdentity,
            Optional<ExecutionOwnerResultEvidence> ownerResultEvidence,
            Optional<ExecutionFailure> failure
    ) {
        ExecutionResultEvidence seed = new ExecutionResultEvidence(
                ExecutionSchema.CURRENT_VERSION,
                "butchercraft:execution_result_evidence/v" + ExecutionSchema.CURRENT_VERSION + "/"
                        + "0".repeat(64),
                operation.operationId(),
                operation.status(),
                operation.authorizationEvidence().authorizationIdentity(),
                operation.authorizationEvidence().authorizationContentDigest(),
                operation.authorizationEvidence().frozenInputIdentity(),
                operation.domainEffectIdentity(),
                schedulerInvocationIdentity,
                schedulerEffectIdentity,
                ownerResultEvidence,
                failure,
                ExecutionValidation.zeroDigest()
        );
        String digest = seed.calculateDigest();
        return new ExecutionResultEvidence(
                ExecutionSchema.CURRENT_VERSION,
                "butchercraft:execution_result_evidence/v" + ExecutionSchema.CURRENT_VERSION + "/"
                        + ExecutionValidation.digestIdSuffix(digest),
                seed.operationId,
                seed.terminalStatus,
                seed.authorizationIdentity,
                seed.authorizationContentDigest,
                seed.frozenInputIdentity,
                seed.domainEffectIdentity,
                seed.schedulerInvocationIdentity,
                seed.schedulerEffectIdentity,
                seed.ownerResultEvidence,
                seed.failure,
                digest
        );
    }

    public boolean digestMatches() {
        return resultContentDigest.equals(calculateDigest());
    }

    public String calculateDigest() {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:execution_result_evidence");
        digest.add(schemaVersion)
                .add(operationId.value())
                .add(terminalStatus.name())
                .add(authorizationIdentity)
                .add(authorizationContentDigest)
                .add(frozenInputIdentity)
                .add(domainEffectIdentity.value())
                .add(schedulerInvocationIdentity.isPresent());
        schedulerInvocationIdentity.ifPresent(value -> digest.add(value.value()));
        digest.add(schedulerEffectIdentity.isPresent());
        schedulerEffectIdentity.ifPresent(value -> digest.add(value.value()));
        digest.add(ownerResultEvidence.isPresent());
        ownerResultEvidence.ifPresent(value -> digest.add(value.contentDigest()));
        digest.add(failure.isPresent());
        failure.ifPresent(value -> digest.add(value.code().name())
                .add(value.message())
                .add(value.referenceIdentity()));
        return digest.finish();
    }
}
