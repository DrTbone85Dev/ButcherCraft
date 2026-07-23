package com.butchercraft.world.allocation;

import java.util.Optional;

public record AllocationRequirementEvaluation(
        RequirementId requirementId,
        AllocationRequirementEvaluationOutcome outcome,
        Optional<ResourceId> selectedResourceId,
        Optional<CapacityId> selectedCapacityId,
        Optional<AllocationQuantity> committedQuantity,
        Optional<AllocationCycleFailure> failure,
        Optional<AllocationConflictRecord> conflict,
        String ledgerBeforeDigest,
        String ledgerAfterDigest
) implements Comparable<AllocationRequirementEvaluation> {
    public AllocationRequirementEvaluation {
        requirementId = AllocationValidation.required(requirementId, "requirementId");
        outcome = AllocationValidation.required(outcome, "outcome");
        selectedResourceId = AllocationValidation.required(
                selectedResourceId,
                "selectedResourceId"
        );
        selectedCapacityId = AllocationValidation.required(
                selectedCapacityId,
                "selectedCapacityId"
        );
        committedQuantity = AllocationValidation.required(
                committedQuantity,
                "committedQuantity"
        );
        failure = AllocationValidation.required(failure, "failure");
        conflict = AllocationValidation.required(conflict, "conflict");
        ledgerBeforeDigest = AllocationCanonicalDigest.validate(
                ledgerBeforeDigest,
                "ledgerBeforeDigest"
        );
        ledgerAfterDigest = AllocationCanonicalDigest.validate(
                ledgerAfterDigest,
                "ledgerAfterDigest"
        );
        boolean satisfied = outcome == AllocationRequirementEvaluationOutcome.SATISFIED;
        boolean requiresFailure =
                outcome == AllocationRequirementEvaluationOutcome.WAITING
                        || outcome == AllocationRequirementEvaluationOutcome.REJECTED;
        if (satisfied != selectedResourceId.isPresent()
                || satisfied != selectedCapacityId.isPresent()
                || satisfied != committedQuantity.isPresent()
                || requiresFailure != failure.isPresent()
                || (!satisfied && !ledgerBeforeDigest.equals(ledgerAfterDigest))) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.REQUIREMENT,
                    requirementId.value(),
                    "Requirement evaluation shape is inconsistent"
            );
        }
        if (outcome == AllocationRequirementEvaluationOutcome.NOT_EVALUATED
                && (failure.isPresent() || conflict.isPresent())) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.REQUIREMENT,
                    requirementId.value(),
                    "Unevaluated Requirement cannot contain failure evidence"
            );
        }
    }

    @Override
    public int compareTo(AllocationRequirementEvaluation other) {
        return requirementId.compareTo(
                AllocationValidation.required(other, "other").requirementId
        );
    }
}
