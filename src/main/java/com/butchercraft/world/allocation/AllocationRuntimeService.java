package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class AllocationRuntimeService {
    private AllocationRegistry definitions;
    private final Map<AllocationSetId, AllocationSetRuntime> runtimes = new TreeMap<>();
    private final Map<AllocationCycleId, AllocationReport> reports = new TreeMap<>();
    private final Map<AllocationCycleId, AllocationCycleTrace> traces = new TreeMap<>();
    private final List<AllocationRuntimeTransitionRecord> historyRecords =
            new ArrayList<>();

    public AllocationRuntimeService(AllocationRegistry definitions) {
        this(definitions, List.of(), List.of(), AllocationHistory.empty());
    }

    public AllocationRuntimeService(
            AllocationRegistry definitions,
            Collection<AllocationRuntimeView> runtimeViews,
            Collection<AllocationReport> reports,
            AllocationHistory history
    ) {
        this(definitions, runtimeViews, reports, history, List.of());
    }

    public AllocationRuntimeService(
            AllocationRegistry definitions,
            Collection<AllocationRuntimeView> runtimeViews,
            Collection<AllocationReport> reports,
            AllocationHistory history,
            Collection<AllocationCycleTrace> traces
    ) {
        this.definitions = AllocationValidation.required(definitions, "definitions");
        this.historyRecords.addAll(
                AllocationValidation.required(history, "history").records()
        );
        for (AllocationRuntimeView view : AllocationValidation.required(
                runtimeViews,
                "runtimeViews"
        )) {
            AllocationRuntimeView value = AllocationValidation.required(view, "view");
            validateRuntimeReferences(value);
            if (runtimes.putIfAbsent(
                    value.allocationSetId(),
                    AllocationSetRuntime.loaded(value)
            ) != null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_RUNTIME,
                        value.allocationSetId().value(),
                        "Duplicate loaded Allocation runtime"
                );
            }
        }
        for (AllocationReport report : AllocationValidation.required(reports, "reports")) {
            AllocationReport value = AllocationValidation.required(report, "report");
            validateReportReferences(value);
            if (this.reports.putIfAbsent(value.allocationCycleId(), value) != null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                        value.allocationCycleId().value(),
                        "Duplicate loaded Allocation report"
                );
            }
        }
        for (AllocationCycleTrace trace : AllocationValidation.required(
                traces,
                "traces"
        )) {
            AllocationCycleTrace value = AllocationValidation.required(trace, "trace");
            AllocationReport report = this.reports.get(value.cycleId());
            if (report == null || report.simulationTick() != value.simulationTick()) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_REPORT,
                        value.cycleId().value(),
                        "Loaded Allocation trace has no matching report"
                );
            }
            if (this.traces.putIfAbsent(value.cycleId(), value) != null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                        value.cycleId().value(),
                        "Duplicate loaded Allocation trace"
                );
            }
        }
        validateHistory();
    }

    public synchronized AllocationRuntimeOperationResult<AllocationRuntimeView>
    registerRequested(
            AllocationSetId setId,
            AllocationMetadata metadata
    ) {
        return AllocationRuntimeOperationResult.validate(() -> {
            AllocationSetDefinition set = definitions.findSet(
                    AllocationValidation.required(setId, "setId")
            ).orElseThrow(() -> AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.UNKNOWN_SET,
                    setId.value(),
                    "Cannot register runtime for unknown AllocationSet"
            ));
            if (runtimes.containsKey(set.id())) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_RUNTIME,
                        set.id().value(),
                        "AllocationSet runtime is already registered"
                );
            }
            AllocationSetRuntime runtime = AllocationSetRuntime.requested(
                    set,
                    AllocationValidation.required(metadata, "metadata")
            );
            AllocationRuntimeView view = runtime.snapshot();
            AllocationRuntimeTransitionRecord record = new AllocationRuntimeTransitionRecord(
                    set.id(),
                    Optional.empty(),
                    AllocationRuntimeStatus.REQUESTED,
                    view.createdSimulationTick(),
                    view.revision(),
                    Optional.empty(),
                    Optional.empty(),
                    AllocationSchema.CURRENT_VERSION
            );
            runtimes.put(set.id(), runtime);
            historyRecords.add(record);
            return view;
        });
    }

    public synchronized AllocationRuntimeOperationResult<AllocationRuntimeView> transition(
            AllocationRuntimeTransitionRequest request
    ) {
        return AllocationRuntimeOperationResult.validate(() -> {
            AllocationRuntimeTransitionRequest transition = AllocationValidation.required(
                    request,
                    "request"
            );
            AllocationSetRuntime runtime = runtimes.get(transition.allocationSetId());
            if (runtime == null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        transition.allocationSetId().value(),
                        "Allocation runtime is not registered"
                );
            }
            AllocationRuntimeView before = runtime.snapshot();
            if (transition.targetStatus() == AllocationRuntimeStatus.ALLOCATED) {
                validateCompleteCommitments(
                        transition.allocationSetId(),
                        transition.commitmentIds()
                );
            }
            AllocationRuntimeView after = runtime.transition(transition);
            AllocationRuntimeTransitionRecord record = new AllocationRuntimeTransitionRecord(
                    after.allocationSetId(),
                    Optional.of(before.status()),
                    after.status(),
                    after.lastUpdatedSimulationTick(),
                    after.revision(),
                    after.failureCode(),
                    after.failureMessage(),
                    AllocationSchema.CURRENT_VERSION
            );
            historyRecords.add(record);
            return after;
        });
    }

    public synchronized AllocationRuntimeOperationResult<List<AllocationCommitmentDefinition>>
    registerCommitments(Collection<AllocationCommitmentDefinition> commitments) {
        return AllocationRuntimeOperationResult.validate(() -> {
            List<AllocationCommitmentDefinition> values = new ArrayList<>(
                    AllocationValidation.required(commitments, "commitments")
            );
            AllocationRegistryBuilder builder = definitions.toBuilder();
            values.forEach(builder::registerCommitment);
            AllocationRegistry candidate = builder.build();
            definitions = candidate;
            return values.stream().sorted().toList();
        });
    }

    public synchronized AllocationRuntimeOperationResult<AllocationReport> registerReport(
            AllocationReport report
    ) {
        return AllocationRuntimeOperationResult.validate(() -> {
            AllocationReport value = AllocationValidation.required(report, "report");
            validateReportReferences(value);
            if (reports.containsKey(value.allocationCycleId())
                    || reports.values().stream().anyMatch(existing ->
                    existing.simulationTick() == value.simulationTick())) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                        value.allocationCycleId().value(),
                        "Allocation report Cycle or tick is already registered"
                );
            }
            reports.put(value.allocationCycleId(), value);
            return value;
        });
    }

    public synchronized AllocationRegistry definitions() {
        return definitions;
    }

    public synchronized AllocationRuntimeRegistry runtimes() {
        return AllocationRuntimeRegistry.of(
                runtimes.values().stream().map(AllocationSetRuntime::snapshot).toList()
        );
    }

    public synchronized AllocationReportRegistry reports() {
        return AllocationReportRegistry.of(reports.values());
    }

    public synchronized AllocationHistory history() {
        return AllocationHistory.of(historyRecords);
    }

    public synchronized AllocationCycleTraceRegistry traces() {
        return AllocationCycleTraceRegistry.of(traces.values());
    }

    public synchronized AllocationQueryService queries() {
        return new AllocationQueryService(
                definitions,
                runtimes(),
                reports(),
                history()
        );
    }

    synchronized AllocationCycleOperationResult<AllocationPublishedCycle> publishCycle(
            AllocationCyclePublicationPlan plan,
            AllocationPublicationFault fault
    ) {
        try {
            AllocationCyclePublicationPlan publication = AllocationValidation.required(
                    plan,
                    "plan"
            );
            AllocationPublicationFault injection = AllocationValidation.required(
                    fault,
                    "fault"
            );
            AllocationCycleId cycleId = publication.report().allocationCycleId();
            if (reports.containsKey(cycleId)
                    || traces.containsKey(cycleId)
                    || reports.values().stream().anyMatch(report ->
                    report.simulationTick() == publication.report().simulationTick())) {
                throw AllocationCycleValidation.failure(
                        AllocationCycleFailureCode.DUPLICATE_CYCLE,
                        AllocationCycleFailureScope.PUBLICATION,
                        cycleId.value(),
                        "Allocation Cycle is already published"
                );
            }
            validateExpectedState(publication);

            AllocationRuntimeService candidate = new AllocationRuntimeService(
                    definitions,
                    runtimes.values().stream()
                            .map(AllocationSetRuntime::snapshot)
                            .toList(),
                    reports.values(),
                    AllocationHistory.of(historyRecords),
                    traces.values()
            );
            requireAccepted(
                    candidate.registerCommitments(publication.commitments()),
                    cycleId
            );
            failIfRequested(
                    injection,
                    AllocationPublicationFault.AFTER_COMMITMENT_REGISTRATION,
                    cycleId
            );
            for (AllocationRuntimeTransitionRequest transition
                    : publication.transitions()) {
                requireAccepted(candidate.transition(transition), cycleId);
            }
            failIfRequested(
                    injection,
                    AllocationPublicationFault.AFTER_RUNTIME_TRANSITIONS,
                    cycleId
            );
            requireAccepted(candidate.registerReport(publication.report()), cycleId);
            failIfRequested(
                    injection,
                    AllocationPublicationFault.AFTER_REPORT_REGISTRATION,
                    cycleId
            );
            candidate.registerTrace(publication.trace());

            definitions = candidate.definitions;
            runtimes.clear();
            runtimes.putAll(candidate.runtimes);
            reports.clear();
            reports.putAll(candidate.reports);
            traces.clear();
            traces.putAll(candidate.traces);
            historyRecords.clear();
            historyRecords.addAll(candidate.historyRecords);
            return AllocationCycleOperationResult.accepted(new AllocationPublishedCycle(
                    definitions,
                    runtimes(),
                    reports(),
                    history(),
                    traces()
            ));
        } catch (AllocationCycleValidationException exception) {
            return AllocationCycleOperationResult.rejected(exception.failures());
        } catch (RuntimeException exception) {
            return AllocationCycleOperationResult.rejected(List.of(
                    new AllocationCycleFailure(
                            AllocationCycleFailureCode.PUBLICATION_FAILED,
                            AllocationCycleFailureScope.PUBLICATION,
                            "allocation_cycle",
                            exception.getMessage() == null
                                    ? "Atomic Allocation publication failed"
                                    : exception.getMessage()
                    )
            ));
        }
    }

    synchronized AllocationCycleOperationResult<AllocationPublishedCycle> publishCycle(
            AllocationCyclePublicationPlan plan
    ) {
        return publishCycle(plan, AllocationPublicationFault.NONE);
    }

    private void registerTrace(AllocationCycleTrace trace) {
        AllocationCycleTrace value = AllocationValidation.required(trace, "trace");
        AllocationReport report = reports.get(value.cycleId());
        if (report == null || report.simulationTick() != value.simulationTick()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    value.cycleId().value(),
                    "Allocation trace requires a matching report"
            );
        }
        if (traces.putIfAbsent(value.cycleId(), value) != null) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                    value.cycleId().value(),
                    "Allocation trace is already registered"
            );
        }
    }

    private void validateExpectedState(AllocationCyclePublicationPlan plan) {
        AllocationRegistry expected = plan.expectedDefinitions();
        if (!definitions.requirements().equals(expected.requirements())
                || !definitions.requests().equals(expected.requests())
                || !definitions.sets().equals(expected.sets())
                || !definitions.commitments().equals(expected.commitments())
                || !runtimes().views().equals(plan.expectedRuntimes().views())) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.STALE_RUNTIME_STATE,
                    AllocationCycleFailureScope.PUBLICATION,
                    plan.report().allocationCycleId().value(),
                    "Allocation runtime changed after cycle input capture"
            );
        }
    }

    private static void requireAccepted(
            AllocationRuntimeOperationResult<?> result,
            AllocationCycleId cycleId
    ) {
        if (!result.accepted()) {
            AllocationRuntimeFailure failure = result.failures().getFirst();
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.PUBLICATION_FAILED,
                    AllocationCycleFailureScope.PUBLICATION,
                    cycleId.value(),
                    failure.code() + ": " + failure.message()
            );
        }
    }

    private static void failIfRequested(
            AllocationPublicationFault actual,
            AllocationPublicationFault checkpoint,
            AllocationCycleId cycleId
    ) {
        if (actual == checkpoint) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.PUBLICATION_FAILED,
                    AllocationCycleFailureScope.PUBLICATION,
                    cycleId.value(),
                    "Deterministic publication fault at " + checkpoint.name()
            );
        }
    }

    private void validateCompleteCommitments(
            AllocationSetId setId,
            List<AllocationCommitmentId> commitmentIds
    ) {
        AllocationSetDefinition set = definitions.findSet(setId).orElseThrow(
                () -> AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        setId.value(),
                        "Allocation runtime references an unknown AllocationSet"
                )
        );
        Map<RequirementId, AllocationCommitmentId> byRequirement = new TreeMap<>();
        for (AllocationCommitmentId id : commitmentIds) {
            AllocationCommitmentDefinition commitment = definitions.findCommitment(id)
                    .orElseThrow(() -> AllocationRuntimeValidation.failure(
                            AllocationRuntimeFailureCode.UNKNOWN_COMMITMENT,
                            id.value(),
                            "Allocated runtime references unknown Commitment"
                    ));
            if (!commitment.allocationSetId().equals(setId)) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_COMMITMENT_ASSOCIATION,
                        id.value(),
                        "Commitment belongs to another AllocationSet"
                );
            }
            if (byRequirement.putIfAbsent(commitment.requirementId(), id) != null) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_COMMITMENT,
                        commitment.requirementId().value(),
                        "Multiple Commitments satisfy one Requirement"
                );
            }
        }
        if (!byRequirement.keySet().equals(new java.util.TreeSet<>(
                set.requirementIds()
        ))) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INCOMPLETE_COMMITMENT_SET,
                    setId.value(),
                    "ALLOCATED runtime requires exactly one Commitment per Requirement"
            );
        }
    }

    private void validateRuntimeReferences(AllocationRuntimeView view) {
        AllocationSetDefinition set = definitions.findSet(view.allocationSetId())
                .orElseThrow(() -> AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        view.allocationSetId().value(),
                        "Loaded runtime references unknown AllocationSet"
                ));
        if (view.createdSimulationTick() != set.creationSimulationTick()
                || !view.expirationSimulationTick().equals(
                set.expirationSimulationTick()
        )) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_TIMESTAMP,
                    set.id().value(),
                    "Loaded runtime timestamps do not match AllocationSet definition"
            );
        }
        if (!view.commitmentIds().isEmpty()) {
            validateCompleteCommitments(set.id(), view.commitmentIds());
        }
    }

    private void validateReportReferences(AllocationReport report) {
        List<AllocationSetId> sets = new ArrayList<>();
        sets.addAll(report.successfulSetIds());
        sets.addAll(report.waitingSetIds());
        sets.addAll(report.rejectedSetIds());
        sets.addAll(report.failedSetIds());
        sets.addAll(report.releasedSetIds());
        sets.addAll(report.expiredSetIds());
        report.conflicts().forEach(conflict -> {
            sets.addAll(conflict.winnerSetIds());
            sets.addAll(conflict.loserSetIds());
        });
        for (AllocationSetId id : sets) {
            if (definitions.findSet(id).isEmpty()) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        id.value(),
                        "Allocation report references unknown AllocationSet"
                );
            }
        }
        for (AllocationCommitmentId id : report.commitmentIds()) {
            AllocationCommitmentDefinition commitment = definitions.findCommitment(id)
                    .orElseThrow(() -> AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.UNKNOWN_COMMITMENT,
                        id.value(),
                        "Allocation report references unknown Commitment"
                    ));
            if (!commitment.allocationCycleId().equals(report.allocationCycleId())) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_REPORT,
                        id.value(),
                        "Allocation report Commitment belongs to another Cycle"
                );
            }
        }
        for (AllocationReportOrderingRecord ordering : report.orderingContexts()) {
            AllocationRequestDefinition request = definitions.findRequest(ordering.requestId())
                    .orElseThrow(() -> AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.UNKNOWN_REQUEST,
                        ordering.requestId().value(),
                        "Allocation report references unknown AllocationRequest"
                    ));
            if (!request.orderingContext().equals(ordering.orderingContext())) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_REPORT,
                        ordering.requestId().value(),
                        "Allocation report ordering context does not match its Request"
                );
            }
        }
    }

    private void validateHistory() {
        AllocationHistory history = AllocationHistory.of(historyRecords);
        for (AllocationRuntimeTransitionRecord record : history.records()) {
            if (!runtimes.containsKey(record.allocationSetId())) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        record.allocationSetId().value(),
                        "Allocation history references unknown runtime"
                );
            }
        }
        for (Map.Entry<AllocationSetId, AllocationSetRuntime> entry : runtimes.entrySet()) {
            AllocationRuntimeView view = entry.getValue().snapshot();
            AllocationRuntimeTransitionRecord latest = history.latest(entry.getKey())
                    .orElseThrow(() -> AllocationRuntimeValidation.failure(
                            AllocationRuntimeFailureCode.INVALID_HISTORY,
                            entry.getKey().value(),
                            "Allocation runtime has no history"
                    ));
            if (latest.status() != view.status()
                    || latest.revision() != view.revision()
                    || latest.transitionSimulationTick()
                    != view.lastUpdatedSimulationTick()) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.INVALID_HISTORY,
                        entry.getKey().value(),
                        "Allocation runtime and history snapshots are inconsistent"
                );
            }
        }
    }
}
