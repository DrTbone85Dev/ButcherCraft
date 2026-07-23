package com.butchercraft.world.allocation;

import java.util.List;

public record AllocationSetEvaluationResult(
        AllocationSetId allocationSetId,
        AllocationSetEvaluationOutcome outcome,
        List<AllocationRequirementEvaluation> requirementEvaluations,
        List<AllocationCommitmentDefinition> proposedCommitments,
        List<AllocationConflictRecord> conflicts,
        List<AllocationCycleFailure> failures,
        String ledgerBeforeDigest,
        String ledgerAfterDigest,
        int canonicalOrderingPosition
) implements Comparable<AllocationSetEvaluationResult> {
    public AllocationSetEvaluationResult {
        allocationSetId = AllocationValidation.required(
                allocationSetId,
                "allocationSetId"
        );
        outcome = AllocationValidation.required(outcome, "outcome");
        requirementEvaluations = AllocationValidation.required(
                requirementEvaluations,
                "requirementEvaluations"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "requirementEvaluation"
        )).sorted().toList();
        proposedCommitments = AllocationValidation.required(
                proposedCommitments,
                "proposedCommitments"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "proposedCommitment"
        )).sorted().toList();
        conflicts = AllocationValidation.required(conflicts, "conflicts").stream()
                .map(value -> AllocationValidation.required(value, "conflict"))
                .sorted()
                .distinct()
                .toList();
        failures = AllocationValidation.required(failures, "failures").stream()
                .map(value -> AllocationValidation.required(value, "failure"))
                .sorted()
                .distinct()
                .toList();
        ledgerBeforeDigest = AllocationCanonicalDigest.validate(
                ledgerBeforeDigest,
                "ledgerBeforeDigest"
        );
        ledgerAfterDigest = AllocationCanonicalDigest.validate(
                ledgerAfterDigest,
                "ledgerAfterDigest"
        );
        if (canonicalOrderingPosition < 0) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.ALLOCATION_SET,
                    allocationSetId.value(),
                    "Canonical ordering position must not be negative"
            );
        }
        boolean success = outcome == AllocationSetEvaluationOutcome.ALLOCATABLE;
        if (success != !proposedCommitments.isEmpty()
                || success == !failures.isEmpty()
                || success == ledgerBeforeDigest.equals(ledgerAfterDigest)) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.ALLOCATION_SET,
                    allocationSetId.value(),
                    "AllocationSet evaluation shape is inconsistent"
            );
        }
        if (success && proposedCommitments.size() != requirementEvaluations.size()) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INCOMPLETE_COMMITMENT_SET,
                    AllocationCycleFailureScope.ALLOCATION_SET,
                    allocationSetId.value(),
                    "Successful AllocationSet requires one Commitment per Requirement"
            );
        }
    }

    @Override
    public int compareTo(AllocationSetEvaluationResult other) {
        int position = Integer.compare(
                canonicalOrderingPosition,
                AllocationValidation.required(
                        other,
                        "other"
                ).canonicalOrderingPosition
        );
        return position != 0
                ? position
                : allocationSetId.compareTo(other.allocationSetId);
    }
}
