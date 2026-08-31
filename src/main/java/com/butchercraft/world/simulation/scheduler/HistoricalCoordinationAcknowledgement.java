package com.butchercraft.world.simulation.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Scheduler-owned evidence of an already-proven consequence. This is never executable Work. */
public record HistoricalCoordinationAcknowledgement(
        int schemaVersion,
        String acknowledgementIdentity,
        String executionOperationIdentity,
        SimulationWorkId schedulerWorkIdentity,
        SchedulerInvocationIdentity schedulerInvocationIdentity,
        SchedulerEffectIdentity schedulerEffectIdentity,
        String authorizationIdentity,
        String authorizationContentDigest,
        String domainEffectIdentity,
        String handlerContractIdentity,
        Optional<String> ownerSubsystemId,
        Optional<String> ownerResultIdentity,
        Optional<String> ownerResultContentDigest,
        String executionResultIdentity,
        String executionResultContentDigest,
        Optional<String> workstationInstanceIdentity,
        OptionalLong workstationInstanceGeneration,
        Optional<String> machineRunIdentity,
        OptionalLong childSequence,
        HistoricalCoordinationProof.TerminalOutcome terminalOutcome,
        long startedSimulationTick,
        long completedSimulationTick,
        List<HistoricalCoordinationSourceEvidence> sourceEvidence,
        String contentDigest
) implements Comparable<HistoricalCoordinationAcknowledgement> {
    private static final int RECOVERY_EVIDENCE_SCHEMA = 1;
    private static final String PREFIX = "butchercraft:historical_coordination_acknowledgement/v1/";

    public HistoricalCoordinationAcknowledgement {
        if (schemaVersion != RECOVERY_EVIDENCE_SCHEMA) {
            throw new IllegalArgumentException("Unsupported historical acknowledgement schema: " + schemaVersion);
        }
        acknowledgementIdentity = SchedulerValidation.requireId(
                acknowledgementIdentity,
                "Historical acknowledgement identity"
        );
        if (!acknowledgementIdentity.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Historical acknowledgement identity has unsupported prefix");
        }
        executionOperationIdentity = SchedulerValidation.requireId(
                executionOperationIdentity,
                "Historical acknowledgement Execution operation identity"
        );
        schedulerWorkIdentity = Objects.requireNonNull(schedulerWorkIdentity, "schedulerWorkIdentity");
        schedulerInvocationIdentity = Objects.requireNonNull(
                schedulerInvocationIdentity,
                "schedulerInvocationIdentity"
        );
        schedulerEffectIdentity = Objects.requireNonNull(schedulerEffectIdentity, "schedulerEffectIdentity");
        authorizationIdentity = SchedulerValidation.requireId(
                authorizationIdentity,
                "Historical acknowledgement authorization identity"
        );
        authorizationContentDigest = SchedulerIdentityDigest.requireDigest(
                authorizationContentDigest,
                "Historical acknowledgement authorization digest"
        );
        domainEffectIdentity = SchedulerValidation.requireId(
                domainEffectIdentity,
                "Historical acknowledgement domain effect identity"
        );
        handlerContractIdentity = SchedulerValidation.requireId(
                handlerContractIdentity,
                "Historical acknowledgement handler contract identity"
        );
        ownerSubsystemId = optionalId(ownerSubsystemId, "Historical acknowledgement owner subsystem id");
        ownerResultIdentity = optionalId(ownerResultIdentity, "Historical acknowledgement owner result identity");
        ownerResultContentDigest = optionalDigest(
                ownerResultContentDigest,
                "Historical acknowledgement owner result digest"
        );
        executionResultIdentity = SchedulerValidation.requireId(
                executionResultIdentity,
                "Historical acknowledgement Execution result identity"
        );
        executionResultContentDigest = SchedulerIdentityDigest.requireDigest(
                executionResultContentDigest,
                "Historical acknowledgement Execution result digest"
        );
        workstationInstanceIdentity = optionalId(
                workstationInstanceIdentity,
                "Historical acknowledgement Workstation Instance Identity"
        );
        workstationInstanceGeneration = Objects.requireNonNull(
                workstationInstanceGeneration,
                "workstationInstanceGeneration"
        );
        machineRunIdentity = optionalId(machineRunIdentity, "Historical acknowledgement Machine Run Identity");
        childSequence = Objects.requireNonNull(childSequence, "childSequence");
        terminalOutcome = Objects.requireNonNull(terminalOutcome, "terminalOutcome");
        startedSimulationTick = SchedulerValidation.requireTick(
                startedSimulationTick,
                "Historical acknowledgement start tick"
        );
        completedSimulationTick = SchedulerValidation.requireTick(
                completedSimulationTick,
                "Historical acknowledgement completion tick"
        );
        sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence").stream().sorted().toList();
        contentDigest = SchedulerIdentityDigest.requireDigest(
                contentDigest,
                "Historical acknowledgement content digest"
        );
        if (terminalOutcome == HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED
                && (ownerSubsystemId.isEmpty() || ownerResultIdentity.isEmpty()
                || ownerResultContentDigest.isEmpty())) {
            throw new IllegalArgumentException("Successful acknowledgement requires exact owner result evidence");
        }
        if (ownerResultIdentity.isPresent() != ownerResultContentDigest.isPresent()) {
            throw new IllegalArgumentException("Owner result identity and digest must be present together");
        }
        if (workstationInstanceIdentity.isPresent() != workstationInstanceGeneration.isPresent()) {
            throw new IllegalArgumentException("Workstation identity and generation must be present together");
        }
        if (machineRunIdentity.isPresent() != childSequence.isPresent()) {
            throw new IllegalArgumentException("Machine Run identity and child sequence must be present together");
        }
        String expectedDigest = calculateDigest(
                executionOperationIdentity,
                schedulerWorkIdentity,
                schedulerInvocationIdentity,
                schedulerEffectIdentity,
                authorizationIdentity,
                authorizationContentDigest,
                domainEffectIdentity,
                handlerContractIdentity,
                ownerSubsystemId,
                ownerResultIdentity,
                ownerResultContentDigest,
                executionResultIdentity,
                executionResultContentDigest,
                workstationInstanceIdentity,
                workstationInstanceGeneration,
                machineRunIdentity,
                childSequence,
                terminalOutcome,
                startedSimulationTick,
                completedSimulationTick,
                sourceEvidence
        );
        String expectedIdentity = PREFIX + SchedulerIdentityDigest.digestIdSuffix(
                expectedDigest,
                "Historical acknowledgement digest"
        );
        if (!contentDigest.equals(expectedDigest) || !acknowledgementIdentity.equals(expectedIdentity)) {
            throw new IllegalArgumentException("Historical acknowledgement is not canonical");
        }
    }

    public static HistoricalCoordinationAcknowledgement fromProof(HistoricalCoordinationProof proof) {
        Objects.requireNonNull(proof, "proof");
        List<String> missing = missingRequiredFields(proof);
        if (proof.conflictingEvidence() || !missing.isEmpty()) {
            throw new IllegalArgumentException("Historical acknowledgement proof is incomplete: " + missing);
        }
        String digest = calculateDigest(
                proof.executionOperationIdentity(),
                proof.schedulerWorkIdentity().orElseThrow(),
                proof.schedulerInvocationIdentity().orElseThrow(),
                proof.schedulerEffectIdentity().orElseThrow(),
                proof.authorizationIdentity().orElseThrow(),
                proof.authorizationContentDigest().orElseThrow(),
                proof.domainEffectIdentity().orElseThrow(),
                proof.handlerContractIdentity().orElseThrow(),
                proof.ownerSubsystemId(),
                proof.ownerResultIdentity(),
                proof.ownerResultContentDigest(),
                proof.executionResultIdentity().orElseThrow(),
                proof.executionResultContentDigest().orElseThrow(),
                proof.workstationInstanceIdentity(),
                proof.workstationInstanceGeneration(),
                proof.machineRunIdentity(),
                proof.childSequence(),
                proof.terminalOutcome().orElseThrow(),
                proof.startedSimulationTick().orElseThrow(),
                proof.completedSimulationTick().orElseThrow(),
                proof.sourceEvidence()
        );
        return new HistoricalCoordinationAcknowledgement(
                RECOVERY_EVIDENCE_SCHEMA,
                PREFIX + SchedulerIdentityDigest.digestIdSuffix(digest, "Historical acknowledgement digest"),
                proof.executionOperationIdentity(),
                proof.schedulerWorkIdentity().orElseThrow(),
                proof.schedulerInvocationIdentity().orElseThrow(),
                proof.schedulerEffectIdentity().orElseThrow(),
                proof.authorizationIdentity().orElseThrow(),
                proof.authorizationContentDigest().orElseThrow(),
                proof.domainEffectIdentity().orElseThrow(),
                proof.handlerContractIdentity().orElseThrow(),
                proof.ownerSubsystemId(),
                proof.ownerResultIdentity(),
                proof.ownerResultContentDigest(),
                proof.executionResultIdentity().orElseThrow(),
                proof.executionResultContentDigest().orElseThrow(),
                proof.workstationInstanceIdentity(),
                proof.workstationInstanceGeneration(),
                proof.machineRunIdentity(),
                proof.childSequence(),
                proof.terminalOutcome().orElseThrow(),
                proof.startedSimulationTick().orElseThrow(),
                proof.completedSimulationTick().orElseThrow(),
                proof.sourceEvidence(),
                digest
        );
    }

    static List<String> missingRequiredFields(HistoricalCoordinationProof proof) {
        List<String> missing = new ArrayList<>();
        require(proof.schedulerWorkIdentity().isPresent(), "Missing exact Scheduler Work Identity", missing);
        require(proof.schedulerInvocationIdentity().isPresent(), "Missing exact Scheduler Invocation Identity", missing);
        require(proof.schedulerEffectIdentity().isPresent(), "Missing exact Scheduler Effect Identity", missing);
        require(proof.authorizationIdentity().isPresent(), "Missing exact authorization identity", missing);
        require(proof.authorizationContentDigest().isPresent(), "Missing exact authorization digest", missing);
        require(proof.domainEffectIdentity().isPresent(), "Missing exact domain Effect Identity", missing);
        require(proof.handlerContractIdentity().isPresent(), "Missing exact Handler Contract Identity", missing);
        require(proof.executionResultIdentity().isPresent(), "Missing exact Execution result identity", missing);
        require(proof.executionResultContentDigest().isPresent(), "Missing exact Execution result digest", missing);
        require(proof.terminalOutcome().isPresent(), "Missing proven terminal outcome", missing);
        require(proof.schedulerInvocationStarted(), "Scheduler invocation was not proven to have started", missing);
        require(proof.startedSimulationTick().isPresent(), "Missing exact start tick", missing);
        require(proof.completedSimulationTick().isPresent(), "Missing exact completion tick", missing);
        require(!proof.sourceEvidence().isEmpty(), "Missing immutable source evidence", missing);
        require(proof.sourceEvidence().stream().anyMatch(evidence ->
                        evidence.ownerSubsystemId().equals("butchercraft:execution")),
                "Missing Execution source evidence", missing);
        if (proof.terminalOutcome().filter(HistoricalCoordinationProof.TerminalOutcome.SUCCEEDED::equals).isPresent()) {
            require(proof.ownerSubsystemId().isPresent(), "Missing owner subsystem identity", missing);
            require(proof.ownerResultIdentity().isPresent(), "Missing exact owner result identity", missing);
            require(proof.ownerResultContentDigest().isPresent(), "Missing exact owner result digest", missing);
            require(proof.workstationInstanceIdentity().isPresent(), "Missing Workstation Instance Identity", missing);
            require(proof.workstationInstanceGeneration().isPresent(), "Missing Workstation generation", missing);
            proof.ownerSubsystemId().ifPresent(owner -> require(
                    proof.sourceEvidence().stream().anyMatch(evidence -> evidence.ownerSubsystemId().equals(owner)),
                    "Missing owner-result source evidence", missing
            ));
        }
        return List.copyOf(missing);
    }

    private static void require(boolean condition, String reason, List<String> missing) {
        if (!condition) missing.add(reason);
    }

    private static String calculateDigest(
            String operation,
            SimulationWorkId work,
            SchedulerInvocationIdentity invocation,
            SchedulerEffectIdentity effect,
            String authorization,
            String authorizationDigest,
            String domainEffect,
            String handlerContract,
            Optional<String> owner,
            Optional<String> ownerResult,
            Optional<String> ownerResultDigest,
            String executionResult,
            String executionResultDigest,
            Optional<String> workstation,
            OptionalLong workstationGeneration,
            Optional<String> run,
            OptionalLong childSequence,
            HistoricalCoordinationProof.TerminalOutcome outcome,
            long startTick,
            long completionTick,
            List<HistoricalCoordinationSourceEvidence> evidence
    ) {
        SchedulerCanonicalDigest digest = SchedulerCanonicalDigest.create(
                "butchercraft:historical_coordination_acknowledgement"
        ).add(RECOVERY_EVIDENCE_SCHEMA)
                .add(operation)
                .add(work.value())
                .add(invocation.value())
                .add(effect.value())
                .add(authorization)
                .add(authorizationDigest)
                .add(domainEffect)
                .add(handlerContract)
                .add(owner.orElse(""))
                .add(ownerResult.orElse(""))
                .add(ownerResultDigest.orElse(""))
                .add(executionResult)
                .add(executionResultDigest)
                .add(workstation.orElse(""))
                .add(workstationGeneration.orElse(-1L))
                .add(run.orElse(""))
                .add(childSequence.orElse(-1L))
                .add(outcome.name())
                .add(startTick)
                .add(completionTick)
                .add(evidence.size());
        evidence.stream().sorted().forEach(reference -> digest
                .add(reference.ownerSubsystemId())
                .add(reference.sourceSnapshotIdentity())
                .add(reference.sourceSnapshotDigest())
                .add(reference.evidenceIdentity())
                .add(reference.evidenceContentDigest()));
        return digest.finish();
    }

    private static Optional<String> optionalId(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(candidate -> SchedulerValidation.requireId(candidate, label));
    }

    private static Optional<String> optionalDigest(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(candidate -> SchedulerIdentityDigest.requireDigest(candidate, label));
    }

    @Override
    public int compareTo(HistoricalCoordinationAcknowledgement other) {
        return acknowledgementIdentity.compareTo(Objects.requireNonNull(other, "other").acknowledgementIdentity);
    }
}
