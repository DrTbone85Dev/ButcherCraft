package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

public final class AllocationCycleExecutor {
    private final AllocationSetEvaluator evaluator = new AllocationSetEvaluator();
    private boolean executing;

    public synchronized AllocationCycleOperationResult<AllocationCycleResult> execute(
            AllocationCycleInput input,
            AllocationRuntimeService runtimeService
    ) {
        return execute(input, runtimeService, AllocationPublicationFault.NONE);
    }

    synchronized AllocationCycleOperationResult<AllocationCycleResult> execute(
            AllocationCycleInput input,
            AllocationRuntimeService runtimeService,
            AllocationPublicationFault fault
    ) {
        if (executing) {
            return rejected(
                    AllocationCycleFailureCode.RECURSIVE_ALLOCATION,
                    AllocationCycleFailureScope.CYCLE,
                    "allocation_cycle",
                    "Recursive Allocation Cycle execution is prohibited"
            );
        }
        executing = true;
        try {
            return executeOnce(
                    AllocationValidation.required(input, "input"),
                    AllocationValidation.required(runtimeService, "runtimeService"),
                    AllocationValidation.required(fault, "fault")
            );
        } catch (AllocationCycleValidationException exception) {
            return AllocationCycleOperationResult.rejected(exception.failures());
        } catch (RuntimeException exception) {
            return rejected(
                    AllocationCycleFailureCode.STRUCTURAL_VALIDATION_FAILED,
                    AllocationCycleFailureScope.CYCLE,
                    "allocation_cycle",
                    exception.getMessage() == null
                            ? "Allocation Cycle failed"
                            : exception.getMessage()
            );
        } finally {
            executing = false;
        }
    }

    private AllocationCycleOperationResult<AllocationCycleResult> executeOnce(
            AllocationCycleInput input,
            AllocationRuntimeService runtimeService,
            AllocationPublicationFault fault
    ) {
        String inputDigest = AllocationCycleDigestSupport.input(input);
        List<AllocationCommitmentDefinition> activeCommitments =
                input.activeCommitments();
        WorkingCapacityLedger ledger = WorkingCapacityLedger.from(input);
        String initialLedgerDigest = ledger.digest();
        List<AllocationSetDefinition> orderedSets = orderedSets(input);
        String orderingDigest = AllocationCycleDigestSupport.ordering(
                orderedSets,
                input.definitions()
        );

        List<AllocationSetEvaluationResult> evaluations = new ArrayList<>();
        for (int index = 0; index < orderedSets.size(); index++) {
            evaluations.add(evaluator.evaluate(
                    index,
                    orderedSets.get(index),
                    input,
                    ledger
            ));
        }
        List<AllocationCommitmentDefinition> commitments = evaluations.stream()
                .flatMap(result -> result.proposedCommitments().stream())
                .sorted()
                .toList();
        List<AllocationConflictRecord> conflicts = evaluations.stream()
                .flatMap(result -> result.conflicts().stream())
                .sorted()
                .distinct()
                .toList();
        List<AllocationCycleFailure> failures = evaluations.stream()
                .flatMap(result -> result.failures().stream())
                .sorted()
                .distinct()
                .toList();
        validateProposedResult(input, evaluations, commitments, ledger);

        List<AllocationSetId> orderedIds = orderedSets.stream()
                .map(AllocationSetDefinition::id)
                .toList();
        List<AllocationSetId> successful = outcomes(
                evaluations,
                AllocationSetEvaluationOutcome.ALLOCATABLE
        );
        List<AllocationSetId> waiting = outcomes(
                evaluations,
                AllocationSetEvaluationOutcome.WAITING
        );
        List<AllocationSetId> failed = outcomes(
                evaluations,
                AllocationSetEvaluationOutcome.FAILED
        );
        List<AllocationRuntimeTransitionRequest> transitions =
                transitions(input, evaluations);
        ProposedAllocationCycleResult proposal = new ProposedAllocationCycleResult(
                input.context().cycleId(),
                orderedIds,
                evaluations,
                commitments,
                transitions,
                ledger.entries(),
                conflicts,
                failures,
                ledger.digest(),
                AllocationCycleDigestSupport.commitments(commitments)
        );
        Map<AllocationCyclePhase, Long> operationCounts = operationCounts(
                input,
                activeCommitments,
                evaluations,
                commitments,
                transitions,
                conflicts
        );
        long totalOperations = operationCounts.values().stream()
                .reduce(0L, (first, second) ->
                        AllocationCycleValidation.add(
                                first,
                                second,
                                "cycleOperations"
                        ));
        AllocationReport report = report(
                input,
                evaluations,
                commitments,
                conflicts,
                failures,
                ledger,
                operationCounts,
                totalOperations
        );
        String finalLedgerDigest = proposal.finalLedgerDigest();
        String commitmentDigest = proposal.commitmentDigest();
        String reportDigest = AllocationCycleDigestSupport.report(report);
        String publicationPlanDigest = publicationPlanDigest(
                commitments,
                transitions,
                reportDigest
        );
        AllocationCycleTrace trace = trace(
                input,
                operationCounts,
                inputDigest,
                initialLedgerDigest,
                orderingDigest,
                finalLedgerDigest,
                commitmentDigest,
                publicationPlanDigest,
                reportDigest
        );
        AllocationCyclePublicationPlan plan = new AllocationCyclePublicationPlan(
                input.definitions(),
                input.runtimes(),
                commitments,
                transitions,
                report,
                trace
        );
        AllocationCycleOperationResult<AllocationPublishedCycle> publication =
                runtimeService.publishCycle(plan, fault);
        if (!publication.accepted()) {
            return AllocationCycleOperationResult.rejected(publication.failures());
        }
        AllocationPublishedCycle published = publication.value().orElseThrow();
        String publicationDigest = AllocationCycleDigestSupport.publication(
                published.definitions(),
                published.runtimes(),
                published.reports(),
                published.history(),
                published.traces()
        );
        AllocationCycleSummary summary = new AllocationCycleSummary(
                input.resources().size(),
                input.capacities().size(),
                activeCommitments.size(),
                evaluations.size(),
                successful.size(),
                waiting.size(),
                failed.size(),
                commitments.size(),
                conflicts.size(),
                totalOperations,
                AllocationSchema.CURRENT_VERSION
        );
        String resultDigest = AllocationCycleDigestSupport.result(
                input.context().cycleId(),
                input.context().simulationTick(),
                orderedIds,
                successful,
                waiting,
                failed,
                failures,
                summary,
                trace,
                inputDigest,
                orderingDigest,
                initialLedgerDigest,
                finalLedgerDigest,
                commitmentDigest,
                reportDigest,
                publicationDigest
        );
        AllocationCycleDigests digests = new AllocationCycleDigests(
                inputDigest,
                orderingDigest,
                initialLedgerDigest,
                finalLedgerDigest,
                commitmentDigest,
                reportDigest,
                publicationDigest,
                resultDigest
        );
        return AllocationCycleOperationResult.accepted(new AllocationCycleResult(
                input.context().cycleId(),
                input.context().simulationTick(),
                orderedIds,
                successful,
                waiting,
                failed,
                evaluations,
                commitments,
                ledger.entries(),
                conflicts,
                failures,
                AllocationPublicationStatus.PUBLISHED,
                report,
                summary,
                trace,
                digests,
                AllocationSchema.CURRENT_VERSION
        ));
    }

    private static List<AllocationSetDefinition> orderedSets(
            AllocationCycleInput input
    ) {
        Comparator<AllocationRequestDefinition> requestOrder =
                AllocationRequestDefinition.canonicalComparator(
                        input.context().simulationTick()
                );
        return input.candidateSets().stream()
                .sorted(Comparator.comparing(
                        (AllocationSetDefinition set) ->
                                input.definitions().findRequest(
                                        set.sourceRequestId()
                                ).orElseThrow(),
                        requestOrder
                ).thenComparing(AllocationSetDefinition::id))
                .toList();
    }

    private static void validateProposedResult(
            AllocationCycleInput input,
            List<AllocationSetEvaluationResult> evaluations,
            List<AllocationCommitmentDefinition> commitments,
            WorkingCapacityLedger ledger
    ) {
        TreeSet<AllocationCommitmentId> commitmentIds = new TreeSet<>();
        TreeSet<RequirementId> committedRequirements = new TreeSet<>();
        for (AllocationCommitmentDefinition commitment : commitments) {
            if (!commitmentIds.add(commitment.id())) {
                invalid(
                        AllocationCycleFailureCode.DUPLICATE_COMMITMENT,
                        commitment.id().value(),
                        "Proposed result contains duplicate Commitment identity"
                );
            }
            if (!committedRequirements.add(commitment.requirementId())) {
                invalid(
                        AllocationCycleFailureCode.DUPLICATE_COMMITMENT,
                        commitment.requirementId().value(),
                        "Proposed result commits one Requirement more than once"
                );
            }
        }
        for (AllocationSetEvaluationResult evaluation : evaluations) {
            AllocationSetDefinition set = input.definitions().findSet(
                    evaluation.allocationSetId()
            ).orElseThrow();
            TreeSet<RequirementId> expected = new TreeSet<>(set.requirementIds());
            TreeSet<RequirementId> actual = evaluation.proposedCommitments().stream()
                    .map(AllocationCommitmentDefinition::requirementId)
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
            boolean successful = evaluation.outcome()
                    == AllocationSetEvaluationOutcome.ALLOCATABLE;
            if (successful != expected.equals(actual)
                    || (!successful && !actual.isEmpty())) {
                invalid(
                        AllocationCycleFailureCode.INCOMPLETE_COMMITMENT_SET,
                        set.id().value(),
                        "Proposed Commitment set does not match AllocationSet outcome"
                );
            }
        }
        ledger.entries().forEach(AllocationLedgerEntryView::remainingQuantity);
    }

    private static List<AllocationRuntimeTransitionRequest> transitions(
            AllocationCycleInput input,
            List<AllocationSetEvaluationResult> evaluations
    ) {
        List<AllocationRuntimeTransitionRequest> transitions = new ArrayList<>();
        for (AllocationSetEvaluationResult evaluation : evaluations) {
            AllocationRuntimeView runtime = input.runtimes().find(
                    evaluation.allocationSetId()
            ).orElseThrow();
            if (evaluation.outcome() == AllocationSetEvaluationOutcome.ALLOCATABLE) {
                transitions.add(AllocationRuntimeTransitionRequest.allocated(
                        evaluation.allocationSetId(),
                        input.context().simulationTick(),
                        evaluation.proposedCommitments().stream()
                                .map(AllocationCommitmentDefinition::id)
                                .toList()
                ));
            } else if (evaluation.outcome() == AllocationSetEvaluationOutcome.WAITING
                    && runtime.status() == AllocationRuntimeStatus.REQUESTED) {
                transitions.add(AllocationRuntimeTransitionRequest.waiting(
                        evaluation.allocationSetId(),
                        input.context().simulationTick(),
                        "Complete Capacity is unavailable in Allocation Cycle "
                                + input.context().cycleId().value()
                ));
            }
        }
        return transitions.stream()
                .sorted(Comparator.comparing(
                        AllocationRuntimeTransitionRequest::allocationSetId
                ))
                .toList();
    }

    private static AllocationReport report(
            AllocationCycleInput input,
            List<AllocationSetEvaluationResult> evaluations,
            List<AllocationCommitmentDefinition> commitments,
            List<AllocationConflictRecord> conflicts,
            List<AllocationCycleFailure> failures,
            WorkingCapacityLedger ledger,
            Map<AllocationCyclePhase, Long> operationCounts,
            long totalOperations
    ) {
        List<AllocationSetId> expired = evaluations.stream()
                .filter(result -> result.failures().stream().anyMatch(failure ->
                        failure.code() == AllocationCycleFailureCode.SET_EXPIRED))
                .map(AllocationSetEvaluationResult::allocationSetId)
                .sorted()
                .toList();
        List<AllocationSetId> failed = outcomes(
                evaluations,
                AllocationSetEvaluationOutcome.FAILED
        ).stream().filter(id -> !expired.contains(id)).toList();
        Map<String, Long> stages = new LinkedHashMap<>();
        operationCounts.forEach((phase, count) ->
                stages.put(
                        "butchercraft:" + phase.name().toLowerCase(
                                java.util.Locale.ROOT
                        ),
                        count
                ));
        return new AllocationReport(
                input.context().cycleId(),
                outcomes(evaluations, AllocationSetEvaluationOutcome.ALLOCATABLE),
                outcomes(evaluations, AllocationSetEvaluationOutcome.WAITING),
                List.of(),
                failed,
                List.of(),
                expired,
                commitments.stream().map(AllocationCommitmentDefinition::id).toList(),
                conflicts,
                ledger.entries().stream().map(entry ->
                        new AllocationCapacityReportEntry(
                                entry.capacityKey(),
                                entry.observedQuantity(),
                                entry.totalCommittedQuantity(),
                                entry.remainingQuantity()
                        )).toList(),
                evaluations.stream().map(evaluation -> {
                    AllocationSetDefinition set = input.definitions().findSet(
                            evaluation.allocationSetId()
                    ).orElseThrow();
                    AllocationRequestDefinition request =
                            input.definitions().findRequest(
                                    set.sourceRequestId()
                            ).orElseThrow();
                    return new AllocationReportOrderingRecord(
                            request.id(),
                            request.orderingContext()
                    );
                }).toList(),
                new AllocationReportWorkSummary(
                        stages,
                        totalOperations,
                        totalOperations,
                        false
                ),
                failures.stream().map(
                        AllocationCycleExecutor::runtimeFailure
                ).toList(),
                input.context().policyId(),
                input.context().simulationTick(),
                AllocationSchema.CURRENT_VERSION
        );
    }

    private static AllocationRuntimeFailure runtimeFailure(
            AllocationCycleFailure failure
    ) {
        AllocationRuntimeFailureCode code = switch (failure.code()) {
            case CAPACITY_UNAVAILABLE, EXCLUSIVE_CONFLICT ->
                    AllocationRuntimeFailureCode.CAPACITY_UNAVAILABLE;
            case SET_EXPIRED -> AllocationRuntimeFailureCode.SET_EXPIRED;
            default -> AllocationRuntimeFailureCode.SET_FAILED;
        };
        return new AllocationRuntimeFailure(
                code,
                failure.subject(),
                failure.message()
        );
    }

    private static Map<AllocationCyclePhase, Long> operationCounts(
            AllocationCycleInput input,
            List<AllocationCommitmentDefinition> activeCommitments,
            List<AllocationSetEvaluationResult> evaluations,
            List<AllocationCommitmentDefinition> commitments,
            List<AllocationRuntimeTransitionRequest> transitions,
            List<AllocationConflictRecord> conflicts
    ) {
        long inputCount = input.resources().size()
                + (long) input.capacities().size()
                + input.definitions().requirementCount()
                + input.definitions().requestCount()
                + input.definitions().setCount()
                + input.definitions().commitmentCount()
                + input.runtimes().size()
                + input.candidateSetIds().size();
        long requirementCount = evaluations.stream()
                .mapToLong(result -> result.requirementEvaluations().size())
                .sum();
        EnumMap<AllocationCyclePhase, Long> counts =
                new EnumMap<>(AllocationCyclePhase.class);
        counts.put(AllocationCyclePhase.CAPTURE_EXPLICIT_INPUT, inputCount);
        counts.put(AllocationCyclePhase.VALIDATE_CYCLE_ENVELOPE, inputCount);
        counts.put(
                AllocationCyclePhase.VALIDATE_REFERENCES,
                input.definitions().requirementCount()
                        + (long) input.definitions().requestCount()
                        + input.definitions().setCount()
                        + input.definitions().commitmentCount()
                        + input.runtimes().size()
        );
        counts.put(
                AllocationCyclePhase.OBSERVE_ACTIVE_COMMITMENTS,
                (long) activeCommitments.size()
        );
        counts.put(
                AllocationCyclePhase.CONSTRUCT_WORKING_LEDGER,
                input.capacities().size() + (long) activeCommitments.size()
        );
        counts.put(
                AllocationCyclePhase.ORDER_ELIGIBLE_SETS,
                (long) evaluations.size()
        );
        counts.put(
                AllocationCyclePhase.EVALUATE_ALLOCATION_SETS,
                requirementCount
        );
        counts.put(
                AllocationCyclePhase.CONSTRUCT_COMMITMENTS,
                (long) commitments.size()
        );
        counts.put(
                AllocationCyclePhase.VALIDATE_PROPOSED_RESULT,
                evaluations.size() + (long) commitments.size()
                        + input.capacities().size()
        );
        counts.put(
                AllocationCyclePhase.PUBLISH_ATOMICALLY,
                commitments.size() + (long) transitions.size() + 2L
        );
        counts.put(
                AllocationCyclePhase.PRODUCE_IMMUTABLE_EVIDENCE,
                evaluations.size() + (long) conflicts.size()
                        + input.capacities().size()
                        + AllocationSchema.MAXIMUM_TRACE_PHASES
        );
        return Map.copyOf(counts);
    }

    private static AllocationCycleTrace trace(
            AllocationCycleInput input,
            Map<AllocationCyclePhase, Long> counts,
            String inputDigest,
            String initialLedgerDigest,
            String orderingDigest,
            String finalLedgerDigest,
            String commitmentDigest,
            String publicationPlanDigest,
            String reportDigest
    ) {
        List<String> phaseDigests = List.of(
                inputDigest,
                inputDigest,
                inputDigest,
                initialLedgerDigest,
                initialLedgerDigest,
                orderingDigest,
                finalLedgerDigest,
                commitmentDigest,
                finalLedgerDigest,
                publicationPlanDigest,
                reportDigest
        );
        List<AllocationCyclePhaseRecord> phases = new ArrayList<>();
        for (AllocationCyclePhase phase : AllocationCyclePhase.values()) {
            phases.add(new AllocationCyclePhaseRecord(
                    phase,
                    phase.ordinal() + 1,
                    counts.get(phase),
                    phaseDigests.get(phase.ordinal())
            ));
        }
        return AllocationCycleTrace.create(
                input.context().cycleId(),
                input.context().simulationTick(),
                phases
        );
    }

    private static String publicationPlanDigest(
            List<AllocationCommitmentDefinition> commitments,
            List<AllocationRuntimeTransitionRequest> transitions,
            String reportDigest
    ) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_publication_plan_v1")
                        .add(AllocationCycleDigestSupport.commitments(commitments))
                        .add(reportDigest);
        transitions.forEach(transition -> {
            digest.add(transition.allocationSetId().value())
                    .add(transition.targetStatus().name())
                    .add(transition.transitionSimulationTick());
            transition.commitmentIds().forEach(id -> digest.add(id.value()));
            transition.failureCode().ifPresent(code -> digest.add(code.name()));
            transition.failureMessage().ifPresent(digest::add);
        });
        return digest.finish();
    }

    private static List<AllocationSetId> outcomes(
            List<AllocationSetEvaluationResult> evaluations,
            AllocationSetEvaluationOutcome outcome
    ) {
        return evaluations.stream()
                .filter(result -> result.outcome() == outcome)
                .map(AllocationSetEvaluationResult::allocationSetId)
                .sorted()
                .toList();
    }

    private static void invalid(
            AllocationCycleFailureCode code,
            String subject,
            String message
    ) {
        throw AllocationCycleValidation.failure(
                code,
                AllocationCycleFailureScope.CYCLE,
                subject,
                message
        );
    }

    private static <T> AllocationCycleOperationResult<T> rejected(
            AllocationCycleFailureCode code,
            AllocationCycleFailureScope scope,
            String subject,
            String message
    ) {
        return AllocationCycleOperationResult.rejected(List.of(
                new AllocationCycleFailure(code, scope, subject, message)
        ));
    }
}
