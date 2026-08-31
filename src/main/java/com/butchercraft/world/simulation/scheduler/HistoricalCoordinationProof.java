package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Owner-supplied proof candidate. Missing fields remain explicit and are never inferred. */
public record HistoricalCoordinationProof(
        String executionOperationIdentity,
        Optional<SimulationWorkId> schedulerWorkIdentity,
        Optional<SchedulerInvocationIdentity> schedulerInvocationIdentity,
        Optional<SchedulerEffectIdentity> schedulerEffectIdentity,
        Optional<String> authorizationIdentity,
        Optional<String> authorizationContentDigest,
        Optional<String> domainEffectIdentity,
        Optional<String> handlerContractIdentity,
        Optional<String> ownerSubsystemId,
        Optional<String> ownerResultIdentity,
        Optional<String> ownerResultContentDigest,
        Optional<String> executionResultIdentity,
        Optional<String> executionResultContentDigest,
        Optional<String> workstationInstanceIdentity,
        OptionalLong workstationInstanceGeneration,
        Optional<String> machineRunIdentity,
        OptionalLong childSequence,
        Optional<TerminalOutcome> terminalOutcome,
        boolean schedulerInvocationStarted,
        OptionalLong startedSimulationTick,
        OptionalLong completedSimulationTick,
        List<HistoricalCoordinationSourceEvidence> sourceEvidence,
        boolean conflictingEvidence
) implements Comparable<HistoricalCoordinationProof> {
    public HistoricalCoordinationProof {
        executionOperationIdentity = SchedulerValidation.requireId(
                executionOperationIdentity,
                "Historical Execution operation identity"
        );
        schedulerWorkIdentity = Objects.requireNonNull(schedulerWorkIdentity, "schedulerWorkIdentity");
        schedulerInvocationIdentity = Objects.requireNonNull(
                schedulerInvocationIdentity,
                "schedulerInvocationIdentity"
        );
        schedulerEffectIdentity = Objects.requireNonNull(schedulerEffectIdentity, "schedulerEffectIdentity");
        authorizationIdentity = canonicalId(authorizationIdentity, "Historical authorization identity");
        authorizationContentDigest = digest(authorizationContentDigest, "Historical authorization digest");
        domainEffectIdentity = canonicalId(domainEffectIdentity, "Historical domain effect identity");
        handlerContractIdentity = canonicalId(handlerContractIdentity, "Historical handler contract identity");
        ownerSubsystemId = canonicalId(ownerSubsystemId, "Historical owner subsystem id");
        ownerResultIdentity = canonicalId(ownerResultIdentity, "Historical owner result identity");
        ownerResultContentDigest = digest(ownerResultContentDigest, "Historical owner result digest");
        executionResultIdentity = canonicalId(executionResultIdentity, "Historical Execution result identity");
        executionResultContentDigest = digest(
                executionResultContentDigest,
                "Historical Execution result digest"
        );
        workstationInstanceIdentity = canonicalId(
                workstationInstanceIdentity,
                "Historical Workstation Instance Identity"
        );
        workstationInstanceGeneration = nonNegativeOptional(
                workstationInstanceGeneration,
                "Historical Workstation instance generation"
        );
        machineRunIdentity = canonicalId(machineRunIdentity, "Historical Machine Run Identity");
        childSequence = positiveOptional(childSequence, "Historical Machine Run child sequence");
        terminalOutcome = Objects.requireNonNull(terminalOutcome, "terminalOutcome");
        startedSimulationTick = tick(startedSimulationTick, "Historical start tick");
        completedSimulationTick = tick(completedSimulationTick, "Historical completion tick");
        if (startedSimulationTick.isPresent() && completedSimulationTick.isPresent()
                && completedSimulationTick.getAsLong() < startedSimulationTick.getAsLong()) {
            throw new IllegalArgumentException("Historical completion tick precedes start tick");
        }
        sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence").stream()
                .map(value -> Objects.requireNonNull(value, "sourceEvidence entry"))
                .sorted()
                .toList();
        if (machineRunIdentity.isPresent() != childSequence.isPresent()) {
            throw new IllegalArgumentException("Machine Run identity and child sequence must be present together");
        }
        if (workstationInstanceIdentity.isPresent() != workstationInstanceGeneration.isPresent()) {
            throw new IllegalArgumentException(
                    "Workstation Instance Identity and generation must be present together"
            );
        }
    }

    @Override
    public int compareTo(HistoricalCoordinationProof other) {
        return executionOperationIdentity.compareTo(Objects.requireNonNull(other, "other").executionOperationIdentity);
    }

    private static Optional<String> canonicalId(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(candidate -> SchedulerValidation.requireId(candidate, label));
    }

    private static Optional<String> digest(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(candidate -> SchedulerIdentityDigest.requireDigest(candidate, label));
    }

    private static OptionalLong tick(OptionalLong value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isPresent()) SchedulerValidation.requireTick(value.getAsLong(), label);
        return value;
    }

    private static OptionalLong nonNegativeOptional(OptionalLong value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isPresent() && value.getAsLong() < 0L) {
            throw new IllegalArgumentException(label + " must not be negative");
        }
        return value;
    }

    private static OptionalLong positiveOptional(OptionalLong value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isPresent() && value.getAsLong() <= 0L) {
            throw new IllegalArgumentException(label + " must be positive");
        }
        return value;
    }

    public enum TerminalOutcome {
        SUCCEEDED,
        FAILED
    }
}
