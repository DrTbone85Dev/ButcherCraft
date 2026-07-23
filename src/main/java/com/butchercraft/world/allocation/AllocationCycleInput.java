package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

public final class AllocationCycleInput {
    private static final Comparator<ObservedResourceSnapshot> RESOURCE_ORDER =
            Comparator.comparing(ObservedResourceSnapshot::resourceId);
    private static final Comparator<ObservedCapacitySnapshot> CAPACITY_ORDER =
            Comparator.comparing(ObservedCapacitySnapshot::capacityKey)
                    .thenComparing(ObservedCapacitySnapshot::capacityId);

    private final AllocationCycleContext context;
    private final List<ObservedResourceSnapshot> resources;
    private final List<ObservedCapacitySnapshot> capacities;
    private final AllocationRegistry definitions;
    private final AllocationRuntimeRegistry runtimes;
    private final List<AllocationSetId> candidateSetIds;
    private final Map<ResourceId, ObservedResourceSnapshot> resourceById;
    private final Map<CapacityId, ObservedCapacitySnapshot> capacityById;
    private final Map<CapacityKey, ObservedCapacitySnapshot> capacityByKey;

    private AllocationCycleInput(
            AllocationCycleContext context,
            Collection<ObservedResourceSnapshot> resources,
            Collection<ObservedCapacitySnapshot> capacities,
            Collection<RequirementDefinition> requirements,
            Collection<AllocationRequestDefinition> requests,
            Collection<AllocationSetDefinition> sets,
            Collection<AllocationCommitmentDefinition> commitments,
            Collection<AllocationRuntimeView> runtimeViews,
            Collection<AllocationSetId> candidateSetIds
    ) {
        this.context = AllocationValidation.required(context, "context");
        this.resources = AllocationCycleValidation.canonical(
                resources,
                ObservedResourceSnapshot::resourceId,
                RESOURCE_ORDER,
                AllocationSchema.MAXIMUM_CYCLE_RESOURCES,
                AllocationCycleFailureCode.DUPLICATE_RESOURCE,
                "Resource snapshots"
        );
        this.capacities = AllocationCycleValidation.canonical(
                capacities,
                ObservedCapacitySnapshot::capacityId,
                CAPACITY_ORDER,
                AllocationSchema.MAXIMUM_CYCLE_CAPACITIES,
                AllocationCycleFailureCode.DUPLICATE_CAPACITY,
                "Capacity snapshots"
        );
        List<RequirementDefinition> canonicalRequirements =
                AllocationCycleValidation.canonical(
                        requirements,
                        RequirementDefinition::id,
                        Comparator.naturalOrder(),
                        AllocationSchema.MAXIMUM_CYCLE_REQUIREMENTS,
                        AllocationCycleFailureCode.DUPLICATE_REQUIREMENT,
                        "Requirements"
                );
        List<AllocationRequestDefinition> canonicalRequests =
                AllocationCycleValidation.canonical(
                        requests,
                        AllocationRequestDefinition::id,
                        Comparator.comparing(AllocationRequestDefinition::id),
                        AllocationSchema.MAXIMUM_CYCLE_REQUESTS,
                        AllocationCycleFailureCode.DUPLICATE_REQUEST,
                        "Allocation Requests"
                );
        List<AllocationSetDefinition> canonicalSets =
                AllocationCycleValidation.canonical(
                        sets,
                        AllocationSetDefinition::id,
                        Comparator.comparing(AllocationSetDefinition::id),
                        AllocationSchema.MAXIMUM_CYCLE_SETS,
                        AllocationCycleFailureCode.DUPLICATE_ALLOCATION_SET,
                        "AllocationSets"
                );
        List<AllocationCommitmentDefinition> canonicalCommitments =
                AllocationCycleValidation.canonical(
                        commitments,
                        AllocationCommitmentDefinition::id,
                        Comparator.comparing(AllocationCommitmentDefinition::id),
                        AllocationSchema.MAXIMUM_CYCLE_COMMITMENTS,
                        AllocationCycleFailureCode.DUPLICATE_COMMITMENT,
                        "Allocation Commitments"
                );
        this.candidateSetIds = AllocationCycleValidation.canonical(
                candidateSetIds,
                id -> id,
                Comparator.naturalOrder(),
                AllocationSchema.MAXIMUM_CYCLE_CANDIDATE_SETS,
                AllocationCycleFailureCode.DUPLICATE_ALLOCATION_SET,
                "candidate AllocationSets"
        );

        definitions = buildDefinitions(
                canonicalRequirements,
                canonicalRequests,
                canonicalSets,
                canonicalCommitments
        );
        runtimes = buildRuntimes(runtimeViews);
        resourceById = index(this.resources, ObservedResourceSnapshot::resourceId);
        capacityById = index(this.capacities, ObservedCapacitySnapshot::capacityId);
        capacityByKey = capacityKeyIndex(this.capacities);
        validateEnvelope();
    }

    public static AllocationCycleInput of(
            AllocationCycleContext context,
            Collection<ObservedResourceSnapshot> resources,
            Collection<ObservedCapacitySnapshot> capacities,
            Collection<RequirementDefinition> requirements,
            Collection<AllocationRequestDefinition> requests,
            Collection<AllocationSetDefinition> sets,
            Collection<AllocationCommitmentDefinition> commitments,
            Collection<AllocationRuntimeView> runtimeViews,
            Collection<AllocationSetId> candidateSetIds
    ) {
        try {
            return new AllocationCycleInput(
                    context,
                    resources,
                    capacities,
                    requirements,
                    requests,
                    sets,
                    commitments,
                    runtimeViews,
                    candidateSetIds
            );
        } catch (AllocationCycleValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw AllocationCycleValidation.structural(exception);
        }
    }

    public static AllocationCycleInput fromRegistries(
            AllocationCycleContext context,
            Collection<ObservedResourceSnapshot> resources,
            Collection<ObservedCapacitySnapshot> capacities,
            AllocationRegistry definitions,
            AllocationRuntimeRegistry runtimes,
            Collection<AllocationSetId> candidateSetIds
    ) {
        AllocationRegistry registry = AllocationValidation.required(
                definitions,
                "definitions"
        );
        AllocationRuntimeRegistry runtimeRegistry = AllocationValidation.required(
                runtimes,
                "runtimes"
        );
        return of(
                context,
                resources,
                capacities,
                registry.requirements(),
                registry.requests(),
                registry.sets(),
                registry.commitments(),
                runtimeRegistry.views(),
                candidateSetIds
        );
    }

    public static AllocationCycleOperationResult<AllocationCycleInput> validate(
            AllocationCycleContext context,
            Collection<ObservedResourceSnapshot> resources,
            Collection<ObservedCapacitySnapshot> capacities,
            Collection<RequirementDefinition> requirements,
            Collection<AllocationRequestDefinition> requests,
            Collection<AllocationSetDefinition> sets,
            Collection<AllocationCommitmentDefinition> commitments,
            Collection<AllocationRuntimeView> runtimeViews,
            Collection<AllocationSetId> candidateSetIds
    ) {
        try {
            return AllocationCycleOperationResult.accepted(of(
                    context,
                    resources,
                    capacities,
                    requirements,
                    requests,
                    sets,
                    commitments,
                    runtimeViews,
                    candidateSetIds
            ));
        } catch (AllocationCycleValidationException exception) {
            return AllocationCycleOperationResult.rejected(exception.failures());
        }
    }

    public AllocationCycleContext context() {
        return context;
    }

    public List<ObservedResourceSnapshot> resources() {
        return resources;
    }

    public List<ObservedCapacitySnapshot> capacities() {
        return capacities;
    }

    public AllocationRegistry definitions() {
        return definitions;
    }

    public AllocationRuntimeRegistry runtimes() {
        return runtimes;
    }

    public List<AllocationSetId> candidateSetIds() {
        return candidateSetIds;
    }

    public List<AllocationSetDefinition> candidateSets() {
        return candidateSetIds.stream()
                .map(id -> definitions.findSet(id).orElseThrow())
                .toList();
    }

    public Optional<ObservedResourceSnapshot> findResource(ResourceId id) {
        return Optional.ofNullable(resourceById.get(
                AllocationValidation.required(id, "id")
        ));
    }

    public Optional<ObservedCapacitySnapshot> findCapacity(CapacityId id) {
        return Optional.ofNullable(capacityById.get(
                AllocationValidation.required(id, "id")
        ));
    }

    public Optional<ObservedCapacitySnapshot> findCapacity(CapacityKey key) {
        return Optional.ofNullable(capacityByKey.get(
                AllocationValidation.required(key, "key")
        ));
    }

    public List<AllocationCommitmentDefinition> activeCommitments() {
        return runtimes.views().stream()
                .filter(view -> view.status() == AllocationRuntimeStatus.ALLOCATED
                        || view.status() == AllocationRuntimeStatus.ACTIVE)
                .flatMap(view -> view.commitmentIds().stream())
                .map(id -> definitions.findCommitment(id).orElseThrow())
                .sorted(Comparator.comparing(AllocationCommitmentDefinition::id))
                .toList();
    }

    public int schemaVersion() {
        return context.schemaVersion();
    }

    private void validateEnvelope() {
        validateObservationTicks();
        validateCapacityReferences();
        validateDefinitionRuntimeGraph();
        validateCandidates();
        validateActiveCommitments();
    }

    private void validateObservationTicks() {
        for (ObservedResourceSnapshot resource : resources) {
            if (resource.observationSimulationTick() != context.simulationTick()) {
                invalid(
                        AllocationCycleFailureCode.INVALID_OBSERVATION_TICK,
                        resource.resourceId().value(),
                        "Resource observation tick does not match the Allocation Cycle"
                );
            }
        }
        for (ObservedCapacitySnapshot capacity : capacities) {
            if (capacity.observationSimulationTick() != context.simulationTick()) {
                invalid(
                        AllocationCycleFailureCode.INVALID_OBSERVATION_TICK,
                        capacity.capacityId().value(),
                        "Capacity observation tick does not match the Allocation Cycle"
                );
            }
        }
    }

    private void validateCapacityReferences() {
        for (ObservedCapacitySnapshot capacity : capacities) {
            ObservedResourceSnapshot resource = resourceById.get(capacity.resourceId());
            if (resource == null) {
                invalid(
                        AllocationCycleFailureCode.UNKNOWN_RESOURCE,
                        capacity.capacityId().value(),
                        "Capacity snapshot references an unknown Resource"
                );
            }
            if (resource.exclusivityMode() == ResourceExclusivityMode.EXCLUSIVE
                    && !capacity.observedAmount().equals(
                    AllocationQuantity.of(1L, capacity.capacityUnitId())
            )) {
                invalid(
                        AllocationCycleFailureCode.INVALID_EXCLUSIVITY,
                        capacity.capacityId().value(),
                        "Exclusive Resource Capacity must expose exactly one schema-1 unit"
                );
            }
        }
    }

    private void validateDefinitionRuntimeGraph() {
        for (AllocationSetDefinition set : definitions.sets()) {
            if (!runtimes.contains(set.id())) {
                invalid(
                        AllocationCycleFailureCode.UNKNOWN_ALLOCATION_SET,
                        set.id().value(),
                        "AllocationSet has no runtime snapshot"
                );
            }
        }
        for (AllocationRuntimeView runtime : runtimes.views()) {
            if (definitions.findSet(runtime.allocationSetId()).isEmpty()) {
                invalid(
                        AllocationCycleFailureCode.UNKNOWN_ALLOCATION_SET,
                        runtime.allocationSetId().value(),
                        "Runtime snapshot references an unknown AllocationSet"
                );
            }
            if (runtime.lastUpdatedSimulationTick() > context.simulationTick()) {
                invalid(
                        AllocationCycleFailureCode.INVALID_SIMULATION_TICK,
                        runtime.allocationSetId().value(),
                        "Runtime snapshot is newer than the Allocation Cycle"
                );
            }
        }
    }

    private void validateCandidates() {
        TreeSet<ExternalReference> planningCycles = new TreeSet<>();
        for (AllocationSetId id : candidateSetIds) {
            AllocationSetDefinition set = definitions.findSet(id).orElse(null);
            if (set == null) {
                invalid(
                        AllocationCycleFailureCode.UNKNOWN_ALLOCATION_SET,
                        id.value(),
                        "Candidate AllocationSet is not registered"
                );
            }
            AllocationRuntimeView runtime = runtimes.find(id).orElseThrow();
            if (runtime.status() != AllocationRuntimeStatus.REQUESTED
                    && runtime.status() != AllocationRuntimeStatus.WAITING) {
                invalid(
                        AllocationCycleFailureCode.SET_NOT_ELIGIBLE,
                        id.value(),
                        "Candidate AllocationSet runtime is not REQUESTED or WAITING"
                );
            }
            if (runtime.lastUpdatedSimulationTick()
                    > context.simulationTick()
                    || set.creationSimulationTick()
                    > context.simulationTick()) {
                invalid(
                        AllocationCycleFailureCode.INVALID_SIMULATION_TICK,
                        id.value(),
                        "Candidate AllocationSet state is newer than the Allocation Cycle"
                );
            }
            planningCycles.add(set.planningCycleReference());
            for (RequirementId requirementId : set.requirementIds()) {
                validateExactRequirement(
                        definitions.findRequirement(requirementId).orElseThrow()
                );
            }
        }
        if (planningCycles.size() > 1) {
            invalid(
                    AllocationCycleFailureCode.INVALID_REFERENCE,
                    context.cycleId().value(),
                    "One Allocation Cycle cannot evaluate multiple Planning Cycles"
            );
        }
    }

    private void validateActiveCommitments() {
        TreeSet<RequirementId> activeRequirements = new TreeSet<>();
        for (AllocationRuntimeView runtime : runtimes.views()) {
            boolean consumes = runtime.status() == AllocationRuntimeStatus.ALLOCATED
                    || runtime.status() == AllocationRuntimeStatus.ACTIVE;
            if (!consumes) {
                continue;
            }
            for (AllocationCommitmentId id : runtime.commitmentIds()) {
                AllocationCommitmentDefinition commitment =
                        definitions.findCommitment(id).orElse(null);
                if (commitment == null) {
                    invalid(
                            AllocationCycleFailureCode.UNKNOWN_COMMITMENT,
                            id.value(),
                            "Active runtime references an unknown Commitment"
                    );
                }
                if (commitment.expirationSimulationTick().isPresent()
                        && commitment.expirationSimulationTick().getAsLong()
                        < context.simulationTick()) {
                    invalid(
                            AllocationCycleFailureCode.EXPIRED_ACTIVE_COMMITMENT,
                            id.value(),
                            "Expired Commitment remains attached to consuming runtime"
                    );
                }
                if (!activeRequirements.add(commitment.requirementId())) {
                    invalid(
                            AllocationCycleFailureCode.INVALID_ACTIVE_COMMITMENT,
                            commitment.requirementId().value(),
                            "Requirement has more than one active Commitment"
                    );
                }
                validateCommitmentObservation(commitment);
            }
        }
    }

    private void validateExactRequirement(RequirementDefinition requirement) {
        if (requirement.exactResourceId().isEmpty()) {
            return;
        }
        ResourceId resourceId = requirement.exactResourceId().orElseThrow();
        ObservedResourceSnapshot resource = resourceById.get(resourceId);
        if (resource == null) {
            invalid(
                    AllocationCycleFailureCode.UNKNOWN_RESOURCE,
                    requirement.id().value(),
                    "Exact Requirement references an unknown Resource snapshot"
            );
        }
        if (!resource.resourceCategory().equals(requirement.resourceCategory())) {
            invalid(
                    AllocationCycleFailureCode.INVALID_RESOURCE_CATEGORY,
                    requirement.id().value(),
                    "Exact Requirement Resource category does not match"
            );
        }
        if (!capacityByKey.containsKey(requirement.exactCapacityKey().orElseThrow())) {
            invalid(
                    AllocationCycleFailureCode.UNKNOWN_CAPACITY,
                    requirement.id().value(),
                    "Exact Requirement references an unavailable Capacity key"
            );
        }
    }

    private void validateCommitmentObservation(
            AllocationCommitmentDefinition commitment
    ) {
        RequirementDefinition requirement = definitions.findRequirement(
                commitment.requirementId()
        ).orElseThrow();
        ObservedResourceSnapshot resource = resourceById.get(commitment.resourceId());
        ObservedCapacitySnapshot capacity = capacityById.get(commitment.capacityId());
        if (resource == null) {
            invalid(
                    AllocationCycleFailureCode.UNKNOWN_RESOURCE,
                    commitment.id().value(),
                    "Active Commitment references an unobserved Resource"
            );
        }
        if (capacity == null) {
            invalid(
                    AllocationCycleFailureCode.UNKNOWN_CAPACITY,
                    commitment.id().value(),
                    "Active Commitment references an unobserved Capacity"
            );
        }
        if (!capacity.resourceId().equals(commitment.resourceId())
                || !capacity.capacityTypeId().equals(requirement.capacityTypeId())
                || !capacity.capacityUnitId().equals(commitment.capacityUnitId())
                || !resource.resourceCategory().equals(requirement.resourceCategory())
                || !commitment.committedQuantity().equals(
                requirement.requiredQuantity()
        )
                || commitment.createdSimulationTick()
                < requirement.creationSimulationTick()
                || commitment.createdSimulationTick()
                > context.simulationTick()
                || !commitment.allocationCycleId().equals(
                AllocationCycleId.forTick(commitment.createdSimulationTick())
        )) {
            invalid(
                    AllocationCycleFailureCode.INVALID_ACTIVE_COMMITMENT,
                    commitment.id().value(),
                    "Active Commitment is incompatible with current Resource and Capacity observations"
            );
        }
    }

    private static AllocationRegistry buildDefinitions(
            List<RequirementDefinition> requirements,
            List<AllocationRequestDefinition> requests,
            List<AllocationSetDefinition> sets,
            List<AllocationCommitmentDefinition> commitments
    ) {
        AllocationRegistryBuilder builder = AllocationRegistry.builder();
        requirements.forEach(builder::registerRequirement);
        requests.forEach(builder::registerRequest);
        sets.forEach(builder::registerSet);
        commitments.forEach(builder::registerCommitment);
        return builder.build();
    }

    private static AllocationRuntimeRegistry buildRuntimes(
            Collection<AllocationRuntimeView> runtimeViews
    ) {
        Collection<AllocationRuntimeView> values = AllocationValidation.required(
                runtimeViews,
                "runtimeViews"
        );
        if (values.size() > AllocationSchema.MAXIMUM_CYCLE_RUNTIMES) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.STRUCTURAL_BOUND_EXCEEDED,
                    AllocationCycleFailureScope.CYCLE,
                    "runtimeViews",
                    "Runtime snapshots exceed the Allocation schema bound"
            );
        }
        return AllocationRuntimeRegistry.of(values);
    }

    private static Map<CapacityKey, ObservedCapacitySnapshot> capacityKeyIndex(
            List<ObservedCapacitySnapshot> capacities
    ) {
        Map<CapacityKey, ObservedCapacitySnapshot> index = new TreeMap<>();
        for (ObservedCapacitySnapshot capacity : capacities) {
            if (index.putIfAbsent(capacity.capacityKey(), capacity) != null) {
                throw AllocationCycleValidation.failure(
                        AllocationCycleFailureCode.DUPLICATE_CAPACITY_KEY,
                        AllocationCycleFailureScope.CYCLE,
                        capacity.capacityKey().toString(),
                        "Duplicate observed Capacity key"
                );
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(index));
    }

    private static <I extends Comparable<? super I>, T> Map<I, T> index(
            List<T> values,
            java.util.function.Function<T, I> identity
    ) {
        Map<I, T> index = new TreeMap<>();
        for (T value : values) {
            index.put(identity.apply(value), value);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(index));
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

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AllocationCycleInput that)) {
            return false;
        }
        return context.equals(that.context)
                && resources.equals(that.resources)
                && capacities.equals(that.capacities)
                && definitions.requirements().equals(that.definitions.requirements())
                && definitions.requests().equals(that.definitions.requests())
                && definitions.sets().equals(that.definitions.sets())
                && definitions.commitments().equals(that.definitions.commitments())
                && runtimes.views().equals(that.runtimes.views())
                && candidateSetIds.equals(that.candidateSetIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                context,
                resources,
                capacities,
                definitions.requirements(),
                definitions.requests(),
                definitions.sets(),
                definitions.commitments(),
                runtimes.views(),
                candidateSetIds
        );
    }
}
