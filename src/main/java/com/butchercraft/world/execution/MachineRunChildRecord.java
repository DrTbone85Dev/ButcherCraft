package com.butchercraft.world.execution;

import java.util.Objects;
import java.util.Optional;

public record MachineRunChildRecord(
        int schemaVersion,
        String childIdentity,
        MachineRunIdentity runIdentity,
        long sequence,
        String workstationInstanceIdentity,
        ExecutionOperationId operationId,
        String authorizationContentDigest,
        String operationType,
        String sourceFreshnessIdentity,
        String configurationIdentity,
        MachineRunChildState state,
        long preparedSimulationTick,
        long lastUpdatedSimulationTick,
        Optional<String> terminalEvidenceIdentity,
        Optional<MachineRunResultCode> failureCode,
        String contentDigest
) implements Comparable<MachineRunChildRecord> {
    private static final String PREFIX = "butchercraft:machine_run_child/v1/";

    public MachineRunChildRecord {
        if (schemaVersion != MachineRunSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Machine Run child schema: " + schemaVersion);
        }
        childIdentity = ExecutionValidation.requireId(childIdentity, "Machine Run child identity");
        if (!childIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Machine Run child identity has an unsupported prefix");
        }
        runIdentity = Objects.requireNonNull(runIdentity, "runIdentity");
        if (sequence <= 0L) throw new IllegalArgumentException("Machine Run child sequence must be positive");
        workstationInstanceIdentity = ExecutionValidation.requireId(
                workstationInstanceIdentity,
                "Machine Run child Workstation Instance Identity"
        );
        operationId = Objects.requireNonNull(operationId, "operationId");
        authorizationContentDigest = ExecutionValidation.requireDigest(
                authorizationContentDigest,
                "Machine Run child authorization digest"
        );
        operationType = ExecutionValidation.requireId(operationType, "Machine Run child operation type");
        sourceFreshnessIdentity = ExecutionValidation.requireId(
                sourceFreshnessIdentity,
                "Machine Run child source freshness identity"
        );
        configurationIdentity = ExecutionValidation.requireId(
                configurationIdentity,
                "Machine Run child configuration identity"
        );
        state = Objects.requireNonNull(state, "state");
        preparedSimulationTick = ExecutionValidation.requireTick(
                preparedSimulationTick,
                "Machine Run child prepared tick"
        );
        lastUpdatedSimulationTick = ExecutionValidation.requireTick(
                lastUpdatedSimulationTick,
                "Machine Run child update tick"
        );
        if (lastUpdatedSimulationTick < preparedSimulationTick) {
            throw new IllegalArgumentException("Machine Run child update tick precedes preparation");
        }
        terminalEvidenceIdentity = Objects.requireNonNull(terminalEvidenceIdentity, "terminalEvidenceIdentity")
                .map(value -> ExecutionValidation.requireId(value, "Machine Run child terminal evidence identity"));
        failureCode = Objects.requireNonNull(failureCode, "failureCode");
        if (!state.terminal() && (terminalEvidenceIdentity.isPresent() || failureCode.isPresent())) {
            throw new IllegalArgumentException("Nonterminal Machine Run child cannot have terminal evidence");
        }
        if (state == MachineRunChildState.COMPLETED && terminalEvidenceIdentity.isEmpty()) {
            throw new IllegalArgumentException("Completed Machine Run child requires result evidence");
        }
        contentDigest = ExecutionValidation.requireDigest(contentDigest, "Machine Run child content digest");
        if (!contentDigest.equals(contentDigest(
                childIdentity,
                state,
                lastUpdatedSimulationTick,
                terminalEvidenceIdentity,
                failureCode
        ))) {
            throw new IllegalArgumentException("Machine Run child content digest mismatch");
        }
        if (!childIdentity.equals(PREFIX + ExecutionValidation.digestIdSuffix(identityDigest(
                runIdentity,
                sequence,
                workstationInstanceIdentity,
                operationId,
                authorizationContentDigest,
                operationType,
                sourceFreshnessIdentity,
                configurationIdentity
        )))) {
            throw new IllegalArgumentException("Machine Run child identity is not canonical");
        }
    }

    public static MachineRunChildRecord prepared(
            MachineRunIdentity runIdentity,
            long sequence,
            String workstationInstanceIdentity,
            ExecutionAuthorizationEvidence authorization,
            long tick
    ) {
        ExecutionOperationId operationId = ExecutionOperationId.derive(authorization);
        String identityDigest = identityDigest(
                runIdentity,
                sequence,
                workstationInstanceIdentity,
                operationId,
                authorization.authorizationContentDigest(),
                authorization.operationType(),
                authorization.sourceFreshnessIdentity(),
                authorization.configurationIdentity()
        );
        String identity = PREFIX + ExecutionValidation.digestIdSuffix(identityDigest);
        return new MachineRunChildRecord(
                MachineRunSchema.CURRENT_VERSION,
                identity,
                runIdentity,
                sequence,
                workstationInstanceIdentity,
                operationId,
                authorization.authorizationContentDigest(),
                authorization.operationType(),
                authorization.sourceFreshnessIdentity(),
                authorization.configurationIdentity(),
                MachineRunChildState.PREPARED,
                tick,
                tick,
                Optional.empty(),
                Optional.empty(),
                contentDigest(identity, MachineRunChildState.PREPARED, tick, Optional.empty(), Optional.empty())
        );
    }

    public MachineRunChildRecord admitted(long tick) {
        if (state == MachineRunChildState.ADMITTED) return this;
        if (state != MachineRunChildState.PREPARED) {
            throw new IllegalStateException("Only a prepared Machine Run child may be admitted");
        }
        return transition(MachineRunChildState.ADMITTED, tick, Optional.empty(), Optional.empty());
    }

    public MachineRunChildRecord terminal(
            MachineRunChildState terminalState,
            long tick,
            Optional<String> evidenceIdentity,
            Optional<MachineRunResultCode> failure
    ) {
        if (!terminalState.terminal()) {
            throw new IllegalArgumentException("Machine Run child terminal state is required");
        }
        if (state.terminal()) {
            if (state == terminalState
                    && terminalEvidenceIdentity.equals(evidenceIdentity)
                    && failureCode.equals(failure)) return this;
            throw new IllegalStateException("Machine Run child already has a conflicting terminal result");
        }
        return transition(terminalState, tick, evidenceIdentity, failure);
    }

    private MachineRunChildRecord transition(
            MachineRunChildState next,
            long tick,
            Optional<String> evidence,
            Optional<MachineRunResultCode> failure
    ) {
        if (tick < lastUpdatedSimulationTick) {
            throw new IllegalArgumentException("Machine Run child transition tick cannot move backward");
        }
        return new MachineRunChildRecord(
                schemaVersion,
                childIdentity,
                runIdentity,
                sequence,
                workstationInstanceIdentity,
                operationId,
                authorizationContentDigest,
                operationType,
                sourceFreshnessIdentity,
                configurationIdentity,
                next,
                preparedSimulationTick,
                tick,
                evidence,
                failure,
                contentDigest(childIdentity, next, tick, evidence, failure)
        );
    }

    private String identityDigest() {
        return identityDigest(
                runIdentity,
                sequence,
                workstationInstanceIdentity,
                operationId,
                authorizationContentDigest,
                operationType,
                sourceFreshnessIdentity,
                configurationIdentity
        );
    }

    private static String identityDigest(
            MachineRunIdentity runIdentity,
            long sequence,
            String workstationInstanceIdentity,
            ExecutionOperationId operationId,
            String authorizationContentDigest,
            String operationType,
            String sourceFreshnessIdentity,
            String configurationIdentity
    ) {
        return ExecutionCanonicalDigest.create("butchercraft:machine_run_child_identity")
                .add(MachineRunSchema.CURRENT_VERSION)
                .add(runIdentity.value())
                .add(sequence)
                .add(workstationInstanceIdentity)
                .add(operationId.value())
                .add(authorizationContentDigest)
                .add(operationType)
                .add(sourceFreshnessIdentity)
                .add(configurationIdentity)
                .finish();
    }

    public String calculateContentDigest() {
        return contentDigest(childIdentity, state, lastUpdatedSimulationTick, terminalEvidenceIdentity, failureCode);
    }

    private static String contentDigest(
            String childIdentity,
            MachineRunChildState state,
            long tick,
            Optional<String> evidence,
            Optional<MachineRunResultCode> failure
    ) {
        ExecutionCanonicalDigest digest = ExecutionCanonicalDigest.create("butchercraft:machine_run_child_record")
                .add(MachineRunSchema.CURRENT_VERSION)
                .add(childIdentity)
                .add(state.serializedName())
                .add(tick)
                .add(evidence.isPresent());
        evidence.ifPresent(digest::add);
        digest.add(failure.isPresent());
        failure.ifPresent(value -> digest.add(value.serializedName()));
        return digest.finish();
    }

    @Override
    public int compareTo(MachineRunChildRecord other) {
        int sequenceComparison = Long.compare(sequence, other.sequence);
        return sequenceComparison != 0 ? sequenceComparison : childIdentity.compareTo(other.childIdentity);
    }
}
