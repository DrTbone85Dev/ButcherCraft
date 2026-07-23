package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Optional;

public final class AllocationQueryService {
    private final AllocationRegistry definitions;
    private final AllocationRuntimeRegistry runtimes;
    private final AllocationReportRegistry reports;
    private final AllocationHistory history;

    public AllocationQueryService(
            AllocationRegistry definitions,
            AllocationRuntimeRegistry runtimes,
            AllocationReportRegistry reports,
            AllocationHistory history
    ) {
        this.definitions = AllocationValidation.required(definitions, "definitions");
        this.runtimes = AllocationValidation.required(runtimes, "runtimes");
        this.reports = AllocationValidation.required(reports, "reports");
        this.history = AllocationValidation.required(history, "history");
    }

    public Optional<AllocationRequestDefinition> findRequest(AllocationRequestId id) {
        return definitions.findRequest(id);
    }

    public Optional<AllocationSetDefinition> findSet(AllocationSetId id) {
        return definitions.findSet(id);
    }

    public Optional<AllocationCommitmentDefinition> findCommitment(
            AllocationCommitmentId id
    ) {
        return definitions.findCommitment(id);
    }

    public Optional<AllocationRuntimeView> findRuntime(AllocationSetId id) {
        return runtimes.find(id);
    }

    public List<AllocationRuntimeView> findRuntimeByRequest(AllocationRequestId id) {
        return definitions.findSetsByRequest(id).stream()
                .map(AllocationSetDefinition::id)
                .map(runtimes::find)
                .flatMap(Optional::stream)
                .toList();
    }

    public List<AllocationRuntimeView> activeRuntime() {
        return runtimes.findByStatus(AllocationRuntimeStatus.ACTIVE);
    }

    public List<AllocationRuntimeView> waitingRuntime() {
        return runtimes.findByStatus(AllocationRuntimeStatus.WAITING);
    }

    public List<AllocationRuntimeView> releasedRuntime() {
        return runtimes.findByStatus(AllocationRuntimeStatus.RELEASED);
    }

    public List<AllocationRuntimeView> failedRuntime() {
        return runtimes.findByStatus(AllocationRuntimeStatus.FAILED);
    }

    public List<AllocationRuntimeView> expiredRuntime() {
        return runtimes.findByStatus(AllocationRuntimeStatus.EXPIRED);
    }

    public List<AllocationSetDefinition> setsByPlanningCycle(
            ExternalReference planningCycle
    ) {
        ExternalReference reference = AllocationValidation.required(
                planningCycle,
                "planningCycle"
        );
        return definitions.sets().stream()
                .filter(set -> set.planningCycleReference().equals(reference))
                .toList();
    }

    public List<AllocationCommitmentDefinition> commitmentsBySet(
            AllocationSetId id
    ) {
        return definitions.findCommitmentsBySet(id);
    }

    public List<AllocationCommitmentDefinition> commitmentsByRequirement(
            RequirementId id
    ) {
        return definitions.findCommitmentsByRequirement(id);
    }

    public List<AllocationCommitmentDefinition> commitmentsByResource(
            ResourceId id
    ) {
        return definitions.findCommitmentsByResource(id);
    }

    public List<AllocationCommitmentDefinition> commitmentsByExecutionWork(
            ExternalReference work
    ) {
        return definitions.findSetsByExecutionWork(work).stream()
                .flatMap(set -> definitions.findCommitmentsBySet(set.id()).stream())
                .sorted()
                .toList();
    }

    public List<AllocationRuntimeTransitionRecord> history(AllocationSetId id) {
        return history.findBySet(id);
    }

    public List<AllocationRuntimeTransitionRecord> historyBetween(
            long firstInclusive,
            long lastInclusive
    ) {
        return history.findBetween(firstInclusive, lastInclusive);
    }

    public Optional<AllocationReport> findReport(AllocationCycleId id) {
        return reports.find(id);
    }

    public Optional<AllocationReport> findReportByTick(long tick) {
        return reports.findByTick(tick);
    }

    public List<AllocationReport> reportsBetween(
            long firstInclusive,
            long lastInclusive
    ) {
        return reports.findBetween(firstInclusive, lastInclusive);
    }
}
