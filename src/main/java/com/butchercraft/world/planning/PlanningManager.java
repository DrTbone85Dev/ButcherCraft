package com.butchercraft.world.planning;

import com.butchercraft.world.goods.GoodId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class PlanningManager {
    private final PlanningDependencies dependencies;
    private final PlanningPipeline pipeline;
    private final PlanningSelectionPolicy policy;
    private final PlanningExecutionBudget budget;
    private final Map<PlanningCycleId, PlanningCycleSnapshot> cycles = new LinkedHashMap<>();
    private final Map<Long, PlanningCycleId> cycleByTick = new LinkedHashMap<>();

    public PlanningManager(
            PlanningDependencies dependencies,
            PlanningSelectionPolicy policy,
            PlanningExecutionBudget budget
    ) {
        this(dependencies, policy, budget, List.of());
    }

    public PlanningManager(
            PlanningDependencies dependencies,
            PlanningSelectionPolicy policy,
            PlanningExecutionBudget budget,
            Collection<PlanningCycleSnapshot> loaded
    ) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.budget = Objects.requireNonNull(budget, "budget");
        pipeline = new PlanningPipeline(dependencies, policy, budget);
        for (PlanningCycleSnapshot cycle : Objects.requireNonNull(loaded, "loaded")) {
            registerLoaded(cycle);
        }
    }

    public synchronized PlanningCycleSnapshot executeCycle(long simulationTick) {
        PlanningCycleId id = PlanningCycleId.forTick(simulationTick);
        if (cycles.containsKey(id) || cycleByTick.containsKey(simulationTick)) {
            throw new IllegalArgumentException("A Planning Cycle already exists for tick " + simulationTick);
        }
        PlanningCycleSnapshot cycle = pipeline.execute(simulationTick);
        registerLoaded(cycle);
        return cycle;
    }

    public synchronized Optional<PlanningCycleSnapshot> find(PlanningCycleId id) {
        return Optional.ofNullable(cycles.get(Objects.requireNonNull(id, "id")));
    }

    public synchronized Optional<PlanningCycleSnapshot> findByTick(long tick) {
        PlanningCycleId id = cycleByTick.get(tick);
        return id == null ? Optional.empty() : find(id);
    }

    public synchronized List<PlanningCycleSnapshot> cycles() {
        return List.copyOf(cycles.values());
    }

    public synchronized List<NeedDefinition> needsByGood(GoodId id) {
        return cycles.values().stream().flatMap(cycle -> cycle.needs().stream())
                .filter(need -> need.goodId().filter(id::equals).isPresent())
                .sorted(PlanningArtifacts.NEED_ORDER).toList();
    }

    public synchronized List<NeedDefinition> needsByPriority(PlanningPriority priority) {
        return cycles.values().stream().flatMap(cycle -> cycle.needs().stream())
                .filter(need -> need.basePriority() == priority)
                .sorted(PlanningArtifacts.NEED_ORDER).toList();
    }

    public synchronized List<ApprovedPlanDefinition> approvedByCategory(PlanCategory category) {
        return cycles.values().stream().flatMap(cycle -> cycle.approvedPlans().stream())
                .filter(plan -> plan.category() == category)
                .sorted(java.util.Comparator.comparing(ApprovedPlanDefinition::id)).toList();
    }

    public synchronized List<PlanningCycleReport> reportsBetween(long first, long last) {
        if (first < 0 || last < first) throw new IllegalArgumentException("Invalid Planning report range");
        return cycles.values().stream().filter(cycle -> cycle.simulationTick() >= first
                        && cycle.simulationTick() <= last)
                .map(PlanningCycleSnapshot::report).toList();
    }

    public synchronized void validate() {
        if (cycles.size() != cycleByTick.size()) throw new IllegalArgumentException("Planning tick index mismatch");
        for (PlanningCycleSnapshot cycle : cycles.values()) {
            if (!cycle.status().terminal()) throw new IllegalArgumentException("Persisted Planning Cycle is interrupted");
            validateCycle(cycle);
        }
    }

    private void registerLoaded(PlanningCycleSnapshot cycle) {
        Objects.requireNonNull(cycle, "cycle");
        if (!cycle.status().terminal()) throw new IllegalArgumentException("Interrupted Planning Cycle cannot load");
        validateCycle(cycle);
        if (cycles.putIfAbsent(cycle.id(), cycle) != null
                || cycleByTick.putIfAbsent(cycle.simulationTick(), cycle.id()) != null) {
            throw new IllegalArgumentException("Duplicate Planning Cycle");
        }
    }

    private void validateCycle(PlanningCycleSnapshot cycle) {
        validateStructureAndBudget(cycle);
        validateGraph(cycle, budget.maximumRecursiveDepth());
        validateReferences(cycle);
    }

    private void validateStructureAndBudget(PlanningCycleSnapshot cycle) {
        if (!cycle.id().equals(PlanningCycleId.forTick(cycle.simulationTick()))) {
            throw new IllegalArgumentException("Planning Cycle identity does not match its tick");
        }
        if (!cycle.policyId().equals(policy.id())) {
            throw new IllegalArgumentException("Planning Cycle references an unknown policy");
        }
        PlanningCycleReport report = cycle.report();
        if (!report.cycleId().equals(cycle.id()) || report.simulationTick() != cycle.simulationTick()
                || !report.policyId().equals(cycle.policyId()) || report.status() != cycle.status()) {
            throw new IllegalArgumentException("Planning Cycle report identity is inconsistent");
        }
        if (!report.budget().equals(budget)) {
            throw new IllegalArgumentException("Planning Cycle budget does not match the active policy contract");
        }
        requireAtMost(cycle.observations().size(), budget.maximumObservations(), "Observations");
        requireAtMost(cycle.needs().size(), budget.maximumNeeds(), "Needs");
        requireAtMost(cycle.constraints().size(), budget.maximumConstraints(), "Constraints");
        requireAtMost(cycle.opportunities().size(), budget.maximumOpportunities(), "Opportunities");
        requireAtMost(cycle.candidates().size(), budget.maximumCandidates(), "Candidates");
        requireAtMost(cycle.approvedPlans().size(), budget.maximumApprovedPlans(), "Approved Plans");
        requireAtMost(cycle.submissionRuntimes().stream()
                .filter(value -> value.attemptCount() > 0).count(), budget.maximumSubmissions(), "Submissions");
        if (report.providerWorkUnits() > budget.maximumProviderWorkUnits()
                || report.totalWorkUnits() > budget.maximumTotalWorkUnits()) {
            throw new IllegalArgumentException("Planning work consumption exceeds its budget");
        }

        requireUnique(cycle.observations(), ObservationDefinition::id, "Observation");
        requireUnique(cycle.needs(), NeedDefinition::id, "Need");
        requireUnique(cycle.constraints(), ConstraintDefinition::id, "Constraint");
        requireUnique(cycle.opportunities(), OpportunityDefinition::id, "Opportunity");
        requireUnique(cycle.candidates(), CandidatePlanDefinition::id, "Candidate");
        requireUnique(cycle.approvedPlans(), ApprovedPlanDefinition::id, "Approved Plan");
        requireUnique(cycle.needRuntimes(), NeedResolutionRuntime::needId, "Need runtime");
        requireUnique(cycle.submissionRuntimes(), ApprovedPlanSubmissionRuntime::approvedPlanId,
                "Submission runtime");

        Map<NeedAggregationKey, Integer> aggregationCounts = new LinkedHashMap<>();
        cycle.needs().forEach(need -> aggregationCounts.merge(need.aggregationKey(), 1, Integer::sum));
        aggregationCounts.values().forEach(count ->
                requireAtMost(count, budget.maximumAggregationGroupSize(), "Need aggregation group"));
        for (NeedDefinition need : cycle.needs()) {
            long opportunities = cycle.opportunities().stream()
                    .filter(value -> need.goodId().filter(value.outputGoodId()::equals).isPresent()
                            && need.unit().filter(unit -> unit == value.outputUnit()).isPresent())
                    .count();
            requireAtMost(opportunities, budget.maximumOpportunitiesPerNeed(), "Opportunities per Need");
            long candidates = cycle.candidates().stream()
                    .filter(value -> value.sourceNeedIds().contains(need.id())).count();
            requireAtMost(candidates, budget.maximumCandidatesPerNeed(), "Candidates per Need");
            long approvals = cycle.approvedPlans().stream()
                    .filter(value -> value.needAllocations().stream()
                            .anyMatch(allocation -> allocation.needId().equals(need.id()))).count();
            requireAtMost(approvals, budget.maximumApprovedPlansPerNeed(), "Approved Plans per Need");
        }

        cycle.observations().forEach(value -> requirePayload(value.payload().values()));
        cycle.needs().forEach(value -> requirePayload(value.metadata()));
        cycle.constraints().forEach(value -> requirePayload(value.metadata()));
        cycle.opportunities().forEach(value -> requirePayload(value.metadata()));
        cycle.candidates().forEach(value -> requirePayload(value.metadata()));
        cycle.approvedPlans().forEach(value -> requirePayload(value.metadata()));

        int submitted = (int) cycle.submissionRuntimes().stream()
                .filter(value -> value.status() == PlanningSubmissionStatus.SUBMITTED).count();
        int unresolved = (int) cycle.needRuntimes().stream()
                .filter(value -> value.status() != NeedResolutionStatus.FULLY_RESOLVED).count();
        if (report.observations() != cycle.observations().size()
                || report.needs() != cycle.needs().size()
                || report.constraints() != cycle.constraints().size()
                || report.opportunities() != cycle.opportunities().size()
                || report.candidates() != cycle.candidates().size()
                || report.approvals() != cycle.approvedPlans().size()
                || report.submissions() != submitted || report.unresolvedNeeds() != unresolved) {
            throw new IllegalArgumentException("Planning Cycle report counts are inconsistent");
        }
        if (report.truncated() != (cycle.status() == PlanningCycleStatus.COMPLETED_WITH_REMAINDER)) {
            throw new IllegalArgumentException("Planning Cycle truncation status is inconsistent");
        }
    }

    private void requirePayload(Map<String, String> values) {
        if (PlanningValidation.encodedMapSize(values) > budget.maximumPayloadSize()) {
            throw new IllegalArgumentException("Planning metadata or payload exceeds its budget");
        }
    }

    private static void requireAtMost(long value, long maximum, String label) {
        if (value > maximum) throw new IllegalArgumentException(label + " exceed the Planning budget");
    }

    private static <T, I> void requireUnique(
            Collection<T> values,
            Function<T, I> identity,
            String label
    ) {
        Set<I> ids = new HashSet<>();
        for (T value : values) {
            if (!ids.add(identity.apply(value))) {
                throw new IllegalArgumentException("Duplicate " + label + " identity");
            }
        }
    }

    private void validateReferences(PlanningCycleSnapshot cycle) {
        for (ObservationDefinition observation : cycle.observations()) {
            if (!observation.providerId().equals(PlanningPipeline.CORE_PROVIDER)) {
                throw new IllegalArgumentException("Planning Observation references an unknown provider");
            }
        }
        for (NeedDefinition need : cycle.needs()) {
            if (!need.detectingProviderId().equals(PlanningPipeline.CORE_PROVIDER)) {
                throw new IllegalArgumentException("Planning Need references an unknown provider");
            }
            need.goodId().ifPresent(id -> {
                if (dependencies.goodManager().registry().find(id).isEmpty()) {
                    throw new IllegalArgumentException("Planning Need references unknown Good: " + id.value());
                }
            });
            String order = need.metadata().get("butchercraft:order_id");
            if (order != null) {
                com.butchercraft.world.economy.order.EconomicOrderDefinition definition =
                        dependencies.orderManager().find(com.butchercraft.world.economy.order.OrderId.of(order))
                                .orElseThrow(() -> new IllegalArgumentException(
                                        "Planning Need references unknown Order: " + order));
                String line = need.metadata().get("butchercraft:order_line_id");
                if (line != null && definition.findLine(
                        com.butchercraft.world.economy.order.OrderLineId.of(line)).isEmpty()) {
                    throw new IllegalArgumentException("Planning Need references unknown Order line: " + line);
                }
            }
        }
        for (ConstraintDefinition constraint : cycle.constraints()) {
            if (!constraint.detectingProviderId().equals(PlanningPipeline.CORE_PROVIDER)) {
                throw new IllegalArgumentException("Planning Constraint references an unknown provider");
            }
        }
        for (OpportunityDefinition opportunity : cycle.opportunities()) {
            if (!opportunity.discoveringProviderId().equals(PlanningPipeline.CORE_PROVIDER)) {
                throw new IllegalArgumentException("Planning Opportunity references an unknown provider");
            }
            if (dependencies.productionManager().processRegistry().find(opportunity.processId()).isEmpty()
                    || dependencies.actorManager().find(opportunity.actorId()).isEmpty()) {
                throw new IllegalArgumentException("Planning Opportunity references unknown Production authority");
            }
            opportunity.businessId().ifPresent(id -> {
                if (dependencies.businessRuntimeManager().registry().find(id).isEmpty()) {
                    throw new IllegalArgumentException("Planning Opportunity references unknown Business");
                }
            });
            opportunity.bindings().forEach(binding -> {
                if (dependencies.inventoryManager().find(binding.inventoryId()).isEmpty()) {
                    throw new IllegalArgumentException("Planning Opportunity references unknown Inventory");
                }
            });
        }
        for (ApprovedPlanSubmissionRuntime runtime : cycle.submissionRuntimes()) {
            if (!runtime.adapterId().equals(ProductionPlanningSubmissionAdapter.ID)) {
                throw new IllegalArgumentException("Planning submission references an unknown adapter");
            }
            if (runtime.status() != PlanningSubmissionStatus.SUBMITTED) continue;
            runtime.targetPlanReference().ifPresent(id -> {
                if (dependencies.productionManager().planRegistry().find(
                        com.butchercraft.world.production.ProductionPlanId.of(id)).isEmpty()) {
                    throw new IllegalArgumentException("Planning submission references unknown Production Plan");
                }
            });
            runtime.schedulerWorkReference().ifPresent(id -> {
                if (dependencies.schedulerManager().registry().find(id).isEmpty()) {
                    throw new IllegalArgumentException("Planning submission references unknown Scheduler Work");
                }
            });
        }
    }

    static void validateGraph(PlanningCycleSnapshot cycle) {
        validateGraph(cycle, PlanningExecutionBudget.standard().maximumRecursiveDepth());
    }

    private static void validateGraph(PlanningCycleSnapshot cycle, int maximumDepth) {
        Map<ConstraintId, ConstraintDefinition> constraints = new LinkedHashMap<>();
        cycle.constraints().forEach(value -> {
            if (constraints.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate Planning Constraint");
            }
        });
        for (ConstraintDefinition constraint : constraints.values()) {
            detectConstraintCycle(
                    constraint.id(), constraints, new ArrayList<>(), new java.util.HashSet<>(), maximumDepth);
        }
        java.util.Set<NeedId> needs = cycle.needs().stream().map(NeedDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<OpportunityId> opportunities = cycle.opportunities().stream()
                .map(OpportunityDefinition::id).collect(java.util.stream.Collectors.toSet());
        java.util.Set<CandidatePlanId> candidates = cycle.candidates().stream()
                .map(CandidatePlanDefinition::id).collect(java.util.stream.Collectors.toSet());
        for (CandidatePlanDefinition candidate : cycle.candidates()) {
            if (!needs.containsAll(candidate.sourceNeedIds()) || !opportunities.contains(candidate.opportunityId())) {
                throw new IllegalArgumentException("Candidate provenance does not resolve");
            }
            if (!constraints.keySet().containsAll(candidate.constraints())) {
                throw new IllegalArgumentException("Candidate Constraint provenance does not resolve");
            }
        }
        for (ApprovedPlanDefinition approved : cycle.approvedPlans()) {
            if (!candidates.contains(approved.candidatePlanId())) {
                throw new IllegalArgumentException("Approved Plan Candidate does not resolve");
            }
            if (!needs.containsAll(approved.needAllocations().stream()
                    .map(NeedCoverageAllocation::needId).toList())) {
                throw new IllegalArgumentException("Approved Plan Need allocation does not resolve");
            }
        }
        java.util.Set<ApprovedPlanId> approvals = cycle.approvedPlans().stream()
                .map(ApprovedPlanDefinition::id).collect(java.util.stream.Collectors.toSet());
        for (NeedResolutionRuntime runtime : cycle.needRuntimes()) {
            if (!needs.contains(runtime.needId()) || !candidates.containsAll(runtime.candidates())
                    || !approvals.containsAll(runtime.approvedPlans())
                    || !constraints.keySet().containsAll(runtime.blockingConstraints())) {
                throw new IllegalArgumentException("Need runtime provenance does not resolve");
            }
        }
        for (ApprovedPlanSubmissionRuntime runtime : cycle.submissionRuntimes()) {
            if (!approvals.contains(runtime.approvedPlanId())) {
                throw new IllegalArgumentException("Submission runtime Approved Plan does not resolve");
            }
        }
    }

    private static void detectConstraintCycle(
            ConstraintId id,
            Map<ConstraintId, ConstraintDefinition> definitions,
            List<ConstraintId> path,
            java.util.Set<ConstraintId> complete,
            int maximumDepth
    ) {
        if (complete.contains(id)) return;
        if (path.contains(id)) throw new IllegalArgumentException("Planning Constraint graph contains a cycle");
        if (path.size() >= maximumDepth) {
            throw new IllegalArgumentException("Planning Constraint graph exceeds maximum depth");
        }
        ConstraintDefinition definition = definitions.get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown parent Planning Constraint");
        path.add(id);
        for (ConstraintId parent : definition.parentConstraintIds()) {
            detectConstraintCycle(parent, definitions, path, complete, maximumDepth);
        }
        path.removeLast();
        complete.add(id);
    }
}
