package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public final class AllocationRegistry {
    private static final AllocationRegistry EMPTY = new AllocationRegistry(
            List.of(),
            List.of(),
            List.of(),
            List.of()
    );

    private final List<RequirementDefinition> requirements;
    private final List<AllocationRequestDefinition> requests;
    private final List<AllocationSetDefinition> sets;
    private final List<AllocationCommitmentDefinition> commitments;
    private final Map<RequirementId, RequirementDefinition> requirementById;
    private final Map<AllocationRequestId, AllocationRequestDefinition> requestById;
    private final Map<AllocationSetId, AllocationSetDefinition> setById;
    private final Map<AllocationCommitmentId, AllocationCommitmentDefinition> commitmentById;
    private final Map<AllocationRequestId, List<AllocationSetDefinition>> setsByRequest;
    private final Map<ExternalReference, List<AllocationSetDefinition>> setsByWork;
    private final Map<AllocationSetId, List<AllocationCommitmentDefinition>> commitmentsBySet;
    private final Map<RequirementId, List<AllocationCommitmentDefinition>> commitmentsByRequirement;
    private final Map<ResourceId, List<AllocationCommitmentDefinition>> commitmentsByResource;

    AllocationRegistry(
            Collection<RequirementDefinition> requirements,
            Collection<AllocationRequestDefinition> requests,
            Collection<AllocationSetDefinition> sets,
            Collection<AllocationCommitmentDefinition> commitments
    ) {
        this.requirements = canonical(
                requirements,
                RequirementDefinition::id,
                "Requirement"
        );
        this.requests = canonical(
                requests,
                AllocationRequestDefinition::id,
                "AllocationRequest"
        );
        this.sets = canonical(sets, AllocationSetDefinition::id, "AllocationSet");
        this.commitments = canonical(
                commitments,
                AllocationCommitmentDefinition::id,
                "AllocationCommitment"
        );
        requirementById = index(this.requirements, RequirementDefinition::id);
        requestById = index(this.requests, AllocationRequestDefinition::id);
        setById = index(this.sets, AllocationSetDefinition::id);
        commitmentById = index(this.commitments, AllocationCommitmentDefinition::id);
        validateReferences();
        setsByRequest = groups(
                this.sets,
                set -> List.of(set.sourceRequestId())
        );
        setsByWork = groups(
                this.sets,
                set -> List.of(set.executionWorkReference())
        );
        commitmentsBySet = groups(
                this.commitments,
                commitment -> List.of(commitment.allocationSetId())
        );
        commitmentsByRequirement = groups(
                this.commitments,
                commitment -> List.of(commitment.requirementId())
        );
        commitmentsByResource = groups(
                this.commitments,
                commitment -> List.of(commitment.resourceId())
        );
    }

    public static AllocationRegistry empty() {
        return EMPTY;
    }

    public static AllocationRegistryBuilder builder() {
        return new AllocationRegistryBuilder();
    }

    public AllocationRegistryBuilder toBuilder() {
        return new AllocationRegistryBuilder().registerAll(this);
    }

    public int requirementCount() {
        return requirements.size();
    }

    public int requestCount() {
        return requests.size();
    }

    public int setCount() {
        return sets.size();
    }

    public int commitmentCount() {
        return commitments.size();
    }

    public List<RequirementDefinition> requirements() {
        return requirements;
    }

    public List<AllocationRequestDefinition> requests() {
        return requests;
    }

    public List<AllocationSetDefinition> sets() {
        return sets;
    }

    public List<AllocationCommitmentDefinition> commitments() {
        return commitments;
    }

    public Stream<RequirementDefinition> requirementStream() {
        return requirements.stream();
    }

    public Stream<AllocationRequestDefinition> requestStream() {
        return requests.stream();
    }

    public Stream<AllocationSetDefinition> setStream() {
        return sets.stream();
    }

    public Stream<AllocationCommitmentDefinition> commitmentStream() {
        return commitments.stream();
    }

    public Optional<RequirementDefinition> findRequirement(RequirementId id) {
        return Optional.ofNullable(requirementById.get(
                AllocationValidation.required(id, "id")
        ));
    }

    public Optional<AllocationRequestDefinition> findRequest(AllocationRequestId id) {
        return Optional.ofNullable(requestById.get(
                AllocationValidation.required(id, "id")
        ));
    }

    public Optional<AllocationSetDefinition> findSet(AllocationSetId id) {
        return Optional.ofNullable(setById.get(AllocationValidation.required(id, "id")));
    }

    public Optional<AllocationCommitmentDefinition> findCommitment(
            AllocationCommitmentId id
    ) {
        return Optional.ofNullable(commitmentById.get(
                AllocationValidation.required(id, "id")
        ));
    }

    public List<AllocationSetDefinition> findSetsByRequest(AllocationRequestId id) {
        return get(setsByRequest, id);
    }

    public List<AllocationSetDefinition> findSetsByExecutionWork(
            ExternalReference reference
    ) {
        return get(setsByWork, reference);
    }

    public List<AllocationCommitmentDefinition> findCommitmentsBySet(
            AllocationSetId id
    ) {
        return get(commitmentsBySet, id);
    }

    public List<AllocationCommitmentDefinition> findCommitmentsByRequirement(
            RequirementId id
    ) {
        return get(commitmentsByRequirement, id);
    }

    public List<AllocationCommitmentDefinition> findCommitmentsByResource(
            ResourceId id
    ) {
        return get(commitmentsByResource, id);
    }

    private void validateReferences() {
        List<AllocationRuntimeFailure> failures = AllocationRuntimeValidation.failures();
        for (RequirementDefinition requirement : requirements) {
            if (!setById.containsKey(requirement.allocationSetId())) {
                unknown(
                        failures,
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        requirement.id().value(),
                        "Requirement references an unknown AllocationSet"
                );
            }
        }
        for (AllocationRequestDefinition request : requests) {
            AllocationSetDefinition set = setById.get(request.allocationSetId());
            if (set == null) {
                unknown(
                        failures,
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        request.id().value(),
                        "AllocationRequest references an unknown AllocationSet"
                );
            }
            for (RequirementId id : request.requirementIds()) {
                RequirementDefinition requirement = requirementById.get(id);
                if (requirement == null) {
                    unknown(
                            failures,
                            AllocationRuntimeFailureCode.UNKNOWN_REQUIREMENT,
                            request.id().value(),
                            "AllocationRequest references unknown Requirement " + id.value()
                    );
                } else if (!requirement.allocationSetId().equals(request.allocationSetId())
                        || !requirement.executionWorkReference().equals(
                        request.executionWorkReference()
                )) {
                    unknown(
                            failures,
                            AllocationRuntimeFailureCode.UNKNOWN_REFERENCE,
                            request.id().value(),
                            "AllocationRequest Requirement association is inconsistent"
                    );
                }
            }
        }
        for (AllocationSetDefinition set : sets) {
            AllocationRequestDefinition request = requestById.get(set.sourceRequestId());
            if (request == null) {
                unknown(
                        failures,
                        AllocationRuntimeFailureCode.UNKNOWN_REQUEST,
                        set.id().value(),
                        "AllocationSet references an unknown AllocationRequest"
                );
            } else if (!request.allocationSetId().equals(set.id())
                    || !request.requirementIds().equals(set.requirementIds())
                    || !request.executionWorkReference().equals(set.executionWorkReference())) {
                unknown(
                        failures,
                        AllocationRuntimeFailureCode.UNKNOWN_REFERENCE,
                        set.id().value(),
                        "AllocationSet source Request association is inconsistent"
                );
            }
            for (RequirementId id : set.requirementIds()) {
                RequirementDefinition requirement = requirementById.get(id);
                if (requirement == null) {
                    unknown(
                            failures,
                            AllocationRuntimeFailureCode.UNKNOWN_REQUIREMENT,
                            set.id().value(),
                            "AllocationSet references unknown Requirement " + id.value()
                    );
                }
            }
        }
        for (AllocationCommitmentDefinition commitment : commitments) {
            AllocationSetDefinition set = setById.get(commitment.allocationSetId());
            RequirementDefinition requirement = requirementById.get(
                    commitment.requirementId()
            );
            if (set == null) {
                unknown(
                        failures,
                        AllocationRuntimeFailureCode.UNKNOWN_SET,
                        commitment.id().value(),
                        "AllocationCommitment references an unknown AllocationSet"
                );
            }
            if (requirement == null) {
                unknown(
                        failures,
                        AllocationRuntimeFailureCode.UNKNOWN_REQUIREMENT,
                        commitment.id().value(),
                        "AllocationCommitment references an unknown Requirement"
                );
            } else if (!requirement.allocationSetId().equals(
                    commitment.allocationSetId()
            )) {
                unknown(
                        failures,
                        AllocationRuntimeFailureCode.INVALID_COMMITMENT_ASSOCIATION,
                        commitment.id().value(),
                        "AllocationCommitment Requirement belongs to another AllocationSet"
                );
            }
        }
        AllocationRuntimeValidation.throwIfAny(failures);
    }

    private static void unknown(
            List<AllocationRuntimeFailure> failures,
            AllocationRuntimeFailureCode code,
            String subject,
            String message
    ) {
        AllocationRuntimeValidation.add(failures, code, subject, message);
    }

    private static <T, I extends Comparable<? super I>> List<T> canonical(
            Collection<T> source,
            Function<T, I> identity,
            String label
    ) {
        List<T> values = new ArrayList<>(AllocationValidation.required(source, label));
        values.forEach(value -> AllocationValidation.required(value, label));
        values.sort(Comparator.comparing(identity));
        I previous = null;
        for (T value : values) {
            I id = identity.apply(value);
            if (id.equals(previous)) {
                throw AllocationRuntimeValidation.failure(
                        AllocationRuntimeFailureCode.DUPLICATE_REGISTRATION,
                        id.toString(),
                        "Duplicate " + label + " identity"
                );
            }
            previous = id;
        }
        return List.copyOf(values);
    }

    private static <T, I> Map<I, T> index(
            List<T> source,
            Function<T, I> identity
    ) {
        Map<I, T> values = new LinkedHashMap<>();
        source.forEach(value -> values.put(identity.apply(value), value));
        return Collections.unmodifiableMap(values);
    }

    private static <T, K> Map<K, List<T>> groups(
            List<T> source,
            Function<T, Collection<K>> keys
    ) {
        Map<K, List<T>> mutable = new LinkedHashMap<>();
        for (T value : source) {
            for (K key : keys.apply(value)) {
                mutable.computeIfAbsent(key, ignored -> new ArrayList<>()).add(value);
            }
        }
        Map<K, List<T>> immutable = new LinkedHashMap<>();
        mutable.forEach((key, values) -> immutable.put(key, List.copyOf(values)));
        return Collections.unmodifiableMap(immutable);
    }

    private static <T, K> List<T> get(Map<K, List<T>> groups, K key) {
        return groups.getOrDefault(AllocationValidation.required(key, "key"), List.of());
    }
}
