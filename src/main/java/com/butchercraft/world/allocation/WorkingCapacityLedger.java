package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

public final class WorkingCapacityLedger {
    private final Map<CapacityId, EntryState> entries;
    private final Map<ResourceId, AllocationSetId> exclusiveOwners;

    private WorkingCapacityLedger(
            Map<CapacityId, EntryState> entries,
            Map<ResourceId, AllocationSetId> exclusiveOwners
    ) {
        this.entries = entries;
        this.exclusiveOwners = exclusiveOwners;
    }

    public static WorkingCapacityLedger from(AllocationCycleInput input) {
        AllocationCycleInput source = AllocationValidation.required(input, "input");
        return fromObservations(
                source.resources(),
                source.capacities(),
                source.activeCommitments()
        );
    }

    static WorkingCapacityLedger fromObservations(
            Collection<ObservedResourceSnapshot> observedResources,
            Collection<ObservedCapacitySnapshot> observedCapacities,
            Collection<AllocationCommitmentDefinition> activeCommitments
    ) {
        Map<ResourceId, ObservedResourceSnapshot> resources = new TreeMap<>();
        for (ObservedResourceSnapshot resource : AllocationValidation.required(
                observedResources,
                "observedResources"
        )) {
            if (resources.putIfAbsent(resource.resourceId(), resource) != null) {
                throw failure(
                        AllocationCycleFailureCode.DUPLICATE_RESOURCE,
                        resource.resourceId().value(),
                        "Duplicate Resource observation"
                );
            }
        }
        Map<CapacityId, EntryState> entries = new TreeMap<>();
        for (ObservedCapacitySnapshot capacity : AllocationValidation.required(
                observedCapacities,
                "observedCapacities"
        )) {
            ObservedResourceSnapshot resource = resources.get(capacity.resourceId());
            if (resource == null) {
                throw failure(
                        AllocationCycleFailureCode.UNKNOWN_RESOURCE,
                        capacity.capacityId().value(),
                        "Capacity references an unobserved Resource"
                );
            }
            if (entries.putIfAbsent(
                    capacity.capacityId(),
                    EntryState.observed(resource, capacity)
            ) != null) {
                throw failure(
                        AllocationCycleFailureCode.DUPLICATE_CAPACITY,
                        capacity.capacityId().value(),
                        "Duplicate Capacity observation"
                );
            }
        }
        Map<ResourceId, AllocationSetId> exclusiveOwners = new TreeMap<>();
        WorkingCapacityLedger ledger = new WorkingCapacityLedger(
                entries,
                exclusiveOwners
        );
        List<AllocationCommitmentDefinition> commitments =
                AllocationValidation.required(
                        activeCommitments,
                        "activeCommitments"
                ).stream().sorted().toList();
        for (AllocationCommitmentDefinition commitment : commitments) {
            ledger.applyExisting(commitment);
        }
        ledger.validateInvariants();
        return ledger;
    }

    public List<AllocationLedgerEntryView> entries() {
        return entries.values().stream()
                .map(EntryState::view)
                .sorted()
                .toList();
    }

    public String digest() {
        return digestEntries(entries());
    }

    static String digestEntries(List<AllocationLedgerEntryView> entries) {
        AllocationCanonicalDigest digest =
                AllocationCanonicalDigest.create("butchercraft:allocation_working_ledger_v1");
        for (AllocationLedgerEntryView entry : AllocationValidation.required(
                entries,
                "entries"
        ).stream().sorted().toList()) {
            digest.add(entry.capacityId().value())
                    .add(entry.capacityKey().resourceId().value())
                    .add(entry.capacityKey().capacityTypeId().value())
                    .add(entry.capacityKey().capacityUnitId().value())
                    .add(entry.resourceCategory().value())
                    .add(entry.availability().serializedName())
                    .add(entry.exclusivityMode().serializedName())
                    .add(entry.observedQuantity().canonicalAmount())
                    .add(entry.existingCommittedQuantity().canonicalAmount())
                    .add(entry.proposedCommittedQuantity().canonicalAmount())
                    .add(entry.remainingQuantity().canonicalAmount())
                    .add(entry.observationSimulationTick())
                    .add(entry.resourceObservationReference().canonicalKey())
                    .add(entry.capacityObservationReference().canonicalKey());
            entry.existingCommitmentIds().forEach(id -> digest.add(id.value()));
            entry.proposedCommitmentIds().forEach(id -> digest.add(id.value()));
            entry.consumingSetIds().forEach(id -> digest.add(id.value()));
        }
        return digest.finish();
    }

    Branch branch() {
        return new Branch(this);
    }

    private void applyExisting(AllocationCommitmentDefinition commitment) {
        EntryState entry = entries.get(commitment.capacityId());
        if (entry == null) {
            throw failure(
                    AllocationCycleFailureCode.UNKNOWN_CAPACITY,
                    commitment.id().value(),
                    "Existing Commitment references an unobserved Capacity"
            );
        }
        if (!entry.key.resourceId().equals(commitment.resourceId())
                || !entry.key.capacityUnitId().equals(commitment.capacityUnitId())) {
            throw failure(
                    AllocationCycleFailureCode.INVALID_ACTIVE_COMMITMENT,
                    commitment.id().value(),
                    "Existing Commitment does not match its observed Capacity"
            );
        }
        if (entry.existingCommitmentIds.contains(commitment.id())) {
            throw failure(
                    AllocationCycleFailureCode.DUPLICATE_COMMITMENT,
                    commitment.id().value(),
                    "Existing Commitment would be subtracted more than once"
            );
        }
        if (entry.exclusivityMode == ResourceExclusivityMode.EXCLUSIVE) {
            AllocationSetId previous = exclusiveOwners.putIfAbsent(
                    commitment.resourceId(),
                    commitment.allocationSetId()
            );
            if (previous != null) {
                throw failure(
                        AllocationCycleFailureCode.EXCLUSIVE_CONFLICT,
                        commitment.resourceId().value(),
                        "Exclusive Resource has more than one active Commitment"
                );
            }
        }
        try {
            entry.remaining = entry.remaining.subtract(
                    commitment.committedQuantity()
            );
        } catch (AllocationValidationException exception) {
            throw failure(
                    AllocationCycleFailureCode.CAPACITY_UNDERFLOW,
                    commitment.id().value(),
                    "Existing Commitments exceed observed Capacity"
            );
        }
        entry.existingCommitted = entry.existingCommitted.add(
                commitment.committedQuantity()
        );
        entry.existingCommitmentIds.add(commitment.id());
        entry.consumingSetIds.add(commitment.allocationSetId());
    }

    private void merge(Branch branch) {
        if (branch.parent != this || !digest().equals(branch.parentDigest)) {
            throw failure(
                    AllocationCycleFailureCode.STALE_RUNTIME_STATE,
                    "working_ledger",
                    "Working Capacity ledger changed after branch creation"
            );
        }
        entries.clear();
        branch.entries.forEach((id, entry) -> entries.put(id, entry.copy()));
        exclusiveOwners.clear();
        exclusiveOwners.putAll(branch.exclusiveOwners);
        validateInvariants();
        branch.merged = true;
    }

    private void validateInvariants() {
        for (EntryState entry : entries.values()) {
            entry.view();
            if (entry.exclusivityMode == ResourceExclusivityMode.EXCLUSIVE
                    && entry.consumingSetIds.size() > 1) {
                throw failure(
                        AllocationCycleFailureCode.EXCLUSIVE_CONFLICT,
                        entry.key.resourceId().value(),
                        "Exclusive Resource is committed to multiple AllocationSets"
                );
            }
        }
    }

    static final class Branch {
        private final WorkingCapacityLedger parent;
        private final String parentDigest;
        private final Map<CapacityId, EntryState> entries;
        private final Map<ResourceId, AllocationSetId> exclusiveOwners;
        private boolean merged;

        private Branch(WorkingCapacityLedger parent) {
            this.parent = parent;
            parentDigest = parent.digest();
            entries = new TreeMap<>();
            parent.entries.forEach((id, entry) -> entries.put(id, entry.copy()));
            exclusiveOwners = new TreeMap<>(parent.exclusiveOwners);
        }

        LedgerReservationResult reserve(
                AllocationCycleId cycleId,
                AllocationSetId setId,
                RequirementDefinition requirement
        ) {
            if (merged) {
                throw failure(
                        AllocationCycleFailureCode.INVALID_RESULT,
                        setId.value(),
                        "Merged ledger branch cannot be reused"
                );
            }
            List<EntryState> categoryAndType = entries.values().stream()
                    .filter(entry -> entry.resourceCategory.equals(
                            requirement.resourceCategory()
                    ))
                    .filter(entry -> entry.key.capacityTypeId().equals(
                            requirement.capacityTypeId()
                    ))
                    .filter(entry -> requirement.exactResourceId()
                            .map(entry.key.resourceId()::equals)
                            .orElse(true))
                    .sorted(EntryState.ORDER)
                    .toList();
            List<EntryState> compatible = categoryAndType.stream()
                    .filter(entry -> entry.key.capacityUnitId().equals(
                            requirement.capacityUnitId()
                    ))
                    .toList();
            if (compatible.isEmpty() && !categoryAndType.isEmpty()) {
                return rejected(
                        AllocationCycleFailureCode.INVALID_CAPACITY_UNIT,
                        requirement,
                        "Compatible Capacity exists only in a different unit",
                        Optional.empty(),
                        List.of()
                );
            }
            for (EntryState entry : compatible) {
                if (entry.availability != ResourceAvailability.AVAILABLE) {
                    continue;
                }
                boolean exclusiveBlocked =
                        entry.exclusivityMode == ResourceExclusivityMode.EXCLUSIVE
                                && exclusiveOwners.containsKey(
                                entry.key.resourceId()
                        );
                if (!exclusiveBlocked
                        && entry.remaining.compareAmount(
                        requirement.requiredQuantity()
                ) >= 0) {
                    entry.remaining = entry.remaining.subtract(
                            requirement.requiredQuantity()
                    );
                    entry.proposedCommitted = entry.proposedCommitted.add(
                            requirement.requiredQuantity()
                    );
                    AllocationCommitmentId proposedCommitmentId =
                            AllocationIds.commitmentId(
                                    cycleId,
                                    setId,
                                    requirement.id(),
                                    entry.key.resourceId(),
                                    entry.capacityId,
                                    AllocationSchema.CURRENT_VERSION
                            );
                    entry.proposedCommitmentIds.add(proposedCommitmentId);
                    entry.consumingSetIds.add(setId);
                    if (entry.exclusivityMode
                            == ResourceExclusivityMode.EXCLUSIVE) {
                        exclusiveOwners.put(entry.key.resourceId(), setId);
                    }
                    return LedgerReservationResult.accepted(entry.view());
                }
            }
            return scarcity(setId, requirement, compatible);
        }

        List<AllocationLedgerEntryView> entries() {
            return entries.values().stream()
                    .map(EntryState::view)
                    .sorted()
                    .toList();
        }

        String digest() {
            WorkingCapacityLedger detached = new WorkingCapacityLedger(
                    copyEntries(entries),
                    new TreeMap<>(exclusiveOwners)
            );
            return detached.digest();
        }

        void merge() {
            parent.merge(this);
        }

        private LedgerReservationResult scarcity(
                AllocationSetId setId,
                RequirementDefinition requirement,
                List<EntryState> compatible
        ) {
            if (compatible.isEmpty()) {
                return rejected(
                        AllocationCycleFailureCode.CAPACITY_UNAVAILABLE,
                        requirement,
                        "No compatible observed Capacity is available",
                        Optional.empty(),
                        List.of()
                );
            }
            EntryState evidence = compatible.getFirst();
            AllocationQuantity available =
                    evidence.availability == ResourceAvailability.AVAILABLE
                            ? evidence.remaining
                            : AllocationQuantity.zero(
                            requirement.capacityUnitId()
                    );
            AllocationQuantity shortfall = requirement.requiredQuantity()
                    .subtract(available.compareAmount(
                            requirement.requiredQuantity()
                    ) > 0
                            ? requirement.requiredQuantity()
                            : available);
            if (shortfall.isZero()) {
                shortfall = requirement.requiredQuantity();
            }
            List<AllocationSetId> blockers =
                    new ArrayList<>(evidence.consumingSetIds);
            Optional<AllocationConflictRecord> conflict = blockers.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new AllocationConflictRecord(
                    evidence.exclusivityMode == ResourceExclusivityMode.EXCLUSIVE
                            ? AllocationConflictType.EXCLUSIVITY
                            : AllocationConflictType.CAPACITY,
                    evidence.key,
                    blockers,
                    List.of(setId),
                    shortfall,
                    AllocationMetadata.empty()
            ));
            AllocationCycleFailureCode code =
                    evidence.exclusivityMode == ResourceExclusivityMode.EXCLUSIVE
                            && exclusiveOwners.containsKey(
                            evidence.key.resourceId()
                    )
                            ? AllocationCycleFailureCode.EXCLUSIVE_CONFLICT
                            : AllocationCycleFailureCode.CAPACITY_UNAVAILABLE;
            return rejected(
                    code,
                    requirement,
                    code == AllocationCycleFailureCode.EXCLUSIVE_CONFLICT
                            ? "Exclusive Resource is already committed"
                            : "Observed Capacity cannot completely satisfy the Requirement",
                    conflict,
                    blockers
            );
        }

        private static LedgerReservationResult rejected(
                AllocationCycleFailureCode code,
                RequirementDefinition requirement,
                String message,
                Optional<AllocationConflictRecord> conflict,
                List<AllocationSetId> blockers
        ) {
            return LedgerReservationResult.rejected(
                    new AllocationCycleFailure(
                            code,
                            AllocationCycleFailureScope.REQUIREMENT,
                            requirement.id().value(),
                            message
                    ),
                    conflict,
                    blockers
            );
        }
    }

    private static Map<CapacityId, EntryState> copyEntries(
            Map<CapacityId, EntryState> source
    ) {
        Map<CapacityId, EntryState> copy = new TreeMap<>();
        source.forEach((id, entry) -> copy.put(id, entry.copy()));
        return copy;
    }

    private static AllocationCycleValidationException failure(
            AllocationCycleFailureCode code,
            String subject,
            String message
    ) {
        return AllocationCycleValidation.failure(
                code,
                AllocationCycleFailureScope.CYCLE,
                subject,
                message
        );
    }

    private static final class EntryState {
        private static final Comparator<EntryState> ORDER =
                Comparator.comparing((EntryState entry) -> entry.key)
                        .thenComparing(entry -> entry.capacityId);

        private final CapacityId capacityId;
        private final CapacityKey key;
        private final ResourceCategory resourceCategory;
        private final ResourceAvailability availability;
        private final ResourceExclusivityMode exclusivityMode;
        private final AllocationQuantity observed;
        private AllocationQuantity existingCommitted;
        private AllocationQuantity proposedCommitted;
        private AllocationQuantity remaining;
        private final long observationTick;
        private final ExternalReference resourceObservationReference;
        private final ExternalReference capacityObservationReference;
        private final TreeSet<AllocationCommitmentId> existingCommitmentIds;
        private final TreeSet<AllocationCommitmentId> proposedCommitmentIds;
        private final TreeSet<AllocationSetId> consumingSetIds;

        private EntryState(
                CapacityId capacityId,
                CapacityKey key,
                ResourceCategory resourceCategory,
                ResourceAvailability availability,
                ResourceExclusivityMode exclusivityMode,
                AllocationQuantity observed,
                AllocationQuantity existingCommitted,
                AllocationQuantity proposedCommitted,
                AllocationQuantity remaining,
                long observationTick,
                ExternalReference resourceObservationReference,
                ExternalReference capacityObservationReference,
                Collection<AllocationCommitmentId> existingCommitmentIds,
                Collection<AllocationCommitmentId> proposedCommitmentIds,
                Collection<AllocationSetId> consumingSetIds
        ) {
            this.capacityId = capacityId;
            this.key = key;
            this.resourceCategory = resourceCategory;
            this.availability = availability;
            this.exclusivityMode = exclusivityMode;
            this.observed = observed;
            this.existingCommitted = existingCommitted;
            this.proposedCommitted = proposedCommitted;
            this.remaining = remaining;
            this.observationTick = observationTick;
            this.resourceObservationReference = resourceObservationReference;
            this.capacityObservationReference = capacityObservationReference;
            this.existingCommitmentIds = new TreeSet<>(existingCommitmentIds);
            this.proposedCommitmentIds = new TreeSet<>(proposedCommitmentIds);
            this.consumingSetIds = new TreeSet<>(consumingSetIds);
        }

        static EntryState observed(
                ObservedResourceSnapshot resource,
                ObservedCapacitySnapshot capacity
        ) {
            return new EntryState(
                    capacity.capacityId(),
                    capacity.capacityKey(),
                    resource.resourceCategory(),
                    resource.availability(),
                    resource.exclusivityMode(),
                    capacity.observedAmount(),
                    AllocationQuantity.zero(capacity.capacityUnitId()),
                    AllocationQuantity.zero(capacity.capacityUnitId()),
                    capacity.observedAmount(),
                    capacity.observationSimulationTick(),
                    resource.authoritativeExternalReference(),
                    capacity.authoritativeExternalReference(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        EntryState copy() {
            return new EntryState(
                    capacityId,
                    key,
                    resourceCategory,
                    availability,
                    exclusivityMode,
                    observed,
                    existingCommitted,
                    proposedCommitted,
                    remaining,
                    observationTick,
                    resourceObservationReference,
                    capacityObservationReference,
                    existingCommitmentIds,
                    proposedCommitmentIds,
                    consumingSetIds
            );
        }

        AllocationLedgerEntryView view() {
            return new AllocationLedgerEntryView(
                    capacityId,
                    key,
                    resourceCategory,
                    availability,
                    exclusivityMode,
                    observed,
                    existingCommitted,
                    proposedCommitted,
                    remaining,
                    observationTick,
                    resourceObservationReference,
                    capacityObservationReference,
                    List.copyOf(existingCommitmentIds),
                    List.copyOf(proposedCommitmentIds),
                    List.copyOf(consumingSetIds)
            );
        }
    }
}
