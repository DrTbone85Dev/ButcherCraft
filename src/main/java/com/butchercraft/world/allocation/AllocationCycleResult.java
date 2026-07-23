package com.butchercraft.world.allocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record AllocationCycleResult(
        AllocationCycleId cycleId,
        long simulationTick,
        List<AllocationSetId> canonicalSetOrder,
        List<AllocationSetId> successfulSetIds,
        List<AllocationSetId> waitingSetIds,
        List<AllocationSetId> failedSetIds,
        List<AllocationSetEvaluationResult> setEvaluations,
        List<AllocationCommitmentDefinition> createdCommitments,
        List<AllocationLedgerEntryView> finalLedger,
        List<AllocationConflictRecord> conflicts,
        List<AllocationCycleFailure> failures,
        AllocationPublicationStatus publicationStatus,
        AllocationReport report,
        AllocationCycleSummary summary,
        AllocationCycleTrace trace,
        AllocationCycleDigests digests,
        int schemaVersion
) {
    public AllocationCycleResult {
        cycleId = AllocationValidation.required(cycleId, "cycleId");
        simulationTick = AllocationValidation.tick(
                simulationTick,
                "simulationTick"
        );
        canonicalSetOrder = immutable(canonicalSetOrder, "canonicalSetOrder");
        successfulSetIds = canonical(successfulSetIds, "successfulSetIds");
        waitingSetIds = canonical(waitingSetIds, "waitingSetIds");
        failedSetIds = canonical(failedSetIds, "failedSetIds");
        setEvaluations = AllocationValidation.required(
                setEvaluations,
                "setEvaluations"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "setEvaluation"
        )).sorted().toList();
        createdCommitments = AllocationValidation.required(
                createdCommitments,
                "createdCommitments"
        ).stream().map(value -> AllocationValidation.required(
                value,
                "createdCommitment"
        )).sorted().toList();
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
        publicationStatus = AllocationValidation.required(
                publicationStatus,
                "publicationStatus"
        );
        report = AllocationValidation.required(report, "report");
        summary = AllocationValidation.required(summary, "summary");
        trace = AllocationValidation.required(trace, "trace");
        digests = AllocationValidation.required(digests, "digests");
        schemaVersion = AllocationValidation.schema(schemaVersion);
        if (!cycleId.equals(AllocationCycleId.forTick(simulationTick))
                || !cycleId.equals(report.allocationCycleId())
                || !cycleId.equals(trace.cycleId())) {
            throw invalid(cycleId, "Allocation Cycle result identity is inconsistent");
        }
        validateOutcomes(
                canonicalSetOrder,
                successfulSetIds,
                waitingSetIds,
                failedSetIds,
                setEvaluations,
                cycleId
        );
        if (!digests.finalLedgerDigest().equals(
                WorkingCapacityLedger.digestEntries(finalLedger)
        ) || !digests.commitmentDigest().equals(
                AllocationCycleDigestSupport.commitments(createdCommitments)
        ) || !digests.reportDigest().equals(
                AllocationCycleDigestSupport.report(report)
        )) {
            throw invalid(cycleId, "Allocation Cycle evidence digest is inconsistent");
        }
        if (!report.successfulSetIds().equals(successfulSetIds)
                || !report.waitingSetIds().equals(waitingSetIds)
                || !combinedFailedReportSets(report).equals(failedSetIds)
                || !new java.util.TreeSet<>(report.commitmentIds()).equals(
                createdCommitments.stream()
                        .map(AllocationCommitmentDefinition::id)
                        .collect(java.util.stream.Collectors.toCollection(
                                java.util.TreeSet::new
                        ))
        )) {
            throw invalid(cycleId, "Allocation Cycle report is inconsistent with result");
        }
        if (summary.evaluatedSetCount() != setEvaluations.size()
                || summary.successfulSetCount() != successfulSetIds.size()
                || summary.waitingSetCount() != waitingSetIds.size()
                || summary.failedSetCount() != failedSetIds.size()
                || summary.createdCommitmentCount() != createdCommitments.size()
                || summary.conflictCount() != conflicts.size()) {
            throw invalid(cycleId, "Allocation Cycle summary is inconsistent with result");
        }
        String expected = AllocationCycleDigestSupport.result(
                cycleId,
                simulationTick,
                canonicalSetOrder,
                successfulSetIds,
                waitingSetIds,
                failedSetIds,
                failures,
                summary,
                trace,
                digests.inputDigest(),
                digests.orderingDigest(),
                digests.initialLedgerDigest(),
                digests.finalLedgerDigest(),
                digests.commitmentDigest(),
                digests.reportDigest(),
                digests.publicationDigest()
        );
        if (!digests.resultDigest().equals(expected)) {
            throw invalid(cycleId, "Allocation Cycle result digest is invalid");
        }
    }

    private static void validateOutcomes(
            List<AllocationSetId> ordered,
            List<AllocationSetId> successful,
            List<AllocationSetId> waiting,
            List<AllocationSetId> failed,
            List<AllocationSetEvaluationResult> evaluations,
            AllocationCycleId cycleId
    ) {
        Set<AllocationSetId> categorized = new HashSet<>();
        if (new HashSet<>(ordered).size() != ordered.size()
                || evaluations.stream()
                .map(AllocationSetEvaluationResult::allocationSetId)
                .distinct().count() != evaluations.size()) {
            throw invalid(cycleId, "Allocation Cycle result contains duplicate Sets");
        }
        for (List<AllocationSetId> category : List.of(
                successful,
                waiting,
                failed
        )) {
            for (AllocationSetId id : category) {
                if (!categorized.add(id)) {
                    throw invalid(cycleId, "AllocationSet has multiple result outcomes");
                }
            }
        }
        if (!categorized.equals(new HashSet<>(ordered))
                || !evaluations.stream()
                .map(AllocationSetEvaluationResult::allocationSetId)
                .toList()
                .equals(ordered)) {
            throw invalid(
                    cycleId,
                    "Allocation Cycle outcome categories do not cover canonical order"
            );
        }
    }

    private static List<AllocationSetId> combinedFailedReportSets(
            AllocationReport report
    ) {
        java.util.TreeSet<AllocationSetId> failed = new java.util.TreeSet<>(
                report.failedSetIds()
        );
        failed.addAll(report.rejectedSetIds());
        failed.addAll(report.expiredSetIds());
        return List.copyOf(failed);
    }

    private static <T> List<T> immutable(List<T> source, String field) {
        return AllocationValidation.required(source, field).stream()
                .map(value -> AllocationValidation.required(value, field))
                .toList();
    }

    private static <T extends Comparable<? super T>> List<T> canonical(
            List<T> source,
            String field
    ) {
        List<T> values = immutable(source, field).stream().sorted().toList();
        if (values.stream().distinct().count() != values.size()) {
            throw invalid(
                    AllocationCycleId.forTick(0L),
                    field + " contains duplicates"
            );
        }
        return values;
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
