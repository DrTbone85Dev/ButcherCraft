package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record HistoricalCoordinationAssessment(
        String executionOperationIdentity,
        Eligibility eligibility,
        Optional<HistoricalCoordinationAcknowledgement> acknowledgement,
        List<String> reasons
) implements Comparable<HistoricalCoordinationAssessment> {
    public HistoricalCoordinationAssessment {
        executionOperationIdentity = SchedulerValidation.requireId(
                executionOperationIdentity,
                "Historical assessment operation identity"
        );
        eligibility = Objects.requireNonNull(eligibility, "eligibility");
        acknowledgement = Objects.requireNonNull(acknowledgement, "acknowledgement");
        reasons = Objects.requireNonNull(reasons, "reasons").stream()
                .map(reason -> SchedulerValidation.requireText(reason, "Historical assessment reason", 2_048))
                .sorted()
                .toList();
        if ((eligibility == Eligibility.ACKNOWLEDGEMENT_ELIGIBLE) != acknowledgement.isPresent()) {
            throw new IllegalArgumentException("Historical assessment acknowledgement presence is inconsistent");
        }
        if (eligibility != Eligibility.ACKNOWLEDGEMENT_ELIGIBLE && reasons.isEmpty()) {
            throw new IllegalArgumentException("Ineligible historical assessment requires an explanation");
        }
    }

    public static HistoricalCoordinationAssessment evaluate(HistoricalCoordinationProof proof) {
        Objects.requireNonNull(proof, "proof");
        if (proof.conflictingEvidence()) {
            return rejected(proof, Eligibility.CONFLICTING_EVIDENCE, "Conflicting immutable owner evidence");
        }
        List<String> missing = HistoricalCoordinationAcknowledgement.missingRequiredFields(proof);
        if (!missing.isEmpty()) {
            return new HistoricalCoordinationAssessment(
                    proof.executionOperationIdentity(),
                    Eligibility.INCOMPLETE_EVIDENCE,
                    Optional.empty(),
                    missing
            );
        }
        return new HistoricalCoordinationAssessment(
                proof.executionOperationIdentity(),
                Eligibility.ACKNOWLEDGEMENT_ELIGIBLE,
                Optional.of(HistoricalCoordinationAcknowledgement.fromProof(proof)),
                List.of()
        );
    }

    private static HistoricalCoordinationAssessment rejected(
            HistoricalCoordinationProof proof,
            Eligibility eligibility,
            String reason
    ) {
        return new HistoricalCoordinationAssessment(
                proof.executionOperationIdentity(),
                eligibility,
                Optional.empty(),
                List.of(reason)
        );
    }

    @Override
    public int compareTo(HistoricalCoordinationAssessment other) {
        return executionOperationIdentity.compareTo(Objects.requireNonNull(other, "other").executionOperationIdentity);
    }

    public enum Eligibility {
        ACKNOWLEDGEMENT_ELIGIBLE,
        INCOMPLETE_EVIDENCE,
        CONFLICTING_EVIDENCE
    }
}
