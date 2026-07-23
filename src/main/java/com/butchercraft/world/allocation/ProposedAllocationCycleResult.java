package com.butchercraft.world.allocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

record ProposedAllocationCycleResult(
        AllocationCycleId cycleId,
        List<AllocationSetId> canonicalSetOrder,
        List<AllocationSetEvaluationResult> evaluations,
        List<AllocationCommitmentDefinition> commitments,
        List<AllocationRuntimeTransitionRequest> transitions,
        List<AllocationLedgerEntryView> finalLedger,
        List<AllocationConflictRecord> conflicts,
        List<AllocationCycleFailure> failures,
        String finalLedgerDigest,
        String commitmentDigest
) {
    ProposedAllocationCycleResult {
        cycleId = AllocationValidation.required(cycleId, "cycleId");
        canonicalSetOrder = AllocationValidation.required(
                canonicalSetOrder,
                "canonicalSetOrder"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "allocationSetId"
        )).toList();
        evaluations = AllocationValidation.required(
                evaluations,
                "evaluations"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "evaluation"
        )).sorted().toList();
        commitments = AllocationValidation.required(
                commitments,
                "commitments"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "commitment"
        )).sorted().toList();
        transitions = AllocationValidation.required(
                transitions,
                "transitions"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "transition"
        )).sorted(java.util.Comparator.comparing(
                AllocationRuntimeTransitionRequest::allocationSetId
        )).toList();
        finalLedger = AllocationValidation.required(
                finalLedger,
                "finalLedger"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "ledgerEntry"
        )).sorted().toList();
        conflicts = AllocationValidation.required(conflicts, "conflicts").stream()
                .map(value -> AllocationValidation.required(value, "conflict"))
                .sorted().distinct().toList();
        failures = AllocationValidation.required(failures, "failures").stream()
                .map(value -> AllocationValidation.required(value, "failure"))
                .sorted().distinct().toList();
        finalLedgerDigest = AllocationCanonicalDigest.validate(
                finalLedgerDigest,
                "finalLedgerDigest"
        );
        commitmentDigest = AllocationCanonicalDigest.validate(
                commitmentDigest,
                "commitmentDigest"
        );
        if (!finalLedgerDigest.equals(
                WorkingCapacityLedger.digestEntries(finalLedger)
        ) || !commitmentDigest.equals(
                AllocationCycleDigestSupport.commitments(commitments)
        )) {
            throw invalid(cycleId, "Proposed result evidence digest is invalid");
        }
        if (!evaluations.stream()
                .map(AllocationSetEvaluationResult::allocationSetId)
                .toList().equals(canonicalSetOrder)) {
            throw invalid(cycleId, "Proposed result order is inconsistent");
        }
        if (transitions.stream()
                .map(AllocationRuntimeTransitionRequest::allocationSetId)
                .distinct().count() != transitions.size()) {
            throw invalid(cycleId, "Proposed result contains duplicate transitions");
        }
        if (finalLedger.stream().map(AllocationLedgerEntryView::capacityId)
                .distinct().count() != finalLedger.size()
                || finalLedger.stream().map(
                AllocationLedgerEntryView::capacityKey
        ).distinct().count() != finalLedger.size()) {
            throw invalid(cycleId, "Proposed result contains duplicate ledger entries");
        }
        validateCommitments(cycleId, evaluations, commitments);
    }

    private static void validateCommitments(
            AllocationCycleId cycleId,
            List<AllocationSetEvaluationResult> evaluations,
            List<AllocationCommitmentDefinition> commitments
    ) {
        Set<AllocationCommitmentId> ids = new HashSet<>();
        Set<RequirementId> requirements = new HashSet<>();
        java.util.Map<AllocationSetId, Integer> commitmentCounts =
                new java.util.TreeMap<>();
        for (AllocationCommitmentDefinition commitment : commitments) {
            if (!ids.add(commitment.id())
                    || !requirements.add(commitment.requirementId())
                    || !commitment.allocationCycleId().equals(cycleId)) {
                throw invalid(
                        cycleId,
                        "Proposed result contains duplicate or foreign Commitment"
                );
            }
            commitmentCounts.merge(commitment.allocationSetId(), 1, Integer::sum);
        }
        for (AllocationSetEvaluationResult evaluation : evaluations) {
            boolean successful = evaluation.outcome()
                    == AllocationSetEvaluationOutcome.ALLOCATABLE;
            int proposed = commitmentCounts.getOrDefault(
                    evaluation.allocationSetId(),
                    0
            );
            if (successful
                    ? proposed != evaluation.requirementEvaluations().size()
                    : proposed != 0) {
                throw invalid(
                        cycleId,
                        "Proposed Commitments do not match AllocationSet outcome"
                );
            }
        }
    }

    private static AllocationCycleValidationException invalid(
            AllocationCycleId cycleId,
            String message
    ) {
        return AllocationCycleValidation.failure(
                AllocationCycleFailureCode.INVALID_RESULT,
                AllocationCycleFailureScope.CYCLE,
                cycleId.value(),
                message
        );
    }
}
