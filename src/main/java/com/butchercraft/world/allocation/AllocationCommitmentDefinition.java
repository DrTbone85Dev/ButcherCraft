package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public final class AllocationCommitmentDefinition implements Comparable<AllocationCommitmentDefinition> {
    private static final Comparator<AllocationCommitmentDefinition> ORDER = Comparator
            .comparingLong(AllocationCommitmentDefinition::createdSimulationTick)
            .thenComparing(AllocationCommitmentDefinition::id);

    private final AllocationCommitmentId id;
    private final AllocationCycleId allocationCycleId;
    private final AllocationSetId allocationSetId;
    private final RequirementId requirementId;
    private final ResourceId resourceId;
    private final CapacityId capacityId;
    private final AllocationQuantity committedQuantity;
    private final CapacityUnitId capacityUnitId;
    private final long createdSimulationTick;
    private final OptionalLong expirationSimulationTick;
    private final List<ExternalReference> sourceObservationReferences;
    private final AllocationMetadata metadata;
    private final int schemaVersion;

    private AllocationCommitmentDefinition(
            AllocationCommitmentId id,
            AllocationCycleId allocationCycleId,
            AllocationSetId allocationSetId,
            RequirementId requirementId,
            ResourceId resourceId,
            CapacityId capacityId,
            AllocationQuantity committedQuantity,
            CapacityUnitId capacityUnitId,
            long createdSimulationTick,
            OptionalLong expirationSimulationTick,
            List<ExternalReference> sourceObservationReferences,
            AllocationMetadata metadata,
            int schemaVersion
    ) {
        this.id = id;
        this.allocationCycleId = allocationCycleId;
        this.allocationSetId = allocationSetId;
        this.requirementId = requirementId;
        this.resourceId = resourceId;
        this.capacityId = capacityId;
        this.committedQuantity = committedQuantity;
        this.capacityUnitId = capacityUnitId;
        this.createdSimulationTick = createdSimulationTick;
        this.expirationSimulationTick = expirationSimulationTick;
        this.sourceObservationReferences = sourceObservationReferences;
        this.metadata = metadata;
        this.schemaVersion = schemaVersion;
    }

    public static AllocationCommitmentDefinition create(
            AllocationCycleId allocationCycleId,
            RequirementDefinition requirement,
            ResourceId resourceId,
            CapacityId capacityId,
            AllocationQuantity committedQuantity,
            long createdSimulationTick,
            OptionalLong expirationSimulationTick,
            Collection<ExternalReference> sourceObservationReferences,
            AllocationMetadata metadata
    ) {
        List<AllocationValidationFailure> failures = AllocationValidation.failures();
        AllocationValidation.required(allocationCycleId, "allocationCycleId", failures);
        AllocationValidation.required(requirement, "requirement", failures);
        AllocationValidation.required(resourceId, "resourceId", failures);
        AllocationValidation.required(capacityId, "capacityId", failures);
        AllocationValidation.required(committedQuantity, "committedQuantity", failures);
        AllocationValidation.required(expirationSimulationTick, "expirationSimulationTick", failures);
        AllocationValidation.required(
                sourceObservationReferences,
                "sourceObservationReferences",
                failures
        );
        AllocationValidation.required(metadata, "metadata", failures);
        if (committedQuantity != null && !committedQuantity.isPositive()) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.ZERO_QUANTITY,
                    "committedQuantity",
                    "Allocation Commitment quantity must be positive"
            );
        }
        if (requirement != null && committedQuantity != null
                && !requirement.capacityUnitId().equals(committedQuantity.unitId())) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INCOMPATIBLE_UNIT,
                    "committedQuantity",
                    "Commitment quantity unit does not match its Requirement"
            );
        }
        if (requirement != null && resourceId != null
                && requirement.exactResourceId().isPresent()
                && !requirement.exactResourceId().get().equals(resourceId)) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INCONSISTENT_SET_ASSOCIATION,
                    "resourceId",
                    "Commitment Resource does not match the exact Requirement Resource"
            );
        }
        if (createdSimulationTick < 0L) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                    "createdSimulationTick",
                    "Commitment creation tick must not be negative"
            );
        }
        if (expirationSimulationTick != null
                && expirationSimulationTick.isPresent()
                && expirationSimulationTick.getAsLong() < createdSimulationTick) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_EXPIRATION,
                    "expirationSimulationTick",
                    "Commitment expiration cannot precede creation"
            );
        }

        List<ExternalReference> observations = new ArrayList<>();
        if (sourceObservationReferences != null) {
            for (ExternalReference reference : sourceObservationReferences) {
                if (reference == null) {
                    AllocationValidation.add(
                            failures,
                            AllocationValidationFailureCode.NULL_VALUE,
                            "sourceObservationReferences",
                            "Source observation reference is required"
                    );
                } else {
                    observations.add(reference);
                }
            }
        }
        observations.sort(Comparator.naturalOrder());
        Set<ExternalReference> unique = new HashSet<>(observations);
        if (unique.size() != observations.size()) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.DUPLICATE_OBSERVATION_REFERENCE,
                    "sourceObservationReferences",
                    "Commitment contains duplicate source observation references"
            );
        }
        if (observations.isEmpty()) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.NONCANONICAL_INPUT,
                    "sourceObservationReferences",
                    "Commitment requires at least one source observation reference"
            );
        }
        if (observations.size() > AllocationSchema.MAXIMUM_OBSERVATION_REFERENCES) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.UNSUPPORTED_SCHEMA_CONCEPT,
                    "sourceObservationReferences",
                    "Commitment exceeds the schema-1 observation reference limit"
            );
        }
        AllocationValidation.throwIfAny(failures);

        AllocationCommitmentId id = AllocationIds.commitmentId(
                allocationCycleId,
                requirement.allocationSetId(),
                requirement.id(),
                resourceId,
                capacityId,
                AllocationSchema.CURRENT_VERSION
        );
        return new AllocationCommitmentDefinition(
                id,
                allocationCycleId,
                requirement.allocationSetId(),
                requirement.id(),
                resourceId,
                capacityId,
                committedQuantity,
                committedQuantity.unitId(),
                createdSimulationTick,
                expirationSimulationTick,
                List.copyOf(observations),
                metadata,
                AllocationSchema.CURRENT_VERSION
        );
    }

    public AllocationCommitmentId id() {
        return id;
    }

    public AllocationCycleId allocationCycleId() {
        return allocationCycleId;
    }

    public AllocationSetId allocationSetId() {
        return allocationSetId;
    }

    public RequirementId requirementId() {
        return requirementId;
    }

    public ResourceId resourceId() {
        return resourceId;
    }

    public CapacityId capacityId() {
        return capacityId;
    }

    public AllocationQuantity committedQuantity() {
        return committedQuantity;
    }

    public CapacityUnitId capacityUnitId() {
        return capacityUnitId;
    }

    public long createdSimulationTick() {
        return createdSimulationTick;
    }

    public OptionalLong expirationSimulationTick() {
        return expirationSimulationTick;
    }

    public List<ExternalReference> sourceObservationReferences() {
        return sourceObservationReferences;
    }

    public AllocationMetadata metadata() {
        return metadata;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public int compareTo(AllocationCommitmentDefinition other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AllocationCommitmentDefinition that)) {
            return false;
        }
        return createdSimulationTick == that.createdSimulationTick
                && schemaVersion == that.schemaVersion
                && id.equals(that.id)
                && allocationCycleId.equals(that.allocationCycleId)
                && allocationSetId.equals(that.allocationSetId)
                && requirementId.equals(that.requirementId)
                && resourceId.equals(that.resourceId)
                && capacityId.equals(that.capacityId)
                && committedQuantity.equals(that.committedQuantity)
                && capacityUnitId.equals(that.capacityUnitId)
                && expirationSimulationTick.equals(that.expirationSimulationTick)
                && sourceObservationReferences.equals(that.sourceObservationReferences)
                && metadata.equals(that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                allocationCycleId,
                allocationSetId,
                requirementId,
                resourceId,
                capacityId,
                committedQuantity,
                capacityUnitId,
                createdSimulationTick,
                expirationSimulationTick,
                sourceObservationReferences,
                metadata,
                schemaVersion
        );
    }

    @Override
    public String toString() {
        return "AllocationCommitmentDefinition[id=" + id.value()
                + ", allocationSetId=" + allocationSetId.value()
                + ", requirementId=" + requirementId.value()
                + ", resourceId=" + resourceId.value()
                + ", capacityId=" + capacityId.value()
                + ", committedQuantity=" + committedQuantity.canonicalValue()
                + ", createdSimulationTick=" + createdSimulationTick
                + ", schemaVersion=" + schemaVersion + "]";
    }
}
