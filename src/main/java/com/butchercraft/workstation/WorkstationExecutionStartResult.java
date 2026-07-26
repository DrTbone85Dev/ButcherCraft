package com.butchercraft.workstation;

import com.butchercraft.world.execution.ExecutionDomainEffectIdentity;
import com.butchercraft.world.execution.ExecutionOperationId;

import java.util.Objects;
import java.util.Optional;

public record WorkstationExecutionStartResult(
        boolean accepted,
        Optional<ExecutionOperationId> operationId,
        Optional<ExecutionDomainEffectIdentity> domainEffectIdentity,
        Optional<String> frozenInputIdentity,
        Optional<String> expectedOutputIdentity,
        Optional<String> sourceFreshnessIdentity,
        Optional<WorkstationFailure> failure
) {
    public WorkstationExecutionStartResult {
        operationId = Objects.requireNonNull(operationId, "operationId");
        domainEffectIdentity = Objects.requireNonNull(domainEffectIdentity, "domainEffectIdentity");
        frozenInputIdentity = Objects.requireNonNull(frozenInputIdentity, "frozenInputIdentity");
        expectedOutputIdentity = Objects.requireNonNull(expectedOutputIdentity, "expectedOutputIdentity");
        sourceFreshnessIdentity = Objects.requireNonNull(sourceFreshnessIdentity, "sourceFreshnessIdentity");
        failure = Objects.requireNonNull(failure, "failure");
        if (accepted && (operationId.isEmpty()
                || domainEffectIdentity.isEmpty()
                || frozenInputIdentity.isEmpty()
                || expectedOutputIdentity.isEmpty()
                || sourceFreshnessIdentity.isEmpty()
                || failure.isPresent())) {
            throw new IllegalArgumentException("Accepted Workstation Execution start result is incomplete");
        }
        if (!accepted && failure.isEmpty()) {
            throw new IllegalArgumentException("Rejected Workstation Execution start result requires a failure");
        }
    }

    public static WorkstationExecutionStartResult accepted(
            ExecutionOperationId operationId,
            ExecutionDomainEffectIdentity domainEffectIdentity,
            String frozenInputIdentity,
            String expectedOutputIdentity,
            String sourceFreshnessIdentity
    ) {
        return new WorkstationExecutionStartResult(
                true,
                Optional.of(Objects.requireNonNull(operationId, "operationId")),
                Optional.of(Objects.requireNonNull(domainEffectIdentity, "domainEffectIdentity")),
                Optional.of(Objects.requireNonNull(frozenInputIdentity, "frozenInputIdentity")),
                Optional.of(Objects.requireNonNull(expectedOutputIdentity, "expectedOutputIdentity")),
                Optional.of(Objects.requireNonNull(sourceFreshnessIdentity, "sourceFreshnessIdentity")),
                Optional.empty()
        );
    }

    public static WorkstationExecutionStartResult rejected(WorkstationFailure failure) {
        return new WorkstationExecutionStartResult(
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(Objects.requireNonNull(failure, "failure"))
        );
    }
}
