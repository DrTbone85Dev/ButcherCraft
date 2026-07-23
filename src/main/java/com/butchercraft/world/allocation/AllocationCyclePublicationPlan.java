package com.butchercraft.world.allocation;

import java.util.List;
import java.util.TreeSet;

record AllocationCyclePublicationPlan(
        AllocationRegistry expectedDefinitions,
        AllocationRuntimeRegistry expectedRuntimes,
        List<AllocationCommitmentDefinition> commitments,
        List<AllocationRuntimeTransitionRequest> transitions,
        AllocationReport report,
        AllocationCycleTrace trace
) {
    AllocationCyclePublicationPlan {
        expectedDefinitions = AllocationValidation.required(
                expectedDefinitions,
                "expectedDefinitions"
        );
        expectedRuntimes = AllocationValidation.required(
                expectedRuntimes,
                "expectedRuntimes"
        );
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
        report = AllocationValidation.required(report, "report");
        trace = AllocationValidation.required(trace, "trace");
        if (!report.allocationCycleId().equals(trace.cycleId())
                || report.simulationTick() != trace.simulationTick()) {
            throw invalid(report.allocationCycleId(), "Report and trace Cycle mismatch");
        }
        TreeSet<AllocationCommitmentId> commitmentIds = commitments.stream()
                .map(AllocationCommitmentDefinition::id)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        if (!commitmentIds.equals(new TreeSet<>(report.commitmentIds()))) {
            throw invalid(
                    report.allocationCycleId(),
                    "Report Commitment ids do not match proposed Commitments"
            );
        }
    }

    private static AllocationCycleValidationException invalid(
            AllocationCycleId cycleId,
            String message
    ) {
        return AllocationCycleValidation.failure(
                AllocationCycleFailureCode.INVALID_RESULT,
                AllocationCycleFailureScope.PUBLICATION,
                cycleId.value(),
                message
        );
    }
}
