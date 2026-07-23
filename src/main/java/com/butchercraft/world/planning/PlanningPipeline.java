package com.butchercraft.world.planning;

import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.economy.actor.EconomicActorRuntime;
import com.butchercraft.world.economy.order.EconomicOrderDefinition;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderLineDefinition;
import com.butchercraft.world.economy.order.OrderPriority;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryRuntime;
import com.butchercraft.world.production.ProductionBindingDirection;
import com.butchercraft.world.production.ProductionInputDefinition;
import com.butchercraft.world.production.ProductionInventoryBinding;
import com.butchercraft.world.production.ProductionOutputDefinition;
import com.butchercraft.world.production.ProductionFailure;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionPlanDefinition;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionPriority;
import com.butchercraft.world.production.ProductionProcessDefinition;
import com.butchercraft.world.production.ProductionQuantityCalculator;
import com.butchercraft.world.production.ProductionRunSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

public final class PlanningPipeline {
    static final PlanningProviderId CORE_PROVIDER =
            PlanningProviderId.of("butchercraft:core_business_production");
    private static final String ORDER_ID = "butchercraft:order_id";
    private static final String LINE_ID = "butchercraft:order_line_id";
    private static final String REQUESTER = "butchercraft:requester_actor";
    private static final String COUNTERPARTY = "butchercraft:counterparty_actor";
    private static final String GOOD = "butchercraft:good_id";
    private static final String UNIT = "butchercraft:unit";
    private static final String QUANTITY = "butchercraft:quantity";
    private static final String PRIORITY = "butchercraft:priority";
    private static final String REQUIRED_BY = "butchercraft:required_by_tick";
    private static final String CONTRACT = "butchercraft:contract_id";

    private final PlanningDependencies dependencies;
    private final PlanningSelectionPolicy policy;
    private final PlanningExecutionBudget budget;
    private final PlanningSubmissionAdapter submissionAdapter;

    public PlanningPipeline(
            PlanningDependencies dependencies,
            PlanningSelectionPolicy policy,
            PlanningExecutionBudget budget
    ) {
        this.dependencies = java.util.Objects.requireNonNull(dependencies, "dependencies");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
        this.budget = java.util.Objects.requireNonNull(budget, "budget");
        this.submissionAdapter = new ProductionPlanningSubmissionAdapter(dependencies);
    }

    public PlanningCycleSnapshot execute(long tick) {
        PlanningValidation.tick(tick);
        PlanningCycleId cycleId = PlanningCycleId.forTick(tick);
        List<PlanningFailure> failures = new ArrayList<>();
        boolean[] truncated = {false};
        WorkBudget work = new WorkBudget(budget);

        ObservedState observed = observe(tick, truncated);
        List<ObservationDefinition> observations = boundedPayload(
                observed.observations(), work.providerLimit(budget.maximumObservations()),
                value -> value.payload().values(), truncated
        );
        work.consumeProvider(observations.size());
        List<NeedDefinition> needs = boundedPayload(
                detectNeeds(observations, tick), work.providerLimit(budget.maximumNeeds()),
                NeedDefinition::metadata, truncated);
        work.consumeProvider(needs.size());
        List<ConstraintDefinition> constraints = new ArrayList<>();
        List<OpportunityDefinition> opportunities = boundedPayload(
                discoverOpportunities(needs, observed.opportunityFacts(), tick, constraints, truncated, work),
                work.providerLimit(budget.maximumOpportunities()), OpportunityDefinition::metadata, truncated
        );
        work.consumeProvider(opportunities.size());
        List<CandidatePlanDefinition> candidates = boundedPayload(
                generateCandidates(cycleId, needs, opportunities, tick, constraints, truncated, work),
                work.totalLimit(budget.maximumCandidates()), CandidatePlanDefinition::metadata, truncated
        );
        work.consumeTotal(candidates.size());
        List<CandidatePlanDefinition> evaluated =
                evaluate(candidates, constraints, tick, truncated, work);
        Selection selected = select(
                cycleId, needs, constraints, opportunities, evaluated, tick, truncated, work);
        List<ApprovedPlanSubmissionRuntime> submissions =
                submit(selected.approvedPlans(), tick, failures, truncated, work);

        PlanningCycleStatus status = failures.stream()
                .anyMatch(failure -> failure.code() == PlanningFailureCode.INTERNAL_INVARIANT_VIOLATION)
                ? PlanningCycleStatus.FAILED
                : truncated[0] ? PlanningCycleStatus.COMPLETED_WITH_REMAINDER : PlanningCycleStatus.COMPLETED;
        int unresolved = (int) selected.needRuntimes().stream()
                .filter(runtime -> runtime.status() != NeedResolutionStatus.FULLY_RESOLVED).count();
        PlanningCycleReport report = new PlanningCycleReport(
                cycleId, tick, policy.id(), status, observations.size(), needs.size(), constraints.size(),
                opportunities.size(), evaluated.size(), selected.approvedPlans().size(),
                (int) submissions.stream().filter(runtime -> runtime.status() == PlanningSubmissionStatus.SUBMITTED)
                        .count(),
                unresolved, truncated[0], budget, work.providerConsumed(), work.totalConsumed(), failures
        );
        return new PlanningCycleSnapshot(
                cycleId, tick, policy.id(), status, observations, needs, constraints, opportunities,
                evaluated, selected.approvedPlans(), selected.needRuntimes(), submissions, report, 1L,
                PlanningValidation.SCHEMA_VERSION
        );
    }

    private ObservedState observe(long tick, boolean[] truncated) {
        List<ObservationDefinition> observations = new ArrayList<>();
        List<OpportunityDefinition> opportunityFacts = new ArrayList<>();
        Map<String, GoodQuantity> remainingCommitmentCoverage = existingCommitmentCoverage(tick, observations);
        List<EconomicOrderDefinition> orders = dependencies.orderManager().fulfillableOrders().stream()
                .sorted(Comparator.comparing(EconomicOrderDefinition::id)).toList();
        for (EconomicOrderDefinition order : orders) {
            for (OrderLineDefinition line : order.lines()) {
                GoodQuantity remaining = dependencies.orderManager().remainingQuantity(order.id(), line.id());
                String coverageKey = order.id().value() + "|" + line.goodId().value() + "|" + line.unitOfMeasure();
                GoodQuantity covered = minimum(remaining, remainingCommitmentCoverage.getOrDefault(
                        coverageKey, GoodQuantity.zero()));
                remainingCommitmentCoverage.put(coverageKey,
                        remainingCommitmentCoverage.getOrDefault(coverageKey, GoodQuantity.zero()).subtract(covered));
                GoodQuantity uncovered = remaining.subtract(covered);
                if (uncovered.isZero()) continue;
                Map<String, String> payload = new LinkedHashMap<>();
                payload.put(ORDER_ID, order.id().value());
                payload.put(LINE_ID, line.id().value());
                payload.put(REQUESTER, order.requesterActorId().value());
                order.counterpartyActorId().ifPresent(value -> payload.put(COUNTERPARTY, value.value()));
                payload.put(GOOD, line.goodId().value());
                payload.put(UNIT, line.unitOfMeasure().serializedName());
                payload.put(QUANTITY, uncovered.canonicalValue());
                payload.put(PRIORITY, order.priority().serializedName());
                order.requestedFulfillmentTick().ifPresent(value -> payload.put(REQUIRED_BY, Long.toString(value)));
                order.governingContractId().ifPresent(value -> payload.put(CONTRACT, value.value()));
                ObservationId id = ObservationId.of(PlanningValidation.derivedId(
                        "observation", Long.toString(tick), CORE_PROVIDER.value(), "order_line",
                        order.id().value(), line.id().value(), uncovered.canonicalValue()
                ));
                observations.add(new ObservationDefinition(
                        id, CORE_PROVIDER, ObservationType.OPEN_ORDER_LINE, tick,
                        new PlanningOrigin("butchercraft:orders", Optional.of(order.id().value())),
                        List.of(reference("butchercraft:order", order.id().value(), "butchercraft:orders"),
                                reference("butchercraft:order_line", line.id().value(), "butchercraft:orders"),
                                reference("butchercraft:good", line.goodId().value(), "butchercraft:goods")),
                        new PlanningPayload(payload), PlanningValidation.SCHEMA_VERSION
                ));
                opportunityFacts.addAll(observeProductionOpportunities(order, line, uncovered, tick));
                if (observations.size() >= budget.maximumObservations()) {
                    truncated[0] = true;
                    return new ObservedState(observations, opportunityFacts);
                }
            }
        }
        observations.addAll(opportunityFacts.stream().map(opportunity -> new ObservationDefinition(
                ObservationId.of(PlanningValidation.derivedId("observation", Long.toString(tick),
                        CORE_PROVIDER.value(), "production", opportunity.id().value())),
                CORE_PROVIDER, ObservationType.PRODUCTION_PROCESS_AVAILABLE, tick, opportunity.origin(),
                opportunity.supportingReferences(), new PlanningPayload(Map.of(
                        "butchercraft:opportunity_id", opportunity.id().value(),
                        "butchercraft:process_id", opportunity.processId().value(),
                        GOOD, opportunity.outputGoodId().value(),
                        UNIT, opportunity.outputUnit().serializedName()
                )), PlanningValidation.SCHEMA_VERSION
        )).toList());
        observations.add(schedulerCapacityObservation(tick));
        return new ObservedState(observations.stream().sorted().toList(), opportunityFacts);
    }

    private Map<String, GoodQuantity> existingCommitmentCoverage(
            long tick,
            List<ObservationDefinition> observations
    ) {
        Map<String, GoodQuantity> coverage = new LinkedHashMap<>();
        for (ProductionRunSnapshot run : dependencies.productionManager().activeRuns()) {
            ProductionPlanDefinition plan = dependencies.productionManager().planRegistry()
                    .find(run.planId()).orElseThrow();
            if (plan.requestingOrderId().isEmpty()) continue;
            ProductionProcessDefinition process = dependencies.productionManager().processRegistry()
                    .find(plan.processId()).orElseThrow();
            for (ProductionOutputDefinition output : process.outputs()) {
                GoodQuantity quantity = ProductionQuantityCalculator.scaleOutput(
                        output.baseQuantityPerBatch(), plan.batchCount(), output.yieldRatio());
                String key = plan.requestingOrderId().orElseThrow().value() + "|" + output.goodId().value()
                        + "|" + output.unit();
                coverage.merge(key, quantity, GoodQuantity::add);
                observations.add(new ObservationDefinition(
                        ObservationId.of(PlanningValidation.derivedId("observation", Long.toString(tick),
                                CORE_PROVIDER.value(), "commitment", run.id().value(), output.id().value())),
                        CORE_PROVIDER, ObservationType.PRODUCTION_RUN_ACTIVE, tick,
                        new PlanningOrigin("butchercraft:production", Optional.of(run.id().value())),
                        List.of(reference("butchercraft:production_run", run.id().value(),
                                "butchercraft:production")),
                        new PlanningPayload(Map.of(ORDER_ID, plan.requestingOrderId().orElseThrow().value(),
                                GOOD, output.goodId().value(), UNIT, output.unit().serializedName(),
                                QUANTITY, quantity.canonicalValue())),
                        PlanningValidation.SCHEMA_VERSION
                ));
            }
        }
        return coverage;
    }

    private List<OpportunityDefinition> observeProductionOpportunities(
            EconomicOrderDefinition order,
            OrderLineDefinition orderLine,
            GoodQuantity needQuantity,
            long tick
    ) {
        List<OpportunityDefinition> result = new ArrayList<>();
        List<ProductionProcessDefinition> processes = dependencies.productionManager().processRegistry()
                .findByOutputGood(orderLine.goodId()).stream()
                .sorted(Comparator.comparing(ProductionProcessDefinition::id)).toList();
        for (ProductionProcessDefinition process : processes) {
            List<EconomicActorDefinition> actors = dependencies.actorManager().registry().definitions().stream()
                    .filter(actor -> actor.industryId().equals(process.owningIndustryId()))
                    .filter(actor -> actor.hasCapability(process.requiredActorCapability()))
                    .filter(actor -> order.counterpartyActorId().isEmpty()
                            || actor.id().equals(order.counterpartyActorId().orElseThrow()))
                    .sorted(Comparator.comparing(EconomicActorDefinition::id)).toList();
            for (EconomicActorDefinition actor : actors) {
                EconomicActorRuntime actorRuntime = dependencies.actorManager().runtimeFor(actor.id()).orElseThrow();
                List<ProductionInventoryBinding> bindings = bindings(process, actor);
                if (bindings.size() != process.inputs().size() + process.outputs().size()) continue;
                long inputBatches = maximumInputBatches(process, bindings);
                long outputBatches = maximumOutputBatches(process, bindings, tick);
                long processBatches = process.batchPolicy().maximumBatchCount();
                long effectivePolicy = Math.min(processBatches, 10_000L);
                ProductionOutputDefinition output = process.outputs().stream()
                        .filter(value -> value.goodId().equals(orderLine.goodId())
                                && value.unit() == orderLine.unitOfMeasure())
                        .findFirst().orElse(null);
                if (output == null) continue;
                GoodQuantity perBatch = ProductionQuantityCalculator.scaleOutput(
                        output.baseQuantityPerBatch(), 1L, output.yieldRatio());
                OpportunityCapacity capacity = OpportunityCapacity.of(
                        inputBatches, outputBatches, processBatches, effectivePolicy, processBatches);
                Optional<com.butchercraft.world.business.BusinessId> business =
                        actorRuntime.assignedBusinessRuntime();
                List<ConstraintType> blockingReasons = new ArrayList<>();
                if (!actorRuntime.enabled() || !actorRuntime.operational()) {
                    blockingReasons.add(ConstraintType.ACTOR_CAPABILITY_MISSING);
                }
                if (inputBatches == 0L) blockingReasons.add(ConstraintType.INSUFFICIENT_INPUT);
                if (outputBatches == 0L) {
                    blockingReasons.add(ConstraintType.DESTINATION_CAPACITY_INSUFFICIENT);
                }
                ProductionPlanDefinition readinessProbe = readinessProbe(
                        order, process, actor, business, bindings, tick);
                dependencies.productionManager().evaluatePlanReadiness(readinessProbe, tick).stream()
                        .map(PlanningPipeline::constraintType)
                        .forEach(blockingReasons::add);
                blockingReasons = blockingReasons.stream().distinct().sorted().toList();
                boolean available = blockingReasons.isEmpty();
                long completion = Math.addExact(tick, process.duration().baseDurationTicks());
                OpportunityId id = OpportunityId.of(PlanningValidation.derivedId(
                        "opportunity", Long.toString(tick), process.id().value(), actor.id().value(),
                        orderLine.goodId().value(), canonicalBindings(bindings)
                ));
                Map<String, String> metadata = new LinkedHashMap<>();
                metadata.put("butchercraft:need_quantity", needQuantity.canonicalValue());
                metadata.put("butchercraft:input_claims", inputClaims(process, bindings));
                result.add(new OpportunityDefinition(
                        id, OpportunityType.PRODUCTION, CORE_PROVIDER,
                        new PlanningOrigin("butchercraft:production", Optional.of(process.id().value())),
                        actor.id(), business, process.owningIndustryId(), PlanningHorizon.IMMEDIATE,
                        process.id(), output.goodId(), output.unit(), perBatch,
                        new PlanningProcessParameters(
                                process.inputs().stream().map(input -> new PlanningInputRequirement(
                                        input.id(), input.goodId(), input.quantityPerBatch(), input.unit()
                                )).toList(),
                                process.batchPolicy().minimumBatchCount(),
                                process.batchPolicy().maximumBatchCount(),
                                process.batchPolicy().batchIncrement(),
                                process.duration().baseDurationTicks()
                        ),
                        bindings, capacity,
                        available, blockingReasons, tick, completion, 0L,
                        List.of(reference("butchercraft:production_process", process.id().value(),
                                        "butchercraft:production"),
                                reference("butchercraft:actor", actor.id().value(), "butchercraft:actors")),
                        tick, metadata, PlanningValidation.SCHEMA_VERSION
                ));
            }
        }
        return result;
    }

    private static ProductionPlanDefinition readinessProbe(
            EconomicOrderDefinition order,
            ProductionProcessDefinition process,
            EconomicActorDefinition actor,
            Optional<com.butchercraft.world.business.BusinessId> business,
            List<ProductionInventoryBinding> bindings,
            long tick
    ) {
        ProductionPlanDefinition.Builder builder = ProductionPlanDefinition.builder()
                .id(ProductionPlanId.of(PlanningValidation.derivedId(
                        "planning_probe", Long.toString(tick), order.id().value(),
                        process.id().value(), actor.id().value()
                )))
                .processId(process.id()).producerActorId(actor.id()).batchCount(1L)
                .createdSimulationTick(tick).earliestStartTick(tick)
                .priority(ProductionPriority.NORMAL).requestingOrderId(order.id());
        business.ifPresent(builder::businessId);
        order.governingContractId().ifPresent(builder::governingContractId);
        order.requestedFulfillmentTick().ifPresent(builder::latestCompletionTick);
        bindings.forEach(builder::inventoryBinding);
        return builder.build();
    }

    private List<ProductionInventoryBinding> bindings(
            ProductionProcessDefinition process,
            EconomicActorDefinition actor
    ) {
        List<InventoryContainer> inventories = dependencies.inventoryManager().inventoriesOwnedBy(actor.id()).stream()
                .sorted(Comparator.comparing(InventoryContainer::id)).toList();
        List<ProductionInventoryBinding> result = new ArrayList<>();
        for (ProductionInputDefinition input : process.inputs()) {
            InventoryContainer selected = inventories.stream()
                    .filter(value -> input.sourceConstraint().accepts(value.inventoryType()))
                    .filter(value -> dependencies.inventoryManager().runtimeFor(value.id()).orElseThrow()
                            .status().canRelease())
                    .filter(value -> dependencies.inventoryManager().runtimeFor(value.id()).orElseThrow()
                            .quantityOf(input.goodId(), input.unit())
                            >= ProductionQuantityCalculator.toInventoryUnits(input.quantityPerBatch()))
                    .findFirst().orElse(null);
            if (selected != null) result.add(new ProductionInventoryBinding(
                    input.id(), ProductionBindingDirection.INPUT, selected.id(), input.goodId(), input.unit()));
        }
        for (ProductionOutputDefinition output : process.outputs()) {
            InventoryContainer selected = inventories.stream()
                    .filter(value -> output.destinationConstraint().accepts(value.inventoryType()))
                    .filter(value -> dependencies.inventoryManager().runtimeFor(value.id()).orElseThrow()
                            .status().canReceive())
                    .findFirst().orElse(null);
            if (selected != null) result.add(new ProductionInventoryBinding(
                    output.id(), ProductionBindingDirection.OUTPUT, selected.id(), output.goodId(), output.unit()));
        }
        return result.stream().sorted().toList();
    }

    private long maximumInputBatches(
            ProductionProcessDefinition process,
            List<ProductionInventoryBinding> bindings
    ) {
        long maximum = process.batchPolicy().maximumBatchCount();
        for (ProductionInputDefinition input : process.inputs()) {
            ProductionInventoryBinding binding = bindings.stream()
                    .filter(value -> value.direction() == ProductionBindingDirection.INPUT
                            && value.lineId().equals(input.id())).findFirst().orElseThrow();
            InventoryRuntime runtime = dependencies.inventoryManager().runtimeFor(binding.inventoryId()).orElseThrow();
            long perBatch = ProductionQuantityCalculator.toInventoryUnits(input.quantityPerBatch());
            maximum = Math.min(maximum, runtime.quantityOf(input.goodId(), input.unit()) / perBatch);
        }
        return maximum;
    }

    private long maximumOutputBatches(
            ProductionProcessDefinition process,
            List<ProductionInventoryBinding> bindings,
            long tick
    ) {
        long lower = 0L;
        long upper = process.batchPolicy().maximumBatchCount();
        while (lower < upper) {
            long middle = lower + ((upper - lower + 1L) / 2L);
            if (dependencies.inventoryManager().validateChanges(
                    inventoryChanges(process, bindings, middle), tick).isAllowed()) {
                lower = middle;
            } else {
                upper = middle - 1L;
            }
        }
        return lower;
    }

    private static List<InventoryChange> inventoryChanges(
            ProductionProcessDefinition process,
            List<ProductionInventoryBinding> bindings,
            long batches
    ) {
        List<InventoryChange> changes = new ArrayList<>();
        for (ProductionInputDefinition input : process.inputs()) {
            ProductionInventoryBinding binding = bindings.stream()
                    .filter(value -> value.direction() == ProductionBindingDirection.INPUT
                            && value.lineId().equals(input.id())).findFirst().orElseThrow();
            long quantity = ProductionQuantityCalculator.toInventoryUnits(
                    ProductionQuantityCalculator.scaleInput(input.quantityPerBatch(), batches));
            changes.add(InventoryChange.remove(binding.inventoryId(),
                    new InventoryEntry(input.goodId(), quantity, input.unit())));
        }
        for (ProductionOutputDefinition output : process.outputs()) {
            ProductionInventoryBinding binding = bindings.stream()
                    .filter(value -> value.direction() == ProductionBindingDirection.OUTPUT
                            && value.lineId().equals(output.id())).findFirst().orElseThrow();
            long quantity = ProductionQuantityCalculator.toInventoryUnits(
                    ProductionQuantityCalculator.scaleOutput(
                            output.baseQuantityPerBatch(), batches, output.yieldRatio()));
            changes.add(InventoryChange.add(binding.inventoryId(),
                    new InventoryEntry(output.goodId(), quantity, output.unit())));
        }
        return List.copyOf(changes);
    }

    private List<NeedDefinition> detectNeeds(List<ObservationDefinition> observations, long tick) {
        Map<String, NeedDefinition> unique = new LinkedHashMap<>();
        observations.stream().filter(value -> value.type() == ObservationType.OPEN_ORDER_LINE).sorted()
                .forEach(observation -> {
                    PlanningPayload payload = observation.payload();
                    GoodId good = GoodId.of(payload.require(GOOD));
                    com.butchercraft.world.goods.UnitOfMeasure unit =
                            com.butchercraft.world.goods.UnitOfMeasure.fromSerializedName(payload.require(UNIT));
                    GoodQuantity quantity = GoodQuantity.of(payload.require(QUANTITY));
                    Optional<com.butchercraft.world.economy.actor.ActorId> counterparty =
                            Optional.ofNullable(payload.values().get(COUNTERPARTY))
                                    .map(com.butchercraft.world.economy.actor.ActorId::of);
                    Optional<com.butchercraft.world.economy.order.ContractId> contract =
                            Optional.ofNullable(payload.values().get(CONTRACT))
                                    .map(com.butchercraft.world.economy.order.ContractId::of);
                    OptionalLong requiredBy = optionalLong(payload.values().get(REQUIRED_BY));
                    PlanningPriority priority = PlanningPriority.valueOf(
                            payload.require(PRIORITY).toUpperCase(java.util.Locale.ROOT));
                    NeedAggregationKey key = new NeedAggregationKey(
                            NeedType.OUTSTANDING_ORDER_LINE, good, unit, PlanningHorizon.IMMEDIATE,
                            payload.require(ORDER_ID) + "/" + payload.require(LINE_ID), counterparty,
                            requiredBy.orElse(Long.MAX_VALUE), contract, "exact", PlanCategory.PRODUCTION
                    );
                    NeedId id = NeedId.of(PlanningValidation.derivedId(
                            "need", Long.toString(tick), CORE_PROVIDER.value(), "outstanding_order_line",
                            payload.require(ORDER_ID), payload.require(LINE_ID), quantity.canonicalValue()
                    ));
                    Map<String, String> metadata = new LinkedHashMap<>();
                    metadata.put(ORDER_ID, payload.require(ORDER_ID));
                    metadata.put(LINE_ID, payload.require(LINE_ID));
                    metadata.put(REQUESTER, payload.require(REQUESTER));
                    if (payload.values().containsKey(COUNTERPARTY)) {
                        metadata.put(COUNTERPARTY, payload.require(COUNTERPARTY));
                    }
                    if (payload.values().containsKey(CONTRACT)) {
                        metadata.put(CONTRACT, payload.require(CONTRACT));
                    }
                    NeedDefinition need = new NeedDefinition(
                            id, NeedType.OUTSTANDING_ORDER_LINE, CORE_PROVIDER, observation.origin(),
                            observation.references(), PlanningHorizon.IMMEDIATE, priority, tick, requiredBy,
                            requiredBy, Optional.of(good), Optional.of(quantity), Optional.of(unit), key,
                            "whole_process_batches", metadata, PlanningValidation.SCHEMA_VERSION
                    );
                    if (unique.putIfAbsent(key.canonicalKey(), need) != null) {
                        throw new IllegalArgumentException("Duplicate semantic Need in one Planning cycle");
                    }
                });
        return unique.values().stream().sorted(PlanningArtifacts.NEED_ORDER).toList();
    }

    private List<OpportunityDefinition> discoverOpportunities(
            List<NeedDefinition> needs,
            List<OpportunityDefinition> facts,
            long tick,
            List<ConstraintDefinition> constraints,
            boolean[] truncated,
            WorkBudget work
    ) {
        LinkedHashMap<OpportunityId, OpportunityDefinition> result = new LinkedHashMap<>();
        for (NeedDefinition need : needs) {
            List<OpportunityDefinition> matches = facts.stream()
                    .filter(value -> value.outputGoodId().equals(need.goodId().orElseThrow())
                            && value.outputUnit() == need.unit().orElseThrow())
                    .sorted(Comparator.comparing(OpportunityDefinition::id))
                    .limit(budget.maximumOpportunitiesPerNeed()).toList();
            if (matches.isEmpty()) {
                addConstraint(constraints, constraint(
                        ConstraintType.NO_COMPATIBLE_PROCESS, PlanningSeverity.BLOCKING,
                        ConstraintScope.NEED, need.id().value(), tick), truncated, work);
            } else {
                matches.stream().filter(value -> value.capacity().effectiveBatches() == 0L)
                        .flatMap(value -> value.blockingReasons().stream())
                        .distinct().sorted()
                        .forEach(type -> addConstraint(constraints, constraint(
                                type, PlanningSeverity.BLOCKING, ConstraintScope.OPPORTUNITY,
                                need.id().value(), tick
                        ), truncated, work));
            }
            matches.forEach(value -> result.putIfAbsent(value.id(), value));
        }
        return List.copyOf(result.values());
    }

    private List<CandidatePlanDefinition> generateCandidates(
            PlanningCycleId cycleId,
            List<NeedDefinition> needs,
            List<OpportunityDefinition> opportunities,
            long tick,
            List<ConstraintDefinition> constraints,
            boolean[] truncated,
            WorkBudget work
    ) {
        List<CandidatePlanDefinition> result = new ArrayList<>();
        for (NeedDefinition need : needs) {
            int forNeed = 0;
            for (OpportunityDefinition opportunity : opportunities) {
                if (!opportunity.outputGoodId().equals(need.goodId().orElseThrow())
                        || opportunity.outputUnit() != need.unit().orElseThrow()) continue;
                if (forNeed >= budget.maximumCandidatesPerNeed() || result.size() >= budget.maximumCandidates()) break;
                GoodQuantity requested = need.requestedQuantity().orElseThrow();
                long requiredBatches = ceilingBatches(requested, opportunity.outputPerBatch());
                long batches = conformBatchCount(
                        requiredBatches, opportunity.capacity().effectiveBatches(),
                        opportunity.processParameters()
                );
                if (batches <= 0) continue;
                GoodQuantity output = new GoodQuantity(opportunity.outputPerBatch().value()
                        .multiply(BigDecimal.valueOf(batches)));
                GoodQuantity addressed = minimum(requested, output);
                GoodQuantity overproduction = output.subtract(addressed);
                CandidatePlanId candidateId = CandidatePlanId.of(PlanningValidation.derivedId(
                        "candidate", cycleId.value(), need.id().value(), opportunity.id().value(),
                        Long.toString(batches), output.canonicalValue()
                ));
                List<PlanningCapacityClaim> claims = capacityClaims(
                        candidateId, opportunity, batches);
                String orderId = need.metadata().get(ORDER_ID);
                String lineId = need.metadata().get(LINE_ID);
                Optional<com.butchercraft.world.economy.order.ContractId> contract =
                        optionalId(need.metadata().get(CONTRACT),
                                com.butchercraft.world.economy.order.ContractId::of);
                ProductionCandidateAction action = new ProductionCandidateAction(
                        opportunity.processId(), opportunity.actorId(), opportunity.businessId(), batches,
                        opportunity.bindings(), Math.max(tick, opportunity.earliestStartTick()),
                        need.requiredBySimulationTick(),
                        com.butchercraft.world.economy.order.OrderId.of(orderId),
                        com.butchercraft.world.economy.order.OrderLineId.of(lineId),
                        contract, opportunity.outputGoodId(), output,
                        opportunity.processParameters().requiredWorkUnits(batches),
                        productionPriority(need.basePriority()),
                        cycleId.value() + "|" + need.id().value() + "|" + opportunity.id().value()
                );
                CandidateMetrics metrics = new CandidateMetrics(
                        addressed, output, overproduction,
                        Math.addExact(tick, opportunity.processParameters().requiredWorkUnits(batches)),
                        tick, false, batches, opportunity.available() ? 0 : 1, 0,
                        opportunity.capacity().effectiveBatches()
                );
                List<ConstraintId> candidateConstraints = new ArrayList<>();
                CandidateFeasibility feasibility = opportunity.available()
                        ? CandidateFeasibility.UNEVALUATED : CandidateFeasibility.BLOCKED;
                if (!opportunity.available()) {
                    for (ConstraintType type : opportunity.blockingReasons()) {
                        ConstraintDefinition constraint = constraint(
                                type, PlanningSeverity.BLOCKING,
                                ConstraintScope.CANDIDATE, candidateId.value(), tick);
                        if (addConstraint(constraints, constraint, truncated, work)) {
                            candidateConstraints.add(constraint.id());
                        }
                    }
                }
                result.add(new CandidatePlanDefinition(
                        candidateId, PlanCategory.PRODUCTION, cycleId, List.of(need.id()), opportunity.id(),
                        need.horizon(), need.basePriority(), tick, action, claims, metrics,
                        candidateConstraints, feasibility,
                        opportunity.processId().value() + "|" + opportunity.actorId().value()
                                + "|" + batches + "|" + orderId,
                        Map.of(), PlanningValidation.SCHEMA_VERSION
                ));
                forNeed++;
            }
        }
        return result.stream().sorted(PlanningArtifacts.CANDIDATE_ORDER).toList();
    }

    private List<CandidatePlanDefinition> evaluate(
            List<CandidatePlanDefinition> candidates,
            List<ConstraintDefinition> constraints,
            long tick,
            boolean[] truncated,
            WorkBudget work
    ) {
        List<CandidatePlanDefinition> result = new ArrayList<>();
        int evaluationLimit = work.totalLimit(budget.maximumEvaluations());
        if (candidates.size() > evaluationLimit) truncated[0] = true;
        int evaluated = 0;
        for (CandidatePlanDefinition candidate : candidates) {
            if (evaluated >= evaluationLimit) {
                result.add(candidate);
                continue;
            }
            work.consumeTotal(1L);
            evaluated++;
            CandidateFeasibility feasibility = candidate.feasibility() == CandidateFeasibility.UNEVALUATED
                    ? CandidateFeasibility.VALID : candidate.feasibility();
            List<ConstraintId> ids = new ArrayList<>(candidate.constraints());
            if (!candidate.horizon().executable()) feasibility = CandidateFeasibility.UNSUPPORTED;
            if (candidate.action().latestCompletionTick().isPresent()
                    && candidate.metrics().expectedCompletionTick()
                    > candidate.action().latestCompletionTick().orElseThrow()) {
                ConstraintDefinition constraint = constraint(
                        ConstraintType.REQUIRED_BY_TICK_UNACHIEVABLE, PlanningSeverity.BLOCKING,
                        ConstraintScope.CANDIDATE, candidate.id().value(), tick);
                if (addConstraint(constraints, constraint, truncated, work)) ids.add(constraint.id());
                feasibility = CandidateFeasibility.BLOCKED;
            }
            result.add(new CandidatePlanDefinition(
                    candidate.id(), candidate.category(), candidate.cycleId(), candidate.sourceNeedIds(),
                    candidate.opportunityId(), candidate.horizon(), candidate.priority(),
                    candidate.generatedSimulationTick(), candidate.action(), candidate.capacityClaims(),
                    candidate.metrics(), ids, feasibility, candidate.deduplicationKey(),
                    candidate.metadata(), candidate.schemaVersion()
            ));
        }
        return result.stream().sorted(PlanningArtifacts.CANDIDATE_ORDER).toList();
    }

    private Selection select(
            PlanningCycleId cycleId,
            List<NeedDefinition> needs,
            List<ConstraintDefinition> constraints,
            List<OpportunityDefinition> opportunities,
            List<CandidatePlanDefinition> candidates,
            long tick,
            boolean[] truncated,
            WorkBudget work
    ) {
        Map<OpportunityId, Long> opportunityRemaining = new LinkedHashMap<>();
        opportunities.forEach(value -> opportunityRemaining.put(value.id(), value.capacity().effectiveBatches()));
        Map<String, GoodQuantity> sharedRemaining = sharedCapacities(opportunities);
        List<ApprovedPlanDefinition> approved = new ArrayList<>();
        List<NeedResolutionRuntime> runtimes = new ArrayList<>();
        for (NeedDefinition need : needs.stream().sorted(PlanningArtifacts.NEED_ORDER).toList()) {
            GoodQuantity unresolved = need.requestedQuantity().orElseThrow();
            List<CandidatePlanId> candidateIds = new ArrayList<>();
            List<ApprovedPlanId> approvedIds = new ArrayList<>();
            java.util.Set<ConstraintId> blockingConstraintIds = new java.util.TreeSet<>();
            constraints.stream()
                    .filter(value -> value.severity() == PlanningSeverity.BLOCKING)
                    .filter(value -> value.origin().sourceReference()
                            .filter(need.id().value()::equals).isPresent())
                    .map(ConstraintDefinition::id)
                    .forEach(blockingConstraintIds::add);
            int approvedForNeed = 0;
            List<CandidatePlanDefinition> choices = candidates.stream()
                    .filter(value -> value.sourceNeedIds().contains(need.id()))
                    .sorted(PlanningArtifacts.CANDIDATE_ORDER).toList();
            for (CandidatePlanDefinition candidate : choices) {
                candidateIds.add(candidate.id());
                blockingConstraintIds.addAll(candidate.constraints());
                if (candidate.feasibility() != CandidateFeasibility.VALID || unresolved.isZero()) continue;
                if (approved.size() >= Math.min(policy.perCycleApprovalLimit(), budget.maximumApprovedPlans())
                        || approvedForNeed >= Math.min(policy.perNeedApprovalLimit(),
                        budget.maximumApprovedPlansPerNeed())) {
                    truncated[0] = true;
                    break;
                }
                if (!work.consumeTotal(1L)) {
                    truncated[0] = true;
                    break;
                }
                long availableBatches = opportunityRemaining.get(candidate.opportunityId());
                if (availableBatches < candidate.action().batchCount()
                        || !claimsFit(sharedRemaining, candidate.capacityClaims())) continue;
                opportunityRemaining.put(candidate.opportunityId(),
                        availableBatches - candidate.action().batchCount());
                applyClaims(sharedRemaining, candidate.capacityClaims());
                GoodQuantity coverage = minimum(unresolved, candidate.metrics().quantityAddressed());
                unresolved = unresolved.subtract(coverage);
                ApprovalDisposition disposition = candidate.horizon().executable()
                        ? ApprovalDisposition.EXECUTABLE : ApprovalDisposition.ADVISORY;
                ApprovedPlanId approvedId = ApprovedPlanId.of(PlanningValidation.derivedId(
                        "approved_plan", cycleId.value(), candidate.id().value(), policy.id().value(),
                        disposition.name()
                ));
                ApprovedPlanDefinition plan = new ApprovedPlanDefinition(
                        approvedId, cycleId, candidate.id(), candidate.category(), policy.id(), tick,
                        List.of(new NeedCoverageAllocation(need.id(), coverage)),
                        candidate.capacityClaims(), candidate.action(), disposition,
                        PlanningSubmissionStatus.PENDING, Map.of(), PlanningValidation.SCHEMA_VERSION
                );
                approved.add(plan); approvedIds.add(approvedId); approvedForNeed++;
            }
            GoodQuantity allocated = need.requestedQuantity().orElseThrow().subtract(unresolved);
            NeedResolutionStatus status = unresolved.isZero() ? NeedResolutionStatus.FULLY_RESOLVED
                    : allocated.isZero() && !blockingConstraintIds.isEmpty() ? NeedResolutionStatus.BLOCKED
                    : allocated.isZero() ? NeedResolutionStatus.UNRESOLVED
                    : NeedResolutionStatus.PARTIALLY_RESOLVED;
            runtimes.add(new NeedResolutionRuntime(
                    need.id(), status, need.requestedQuantity().orElseThrow(), GoodQuantity.zero(),
                    allocated, unresolved, candidateIds, approvedIds,
                    List.copyOf(blockingConstraintIds), tick, Optional.empty(), 1L
            ));
        }
        return new Selection(List.copyOf(approved), List.copyOf(runtimes));
    }

    private List<ApprovedPlanSubmissionRuntime> submit(
            List<ApprovedPlanDefinition> plans,
            long tick,
            List<PlanningFailure> failures,
            boolean[] truncated,
            WorkBudget work
    ) {
        List<ApprovedPlanSubmissionRuntime> result = new ArrayList<>();
        int submitted = 0;
        for (ApprovedPlanDefinition plan : plans) {
            if (plan.disposition() != ApprovalDisposition.EXECUTABLE) {
                result.add(ApprovedPlanSubmissionRuntime.pending(plan.id(), submissionAdapter.id(), tick));
                continue;
            }
            if (submitted >= budget.maximumSubmissions()) {
                truncated[0] = true;
                result.add(ApprovedPlanSubmissionRuntime.pending(plan.id(), submissionAdapter.id(), tick));
                continue;
            }
            if (!work.consumeTotal(1L)) {
                truncated[0] = true;
                result.add(ApprovedPlanSubmissionRuntime.pending(plan.id(), submissionAdapter.id(), tick));
                continue;
            }
            PlanningSubmissionResult submission = submissionAdapter.submit(plan, tick);
            submission.failure().ifPresent(failures::add);
            result.add(new ApprovedPlanSubmissionRuntime(
                    plan.id(), submission.status(), tick, 1, submissionAdapter.id(),
                    submission.targetPlanReference(), submission.workReference(), submission.failure(), 1L
            ));
            submitted++;
        }
        return List.copyOf(result);
    }

    private List<PlanningCapacityClaim> capacityClaims(
            CandidatePlanId candidateId,
            OpportunityDefinition opportunity,
            long batches
    ) {
        List<PlanningCapacityClaim> claims = new ArrayList<>();
        claims.add(new PlanningCapacityClaim(
                "opportunity|" + opportunity.id().value(), "process_batch", GoodQuantity.of(batches),
                Optional.empty(), opportunity.id(), candidateId, opportunity.supportingReferences()
        ));
        for (PlanningInputRequirement input : opportunity.processParameters().inputRequirements()) {
            ProductionInventoryBinding binding = opportunity.bindings().stream()
                    .filter(value -> value.direction() == ProductionBindingDirection.INPUT
                            && value.lineId().equals(input.lineId())).findFirst().orElseThrow();
            GoodQuantity quantity = ProductionQuantityCalculator.scaleInput(input.quantityPerBatch(), batches);
            claims.add(new PlanningCapacityClaim(
                    "inventory|" + binding.inventoryId().value() + "|" + input.goodId().value()
                            + "|" + input.unit().serializedName(),
                    "input_good", quantity, Optional.of(input.unit()), opportunity.id(), candidateId,
                    List.of(reference("butchercraft:inventory", binding.inventoryId().value(),
                            "butchercraft:inventory"))
            ));
        }
        return List.copyOf(claims);
    }

    private Map<String, GoodQuantity> sharedCapacities(List<OpportunityDefinition> opportunities) {
        Map<String, GoodQuantity> capacities = new LinkedHashMap<>();
        for (OpportunityDefinition opportunity : opportunities) {
            capacities.put("opportunity|" + opportunity.id().value(),
                    GoodQuantity.of(opportunity.capacity().effectiveBatches()));
            String encoded = opportunity.metadata().getOrDefault("butchercraft:input_claims", "");
            if (encoded.isEmpty()) continue;
            for (String claim : encoded.split(";")) {
                String[] fields = claim.split("\\|", -1);
                capacities.putIfAbsent("inventory|" + fields[0] + "|" + fields[1] + "|" + fields[2],
                        GoodQuantity.of(fields[3]));
            }
        }
        return capacities;
    }

    private static boolean claimsFit(
            Map<String, GoodQuantity> remaining,
            List<PlanningCapacityClaim> claims
    ) {
        return claims.stream().allMatch(claim -> remaining.containsKey(claim.key())
                && remaining.get(claim.key()).compareTo(claim.quantity()) >= 0);
    }

    private static void applyClaims(
            Map<String, GoodQuantity> remaining,
            List<PlanningCapacityClaim> claims
    ) {
        Map<String, GoodQuantity> detached = new LinkedHashMap<>(remaining);
        claims.forEach(claim -> detached.put(claim.key(), detached.get(claim.key()).subtract(claim.quantity())));
        remaining.clear();
        remaining.putAll(detached);
    }

    private String inputClaims(
            ProductionProcessDefinition process,
            List<ProductionInventoryBinding> bindings
    ) {
        List<String> claims = new ArrayList<>();
        for (ProductionInputDefinition input : process.inputs()) {
            ProductionInventoryBinding binding = bindings.stream()
                    .filter(value -> value.direction() == ProductionBindingDirection.INPUT
                            && value.lineId().equals(input.id())).findFirst().orElseThrow();
            long available = dependencies.inventoryManager().runtimeFor(binding.inventoryId()).orElseThrow()
                    .quantityOf(input.goodId(), input.unit());
            claims.add(binding.inventoryId().value() + "|" + input.goodId().value() + "|"
                    + input.unit().serializedName() + "|" + available);
        }
        return String.join(";", claims);
    }

    private ObservationDefinition schedulerCapacityObservation(long tick) {
        return new ObservationDefinition(
                ObservationId.of(PlanningValidation.derivedId(
                        "observation", Long.toString(tick), CORE_PROVIDER.value(), "scheduler_capacity")),
                CORE_PROVIDER, ObservationType.SCHEDULER_CAPACITY, tick,
                new PlanningOrigin("butchercraft:scheduler", Optional.empty()),
                List.of(), new PlanningPayload(Map.of(
                        "butchercraft:next_sequence",
                        Long.toString(dependencies.schedulerManager().nextSubmissionSequence())
                )), PlanningValidation.SCHEMA_VERSION
        );
    }

    private static ConstraintDefinition constraint(
            ConstraintType type,
            PlanningSeverity severity,
            ConstraintScope scope,
            String affected,
            long tick
    ) {
        ConstraintId id = ConstraintId.of(PlanningValidation.derivedId(
                "constraint", Long.toString(tick), type.name(), scope.name(), affected));
        return new ConstraintDefinition(
                id, type, CORE_PROVIDER,
                new PlanningOrigin("butchercraft:planning", Optional.of(affected)),
                severity, List.of(), List.of(), tick, OptionalLong.empty(), scope,
                Map.of(), PlanningValidation.SCHEMA_VERSION
        );
    }

    private static ProductionPriority productionPriority(PlanningPriority value) {
        return ProductionPriority.valueOf(value.name());
    }

    private static ConstraintType constraintType(ProductionFailure failure) {
        return switch (failure.code()) {
            case ACTOR_CAPABILITY_MISSING -> ConstraintType.ACTOR_CAPABILITY_MISSING;
            case BUSINESS_NOT_OPERATIONAL, UNKNOWN_BUSINESS -> ConstraintType.BUSINESS_NOT_OPERATIONAL;
            case BUSINESS_CLOSED -> ConstraintType.BUSINESS_CLOSED;
            case BUSINESS_IN_MAINTENANCE -> ConstraintType.BUSINESS_IN_MAINTENANCE;
            case NO_ACTIVE_SHIFT -> ConstraintType.NO_ACTIVE_SHIFT;
            case WORKFORCE_INSUFFICIENT, REQUIRED_POSITION_MISSING, REQUIRED_CERTIFICATION_MISSING,
                    REQUIRED_SKILL_MISSING, UNKNOWN_WORKFORCE_REFERENCE ->
                    ConstraintType.WORKFORCE_INSUFFICIENT;
            case INSUFFICIENT_INPUT -> ConstraintType.INSUFFICIENT_INPUT;
            case DESTINATION_CAPACITY_EXCEEDED -> ConstraintType.DESTINATION_CAPACITY_INSUFFICIENT;
            case INVENTORY_STATUS_INVALID, UNKNOWN_INVENTORY ->
                    ConstraintType.OUTPUT_INVENTORY_UNAVAILABLE;
            case RUN_EXPIRED -> ConstraintType.EXPIRED_NEED;
            case VALIDATION_FAILED, INPUT_BINDING_MISSING, OUTPUT_BINDING_MISSING,
                    DUPLICATE_BINDING, INPUT_GOOD_MISMATCH, OUTPUT_GOOD_MISMATCH, UNIT_MISMATCH ->
                    ConstraintType.POLICY_REJECTED;
            default -> ConstraintType.POLICY_REJECTED;
        };
    }

    private static long ceilingBatches(GoodQuantity quantity, GoodQuantity perBatch) {
        return quantity.value().divide(perBatch.value(), 0, RoundingMode.CEILING).longValueExact();
    }

    private static long conformBatchCount(
            long requested,
            long available,
            PlanningProcessParameters process
    ) {
        if (requested <= 0 || available <= 0) return 0;
        long minimum = process.minimumBatchCount();
        long maximum = Math.min(process.maximumBatchCount(), available);
        if (maximum < minimum) return 0;
        long value = Math.max(minimum, Math.min(requested, maximum));
        long remainder = Math.floorMod(value - minimum, process.batchIncrement());
        if (remainder != 0L) {
            long roundedUp = Math.addExact(value, process.batchIncrement() - remainder);
            value = roundedUp <= maximum ? roundedUp : value - remainder;
        }
        return value < minimum ? 0 : value;
    }

    private static GoodQuantity minimum(GoodQuantity left, GoodQuantity right) {
        return left.compareTo(right) <= 0 ? left : right;
    }

    private static OptionalLong optionalLong(String value) {
        return value == null || value.isBlank() ? OptionalLong.empty()
                : OptionalLong.of(PlanningValidation.tick(Long.parseLong(value)));
    }

    private static <T> Optional<T> optionalId(String value, java.util.function.Function<String, T> factory) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(factory.apply(value));
    }

    private static PlanningReference reference(String type, String id, String source) {
        return new PlanningReference(type, id, source, Optional.empty());
    }

    private static String canonicalBindings(List<ProductionInventoryBinding> bindings) {
        return bindings.stream().sorted().map(value -> value.direction() + "|" + value.lineId().value()
                + "|" + value.inventoryId().value()).collect(java.util.stream.Collectors.joining(";"));
    }

    private <T> List<T> boundedPayload(
            List<T> values,
            int maximum,
            Function<T, Map<String, String>> payload,
            boolean[] truncated
    ) {
        List<T> accepted = new ArrayList<>();
        for (T value : values) {
            if (PlanningValidation.encodedMapSize(payload.apply(value)) > budget.maximumPayloadSize()) {
                truncated[0] = true;
                continue;
            }
            if (accepted.size() >= maximum) {
                truncated[0] = true;
                break;
            }
            accepted.add(value);
        }
        return List.copyOf(accepted);
    }

    private boolean addConstraint(
            List<ConstraintDefinition> constraints,
            ConstraintDefinition constraint,
            boolean[] truncated,
            WorkBudget work
    ) {
        if (constraints.stream().anyMatch(existing -> existing.id().equals(constraint.id()))) return true;
        if (constraints.size() >= budget.maximumConstraints()) {
            truncated[0] = true;
            return false;
        }
        if (work != null && !work.consumeProvider(1L)) {
            truncated[0] = true;
            return false;
        }
        constraints.add(constraint);
        return true;
    }

    private record ObservedState(
            List<ObservationDefinition> observations,
            List<OpportunityDefinition> opportunityFacts
    ) {
    }

    private record Selection(
            List<ApprovedPlanDefinition> approvedPlans,
            List<NeedResolutionRuntime> needRuntimes
    ) {
    }

    private static final class WorkBudget {
        private final PlanningExecutionBudget budget;
        private long providerConsumed;
        private long totalConsumed;

        private WorkBudget(PlanningExecutionBudget budget) {
            this.budget = budget;
        }

        private int providerLimit(int artifactLimit) {
            return boundedInt(Math.min(
                    budget.maximumProviderWorkUnits() - providerConsumed,
                    budget.maximumTotalWorkUnits() - totalConsumed
            ), artifactLimit);
        }

        private int totalLimit(int artifactLimit) {
            return boundedInt(budget.maximumTotalWorkUnits() - totalConsumed, artifactLimit);
        }

        private boolean consumeProvider(long units) {
            if (!canConsume(units, budget.maximumProviderWorkUnits() - providerConsumed)
                    || !canConsume(units, budget.maximumTotalWorkUnits() - totalConsumed)) {
                return false;
            }
            providerConsumed = Math.addExact(providerConsumed, units);
            totalConsumed = Math.addExact(totalConsumed, units);
            return true;
        }

        private boolean consumeTotal(long units) {
            if (!canConsume(units, budget.maximumTotalWorkUnits() - totalConsumed)) return false;
            totalConsumed = Math.addExact(totalConsumed, units);
            return true;
        }

        private long providerConsumed() {
            return providerConsumed;
        }

        private long totalConsumed() {
            return totalConsumed;
        }

        private static int boundedInt(long remaining, int artifactLimit) {
            if (remaining <= 0L) return 0;
            return (int) Math.min(artifactLimit, Math.min(remaining, Integer.MAX_VALUE));
        }

        private static boolean canConsume(long units, long remaining) {
            return units >= 0L && units <= remaining;
        }
    }
}
