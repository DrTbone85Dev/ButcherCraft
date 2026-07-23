package com.butchercraft.world.planning;

import com.butchercraft.world.business.BusinessId;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.order.ContractId;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.economy.order.OrderLineId;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.IndustryId;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.production.ProductionInventoryBinding;
import com.butchercraft.world.production.ProductionLineId;
import com.butchercraft.world.production.ProductionPriority;
import com.butchercraft.world.production.ProductionProcessId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class PlanningArtifacts {
    private PlanningArtifacts() {
    }

    static final Comparator<NeedDefinition> NEED_ORDER = Comparator
            .comparingInt((NeedDefinition value) -> value.horizon().precedence())
            .thenComparing(Comparator.comparingInt(
                    (NeedDefinition value) -> value.basePriority().precedence()).reversed())
            .thenComparingLong(value -> value.requiredBySimulationTick().orElse(Long.MAX_VALUE))
            .thenComparing(NeedDefinition::createdSimulationTick)
            .thenComparing(value -> value.type().name())
            .thenComparing(value -> value.goodId().map(GoodId::value).orElse(""))
            .thenComparing(NeedDefinition::id);

    static final Comparator<CandidatePlanDefinition> CANDIDATE_ORDER = Comparator
            .comparingInt((CandidatePlanDefinition value) -> value.horizon().precedence())
            .thenComparing(Comparator.comparingInt(
                    (CandidatePlanDefinition value) -> value.priority().precedence()).reversed())
            .thenComparingLong(value -> value.metrics().expectedCompletionTick())
            .thenComparing((CandidatePlanDefinition value) -> value.metrics().quantityAddressed(),
                    Comparator.reverseOrder())
            .thenComparing(value -> value.metrics().overproduction())
            .thenComparingLong(value -> value.action().batchCount())
            .thenComparing(CandidatePlanDefinition::id);
}

enum ObservationType {
    OPEN_ORDER_LINE, EXISTING_ORDER_FULFILLMENT, INVENTORY_QUANTITY, INVENTORY_STATUS,
    INVENTORY_CAPACITY, ACTOR_CAPABILITY, BUSINESS_OPERATIONAL_STATE, BUSINESS_ACTIVE_SHIFT,
    WORKFORCE_CAPACITY, PRODUCTION_PROCESS_AVAILABLE, PRODUCTION_PLAN_ACTIVE,
    PRODUCTION_RUN_ACTIVE, PRODUCTION_RUN_BLOCKED, SCHEDULER_CAPACITY, CONTRACT_CONTEXT
}

record PlanningOrigin(String sourceSubsystem, Optional<String> sourceReference) {
    PlanningOrigin {
        sourceSubsystem = PlanningValidation.id(sourceSubsystem, "Planning origin subsystem");
        sourceReference = Objects.requireNonNull(sourceReference, "sourceReference")
                .map(value -> PlanningValidation.id(value, "Planning origin reference"));
    }
    String canonicalKey() { return sourceSubsystem + "|" + sourceReference.orElse(""); }
}

record PlanningReference(String type, String id, String sourceSubsystem, Optional<String> role)
        implements Comparable<PlanningReference> {
    PlanningReference {
        type = PlanningValidation.id(type, "Planning reference type");
        id = PlanningValidation.id(id, "Planning reference id");
        sourceSubsystem = PlanningValidation.id(sourceSubsystem, "Planning reference subsystem");
        role = Objects.requireNonNull(role, "role").map(value -> PlanningValidation.id(value, "Planning role"));
    }
    @Override public int compareTo(PlanningReference other) {
        return (type + "|" + id + "|" + role.orElse(""))
                .compareTo(other.type + "|" + other.id + "|" + other.role.orElse(""));
    }
}

record PlanningPayload(Map<String, String> values) {
    PlanningPayload { values = PlanningValidation.metadata(values); }
    String require(String key) {
        String value = values.get(key);
        if (value == null) throw new IllegalArgumentException("Missing Planning payload value: " + key);
        return value;
    }
}

record ObservationDefinition(
        ObservationId id,
        PlanningProviderId providerId,
        ObservationType type,
        long observedSimulationTick,
        PlanningOrigin origin,
        List<PlanningReference> references,
        PlanningPayload payload,
        int schemaVersion
) implements Comparable<ObservationDefinition> {
    ObservationDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(providerId); Objects.requireNonNull(type);
        observedSimulationTick = PlanningValidation.tick(observedSimulationTick);
        Objects.requireNonNull(origin); references = references.stream().sorted().toList();
        Objects.requireNonNull(payload); schemaVersion = PlanningValidation.schema(schemaVersion);
    }
    @Override public int compareTo(ObservationDefinition other) {
        return Comparator.comparingLong(ObservationDefinition::observedSimulationTick)
                .thenComparing(ObservationDefinition::providerId)
                .thenComparing(value -> value.type().name())
                .thenComparing(value -> value.origin().canonicalKey())
                .thenComparing(ObservationDefinition::id).compare(this, other);
    }
}

record NeedAggregationKey(
        NeedType type,
        GoodId goodId,
        UnitOfMeasure unit,
        PlanningHorizon horizon,
        String destinationKey,
        Optional<ActorId> counterparty,
        long requiredByBucket,
        Optional<ContractId> contractId,
        String substitutionPolicy,
        PlanCategory category
) implements Comparable<NeedAggregationKey> {
    NeedAggregationKey {
        Objects.requireNonNull(type); Objects.requireNonNull(goodId); Objects.requireNonNull(unit);
        Objects.requireNonNull(horizon); destinationKey = PlanningValidation.text(destinationKey, "Destination key");
        Objects.requireNonNull(counterparty); Objects.requireNonNull(contractId);
        substitutionPolicy = PlanningValidation.text(substitutionPolicy, "Substitution policy");
        Objects.requireNonNull(category);
    }
    String canonicalKey() {
        return type + "|" + goodId.value() + "|" + unit + "|" + horizon + "|" + destinationKey + "|"
                + counterparty.map(ActorId::value).orElse("") + "|" + requiredByBucket + "|"
                + contractId.map(ContractId::value).orElse("") + "|" + substitutionPolicy + "|" + category;
    }
    @Override public int compareTo(NeedAggregationKey other) { return canonicalKey().compareTo(other.canonicalKey()); }
}

record NeedDefinition(
        NeedId id,
        NeedType type,
        PlanningProviderId detectingProviderId,
        PlanningOrigin origin,
        List<PlanningReference> sourceReferences,
        PlanningHorizon horizon,
        PlanningPriority basePriority,
        long createdSimulationTick,
        OptionalLong requiredBySimulationTick,
        OptionalLong expirationSimulationTick,
        Optional<GoodId> goodId,
        Optional<GoodQuantity> requestedQuantity,
        Optional<UnitOfMeasure> unit,
        NeedAggregationKey aggregationKey,
        String splitPolicy,
        Map<String, String> metadata,
        int schemaVersion
) {
    NeedDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(detectingProviderId);
        Objects.requireNonNull(origin); sourceReferences = sourceReferences.stream().sorted().toList();
        Objects.requireNonNull(horizon); Objects.requireNonNull(basePriority);
        createdSimulationTick = PlanningValidation.tick(createdSimulationTick);
        Objects.requireNonNull(requiredBySimulationTick); Objects.requireNonNull(expirationSimulationTick);
        Objects.requireNonNull(goodId); Objects.requireNonNull(requestedQuantity); Objects.requireNonNull(unit);
        if (requestedQuantity.isPresent()) requestedQuantity.get().requirePositive("Need quantity");
        Objects.requireNonNull(aggregationKey);
        splitPolicy = PlanningValidation.text(splitPolicy, "Need split policy");
        metadata = PlanningValidation.metadata(metadata); schemaVersion = PlanningValidation.schema(schemaVersion);
    }
}

record ConstraintDefinition(
        ConstraintId id,
        ConstraintType type,
        PlanningProviderId detectingProviderId,
        PlanningOrigin origin,
        PlanningSeverity severity,
        List<PlanningReference> affectedReferences,
        List<ConstraintId> parentConstraintIds,
        long createdSimulationTick,
        OptionalLong expirationSimulationTick,
        ConstraintScope scope,
        Map<String, String> metadata,
        int schemaVersion
) {
    ConstraintDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(detectingProviderId);
        Objects.requireNonNull(origin); Objects.requireNonNull(severity);
        affectedReferences = affectedReferences.stream().sorted().toList();
        parentConstraintIds = parentConstraintIds.stream().sorted().distinct().toList();
        if (parentConstraintIds.contains(id)) throw new IllegalArgumentException("Constraint cannot depend on itself");
        createdSimulationTick = PlanningValidation.tick(createdSimulationTick);
        Objects.requireNonNull(expirationSimulationTick); Objects.requireNonNull(scope);
        metadata = PlanningValidation.metadata(metadata); schemaVersion = PlanningValidation.schema(schemaVersion);
    }
}

record OpportunityCapacity(long inputBatches, long outputBatches, long processBatches,
                           long policyBatches, long commitmentBatches, long effectiveBatches) {
    OpportunityCapacity {
        if (inputBatches < 0 || outputBatches < 0 || processBatches < 0 || policyBatches < 0
                || commitmentBatches < 0 || effectiveBatches < 0) {
            throw new IllegalArgumentException("Opportunity capacity must not be negative");
        }
        long expected = Math.min(Math.min(inputBatches, outputBatches),
                Math.min(processBatches, Math.min(policyBatches, commitmentBatches)));
        if (effectiveBatches != expected) throw new IllegalArgumentException("Effective capacity is not the minimum");
    }
    static OpportunityCapacity of(long input, long output, long process, long policy, long commitments) {
        return new OpportunityCapacity(input, output, process, policy, commitments,
                Math.min(Math.min(input, output), Math.min(process, Math.min(policy, commitments))));
    }
}

record PlanningInputRequirement(
        ProductionLineId lineId,
        GoodId goodId,
        GoodQuantity quantityPerBatch,
        UnitOfMeasure unit
) {
    PlanningInputRequirement {
        Objects.requireNonNull(lineId);
        Objects.requireNonNull(goodId);
        quantityPerBatch = Objects.requireNonNull(quantityPerBatch)
                .requirePositive("Planning input quantity");
        Objects.requireNonNull(unit);
    }
}

record PlanningProcessParameters(
        List<PlanningInputRequirement> inputRequirements,
        long minimumBatchCount,
        long maximumBatchCount,
        long batchIncrement,
        long workUnitsPerBatch
) {
    PlanningProcessParameters {
        inputRequirements = List.copyOf(inputRequirements);
        if (minimumBatchCount <= 0 || maximumBatchCount < minimumBatchCount
                || batchIncrement <= 0 || workUnitsPerBatch <= 0) {
            throw new IllegalArgumentException("Planning process parameters are invalid");
        }
    }

    long requiredWorkUnits(long batchCount) {
        if (batchCount <= 0) throw new IllegalArgumentException("Planning batch count must be positive");
        return Math.multiplyExact(workUnitsPerBatch, batchCount);
    }
}

record OpportunityDefinition(
        OpportunityId id,
        OpportunityType type,
        PlanningProviderId discoveringProviderId,
        PlanningOrigin origin,
        ActorId actorId,
        Optional<BusinessId> businessId,
        IndustryId industryId,
        PlanningHorizon supportedHorizon,
        ProductionProcessId processId,
        GoodId outputGoodId,
        UnitOfMeasure outputUnit,
        GoodQuantity outputPerBatch,
        PlanningProcessParameters processParameters,
        List<ProductionInventoryBinding> bindings,
        OpportunityCapacity capacity,
        boolean available,
        List<ConstraintType> blockingReasons,
        long earliestStartTick,
        long estimatedCompletionTick,
        long existingCommitmentLoad,
        List<PlanningReference> supportingReferences,
        long observedSimulationTick,
        Map<String, String> metadata,
        int schemaVersion
) {
    OpportunityDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(type); Objects.requireNonNull(discoveringProviderId);
        Objects.requireNonNull(origin); Objects.requireNonNull(actorId); Objects.requireNonNull(businessId);
        Objects.requireNonNull(industryId); Objects.requireNonNull(supportedHorizon);
        Objects.requireNonNull(processId); Objects.requireNonNull(outputGoodId); Objects.requireNonNull(outputUnit);
        outputPerBatch = Objects.requireNonNull(outputPerBatch).requirePositive("Opportunity output");
        Objects.requireNonNull(processParameters); bindings = List.copyOf(bindings); Objects.requireNonNull(capacity);
        blockingReasons = blockingReasons.stream().distinct().sorted().toList();
        if (available != blockingReasons.isEmpty()) {
            throw new IllegalArgumentException("Opportunity availability must match its blocking reasons");
        }
        earliestStartTick = PlanningValidation.tick(earliestStartTick);
        estimatedCompletionTick = PlanningValidation.tick(estimatedCompletionTick);
        if (existingCommitmentLoad < 0) throw new IllegalArgumentException("Commitment load must not be negative");
        supportingReferences = supportingReferences.stream().sorted().toList();
        observedSimulationTick = PlanningValidation.tick(observedSimulationTick);
        metadata = PlanningValidation.metadata(metadata); schemaVersion = PlanningValidation.schema(schemaVersion);
    }
}

record PlanningCapacityClaim(String key, String type, GoodQuantity quantity, Optional<UnitOfMeasure> unit,
                             OpportunityId opportunityId, CandidatePlanId candidateId,
                             List<PlanningReference> references) {
    PlanningCapacityClaim {
        key = PlanningValidation.text(key, "Capacity key"); type = PlanningValidation.text(type, "Capacity type");
        quantity = Objects.requireNonNull(quantity).requirePositive("Capacity claim");
        Objects.requireNonNull(unit); Objects.requireNonNull(opportunityId); Objects.requireNonNull(candidateId);
        references = references.stream().sorted().toList();
    }
}

record ProductionCandidateAction(
        ProductionProcessId processId,
        ActorId producerActorId,
        Optional<BusinessId> businessId,
        long batchCount,
        List<ProductionInventoryBinding> bindings,
        long earliestStartTick,
        OptionalLong latestCompletionTick,
        OrderId orderId,
        OrderLineId orderLineId,
        Optional<ContractId> contractId,
        GoodId outputGoodId,
        GoodQuantity expectedOutput,
        long expectedDuration,
        ProductionPriority productionPriority,
        String submissionIdentityKey
) {
    ProductionCandidateAction {
        Objects.requireNonNull(processId); Objects.requireNonNull(producerActorId); Objects.requireNonNull(businessId);
        if (batchCount <= 0) throw new IllegalArgumentException("Candidate batches must be positive");
        bindings = List.copyOf(bindings); earliestStartTick = PlanningValidation.tick(earliestStartTick);
        Objects.requireNonNull(latestCompletionTick); Objects.requireNonNull(orderId); Objects.requireNonNull(orderLineId);
        Objects.requireNonNull(contractId); Objects.requireNonNull(outputGoodId);
        expectedOutput = Objects.requireNonNull(expectedOutput).requirePositive("Candidate output");
        if (expectedDuration <= 0) throw new IllegalArgumentException("Candidate duration must be positive");
        Objects.requireNonNull(productionPriority);
        submissionIdentityKey = PlanningValidation.text(submissionIdentityKey, "Submission identity key");
    }
}

record CandidateMetrics(GoodQuantity quantityAddressed, GoodQuantity expectedOutput,
                        GoodQuantity overproduction, long expectedCompletionTick, long earliestStartTick,
                        boolean existingCommitmentReuse, long wholeBatchCount, int blockingConstraints,
                        int warningConstraints, long remainingOpportunityBatches) {
    CandidateMetrics {
        Objects.requireNonNull(quantityAddressed); Objects.requireNonNull(expectedOutput);
        Objects.requireNonNull(overproduction);
        PlanningValidation.tick(expectedCompletionTick); PlanningValidation.tick(earliestStartTick);
        if (wholeBatchCount <= 0 || blockingConstraints < 0 || warningConstraints < 0
                || remainingOpportunityBatches < 0) throw new IllegalArgumentException("Invalid candidate metrics");
    }
}

record CandidatePlanDefinition(
        CandidatePlanId id,
        PlanCategory category,
        PlanningCycleId cycleId,
        List<NeedId> sourceNeedIds,
        OpportunityId opportunityId,
        PlanningHorizon horizon,
        PlanningPriority priority,
        long generatedSimulationTick,
        ProductionCandidateAction action,
        List<PlanningCapacityClaim> capacityClaims,
        CandidateMetrics metrics,
        List<ConstraintId> constraints,
        CandidateFeasibility feasibility,
        String deduplicationKey,
        Map<String, String> metadata,
        int schemaVersion
) {
    CandidatePlanDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(category); Objects.requireNonNull(cycleId);
        sourceNeedIds = sourceNeedIds.stream().sorted().distinct().toList();
        if (sourceNeedIds.isEmpty()) throw new IllegalArgumentException("Candidate requires a Need");
        Objects.requireNonNull(opportunityId); Objects.requireNonNull(horizon); Objects.requireNonNull(priority);
        generatedSimulationTick = PlanningValidation.tick(generatedSimulationTick);
        Objects.requireNonNull(action); capacityClaims = List.copyOf(capacityClaims);
        Objects.requireNonNull(metrics); constraints = constraints.stream().sorted().distinct().toList();
        Objects.requireNonNull(feasibility);
        deduplicationKey = PlanningValidation.text(deduplicationKey, "Candidate deduplication key");
        metadata = PlanningValidation.metadata(metadata); schemaVersion = PlanningValidation.schema(schemaVersion);
    }
}

record NeedCoverageAllocation(NeedId needId, GoodQuantity quantity) {
    NeedCoverageAllocation {
        Objects.requireNonNull(needId); quantity = Objects.requireNonNull(quantity)
                .requirePositive("Need coverage quantity");
    }
}

record ApprovedPlanDefinition(
        ApprovedPlanId id,
        PlanningCycleId cycleId,
        CandidatePlanId candidatePlanId,
        PlanCategory category,
        PlanningPolicyId selectionPolicyId,
        long approvedSimulationTick,
        List<NeedCoverageAllocation> needAllocations,
        List<PlanningCapacityClaim> acceptedCapacityClaims,
        ProductionCandidateAction approvedAction,
        ApprovalDisposition disposition,
        PlanningSubmissionStatus initialSubmissionState,
        Map<String, String> metadata,
        int schemaVersion
) {
    ApprovedPlanDefinition {
        Objects.requireNonNull(id); Objects.requireNonNull(cycleId); Objects.requireNonNull(candidatePlanId);
        Objects.requireNonNull(category); Objects.requireNonNull(selectionPolicyId);
        approvedSimulationTick = PlanningValidation.tick(approvedSimulationTick);
        needAllocations = List.copyOf(needAllocations);
        acceptedCapacityClaims = List.copyOf(acceptedCapacityClaims);
        Objects.requireNonNull(approvedAction); Objects.requireNonNull(disposition);
        if (initialSubmissionState != PlanningSubmissionStatus.PENDING) {
            throw new IllegalArgumentException("Approved Plan begins pending");
        }
        metadata = PlanningValidation.metadata(metadata); schemaVersion = PlanningValidation.schema(schemaVersion);
    }
}

record PlanningFailure(PlanningFailureCode code, String message, Optional<String> reference) {
    PlanningFailure {
        Objects.requireNonNull(code); message = PlanningValidation.text(message, "Planning failure");
        reference = Objects.requireNonNull(reference, "reference");
    }
}

record ApprovedPlanSubmissionRuntime(
        ApprovedPlanId approvedPlanId,
        PlanningSubmissionStatus status,
        long lastUpdatedSimulationTick,
        int attemptCount,
        PlanningSubmissionAdapterId adapterId,
        Optional<String> targetPlanReference,
        Optional<SimulationWorkId> schedulerWorkReference,
        Optional<PlanningFailure> failure,
        long revision
) {
    ApprovedPlanSubmissionRuntime {
        Objects.requireNonNull(approvedPlanId); Objects.requireNonNull(status);
        lastUpdatedSimulationTick = PlanningValidation.tick(lastUpdatedSimulationTick);
        if (attemptCount < 0 || revision < 0) throw new IllegalArgumentException("Invalid submission runtime");
        Objects.requireNonNull(adapterId); Objects.requireNonNull(targetPlanReference);
        Objects.requireNonNull(schedulerWorkReference); Objects.requireNonNull(failure);
    }
    static ApprovedPlanSubmissionRuntime pending(ApprovedPlanId id, PlanningSubmissionAdapterId adapter, long tick) {
        return new ApprovedPlanSubmissionRuntime(id, PlanningSubmissionStatus.PENDING, tick, 0, adapter,
                Optional.empty(), Optional.empty(), Optional.empty(), 0);
    }
}

record NeedResolutionRuntime(NeedId needId, NeedResolutionStatus status, GoodQuantity originalQuantity,
                             GoodQuantity previouslyCovered, GoodQuantity currentCycleAllocated,
                             GoodQuantity unresolved, List<CandidatePlanId> candidates,
                             List<ApprovedPlanId> approvedPlans, List<ConstraintId> blockingConstraints,
                             long lastUpdatedSimulationTick, Optional<PlanningFailure> failure, long revision) {
    NeedResolutionRuntime {
        Objects.requireNonNull(needId); Objects.requireNonNull(status); Objects.requireNonNull(originalQuantity);
        Objects.requireNonNull(previouslyCovered); Objects.requireNonNull(currentCycleAllocated);
        Objects.requireNonNull(unresolved); candidates = List.copyOf(candidates);
        approvedPlans = List.copyOf(approvedPlans); blockingConstraints = List.copyOf(blockingConstraints);
        PlanningValidation.tick(lastUpdatedSimulationTick); Objects.requireNonNull(failure);
        if (revision < 0) throw new IllegalArgumentException("Need resolution revision must not be negative");
    }
}

record PlanningCycleReport(PlanningCycleId cycleId, long simulationTick, PlanningPolicyId policyId,
                           PlanningCycleStatus status, int observations, int needs, int constraints,
                           int opportunities, int candidates, int approvals, int submissions,
                           int unresolvedNeeds, boolean truncated, PlanningExecutionBudget budget,
                           long providerWorkUnits, long totalWorkUnits, List<PlanningFailure> failures) {
    PlanningCycleReport {
        Objects.requireNonNull(cycleId); PlanningValidation.tick(simulationTick); Objects.requireNonNull(policyId);
        Objects.requireNonNull(status); Objects.requireNonNull(budget);
        if (observations < 0 || needs < 0 || constraints < 0 || opportunities < 0 || candidates < 0
                || approvals < 0 || submissions < 0 || unresolvedNeeds < 0
                || providerWorkUnits < 0 || totalWorkUnits < providerWorkUnits) {
            throw new IllegalArgumentException("Planning report counts are invalid");
        }
        failures = List.copyOf(failures);
    }
}

record PlanningCycleSnapshot(
        PlanningCycleId id,
        long simulationTick,
        PlanningPolicyId policyId,
        PlanningCycleStatus status,
        List<ObservationDefinition> observations,
        List<NeedDefinition> needs,
        List<ConstraintDefinition> constraints,
        List<OpportunityDefinition> opportunities,
        List<CandidatePlanDefinition> candidates,
        List<ApprovedPlanDefinition> approvedPlans,
        List<NeedResolutionRuntime> needRuntimes,
        List<ApprovedPlanSubmissionRuntime> submissionRuntimes,
        PlanningCycleReport report,
        long revision,
        int schemaVersion
) {
    PlanningCycleSnapshot {
        Objects.requireNonNull(id); simulationTick = PlanningValidation.tick(simulationTick);
        Objects.requireNonNull(policyId); Objects.requireNonNull(status);
        observations = observations.stream().sorted().toList();
        needs = needs.stream().sorted(PlanningArtifacts.NEED_ORDER).toList();
        constraints = List.copyOf(constraints); opportunities = List.copyOf(opportunities);
        candidates = candidates.stream().sorted(PlanningArtifacts.CANDIDATE_ORDER).toList();
        approvedPlans = List.copyOf(approvedPlans); needRuntimes = List.copyOf(needRuntimes);
        submissionRuntimes = List.copyOf(submissionRuntimes); Objects.requireNonNull(report);
        if (revision < 0) throw new IllegalArgumentException("Planning cycle revision must not be negative");
        schemaVersion = PlanningValidation.schema(schemaVersion);
    }
}
