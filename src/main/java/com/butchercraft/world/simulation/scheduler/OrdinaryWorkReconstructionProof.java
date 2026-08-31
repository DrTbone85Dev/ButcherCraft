package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/** Proof inventory for a possible ordinary historical Work record. It never inserts Work. */
public record OrdinaryWorkReconstructionProof(
        String candidateIdentity,
        Optional<SimulationWorkId> workIdentity,
        OptionalLong authoritativeSubmissionSequence,
        Optional<String> payloadDigest,
        Optional<String> handlerContractIdentity,
        Optional<String> stageIdentity,
        Optional<String> retryPolicyIdentity,
        Optional<SchedulerInvocationIdentity> invocationIdentity,
        Optional<SchedulerEffectIdentity> effectIdentity,
        Optional<String> ownerResultIdentity,
        Optional<HistoricalCoordinationProof.TerminalOutcome> terminalOutcome,
        OptionalLong startedSimulationTick,
        OptionalLong completedSimulationTick,
        boolean acknowledgementProofComplete,
        boolean conflictingEvidence
) implements Comparable<OrdinaryWorkReconstructionProof> {
    public OrdinaryWorkReconstructionProof {
        candidateIdentity = SchedulerValidation.requireId(candidateIdentity, "Ordinary Work candidate identity");
        workIdentity = Objects.requireNonNull(workIdentity, "workIdentity");
        authoritativeSubmissionSequence = Objects.requireNonNull(
                authoritativeSubmissionSequence,
                "authoritativeSubmissionSequence"
        );
        if (authoritativeSubmissionSequence.isPresent() && authoritativeSubmissionSequence.getAsLong() < 0L) {
            throw new IllegalArgumentException("Ordinary Work submission sequence must not be negative");
        }
        payloadDigest = optionalDigest(payloadDigest, "Ordinary Work payload digest");
        handlerContractIdentity = optionalId(handlerContractIdentity, "Ordinary Work handler contract identity");
        stageIdentity = optionalId(stageIdentity, "Ordinary Work stage identity");
        retryPolicyIdentity = optionalId(retryPolicyIdentity, "Ordinary Work retry policy identity");
        invocationIdentity = Objects.requireNonNull(invocationIdentity, "invocationIdentity");
        effectIdentity = Objects.requireNonNull(effectIdentity, "effectIdentity");
        ownerResultIdentity = optionalId(ownerResultIdentity, "Ordinary Work owner result identity");
        terminalOutcome = Objects.requireNonNull(terminalOutcome, "terminalOutcome");
        startedSimulationTick = tick(startedSimulationTick, "Ordinary Work start tick");
        completedSimulationTick = tick(completedSimulationTick, "Ordinary Work completion tick");
    }

    public Eligibility eligibility() {
        if (conflictingEvidence) return Eligibility.NOT_PROVABLE;
        if (workIdentity.isPresent()
                && authoritativeSubmissionSequence.isPresent()
                && payloadDigest.isPresent()
                && handlerContractIdentity.isPresent()
                && stageIdentity.isPresent()
                && retryPolicyIdentity.isPresent()
                && invocationIdentity.isPresent()
                && effectIdentity.isPresent()
                && ownerResultIdentity.isPresent()
                && terminalOutcome.isPresent()
                && startedSimulationTick.isPresent()
                && completedSimulationTick.isPresent()) {
            return Eligibility.ORDINARY_WORK_RECONSTRUCTABLE;
        }
        return acknowledgementProofComplete
                ? Eligibility.ACKNOWLEDGEMENT_ONLY
                : Eligibility.NOT_PROVABLE;
    }

    @Override
    public int compareTo(OrdinaryWorkReconstructionProof other) {
        return candidateIdentity.compareTo(Objects.requireNonNull(other, "other").candidateIdentity);
    }

    private static Optional<String> optionalId(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(candidate -> SchedulerValidation.requireId(candidate, label));
    }

    private static Optional<String> optionalDigest(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(candidate -> SchedulerIdentityDigest.requireDigest(candidate, label));
    }

    private static OptionalLong tick(OptionalLong value, String label) {
        Objects.requireNonNull(value, label);
        if (value.isPresent()) SchedulerValidation.requireTick(value.getAsLong(), label);
        return value;
    }

    public enum Eligibility {
        ORDINARY_WORK_RECONSTRUCTABLE,
        ACKNOWLEDGEMENT_ONLY,
        NOT_PROVABLE
    }
}
