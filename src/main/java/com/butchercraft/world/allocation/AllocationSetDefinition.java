package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

public final class AllocationSetDefinition implements Comparable<AllocationSetDefinition> {
    private static final Comparator<AllocationSetDefinition> ORDER = Comparator
            .comparingLong(AllocationSetDefinition::creationSimulationTick)
            .thenComparing(AllocationSetDefinition::id);

    private final AllocationSetId id;
    private final ExternalReference executionWorkReference;
    private final AllocationRequestId sourceRequestId;
    private final List<RequirementId> requirementIds;
    private final ExternalReference planningCycleReference;
    private final long creationSimulationTick;
    private final OptionalLong expirationSimulationTick;
    private final AllocationMetadata metadata;
    private final int schemaVersion;

    private AllocationSetDefinition(
            AllocationSetId id,
            ExternalReference executionWorkReference,
            AllocationRequestId sourceRequestId,
            List<RequirementId> requirementIds,
            ExternalReference planningCycleReference,
            long creationSimulationTick,
            OptionalLong expirationSimulationTick,
            AllocationMetadata metadata,
            int schemaVersion
    ) {
        this.id = id;
        this.executionWorkReference = executionWorkReference;
        this.sourceRequestId = sourceRequestId;
        this.requirementIds = requirementIds;
        this.planningCycleReference = planningCycleReference;
        this.creationSimulationTick = creationSimulationTick;
        this.expirationSimulationTick = expirationSimulationTick;
        this.metadata = metadata;
        this.schemaVersion = schemaVersion;
    }

    public static AllocationSetDefinition create(
            AllocationSetId id,
            ExternalReference executionWorkReference,
            AllocationRequestDefinition sourceRequest,
            Collection<RequirementDefinition> requirements,
            ExternalReference planningCycleReference,
            long creationSimulationTick,
            OptionalLong expirationSimulationTick,
            AllocationMetadata metadata,
            int schemaVersion
    ) {
        List<AllocationValidationFailure> failures = AllocationValidation.failures();
        AllocationValidation.required(id, "id", failures);
        AllocationValidation.required(executionWorkReference, "executionWorkReference", failures);
        AllocationValidation.required(sourceRequest, "sourceRequest", failures);
        AllocationValidation.required(requirements, "requirements", failures);
        AllocationValidation.required(planningCycleReference, "planningCycleReference", failures);
        AllocationValidation.required(expirationSimulationTick, "expirationSimulationTick", failures);
        AllocationValidation.required(metadata, "metadata", failures);

        List<RequirementDefinition> canonical = new ArrayList<>();
        if (requirements != null) {
            for (RequirementDefinition requirement : requirements) {
                if (requirement == null) {
                    AllocationValidation.add(
                            failures,
                            AllocationValidationFailureCode.NULL_VALUE,
                            "requirements",
                            "AllocationSet Requirement is required"
                    );
                } else {
                    canonical.add(requirement);
                }
            }
        }
        canonical.sort(Comparator.naturalOrder());
        if (canonical.isEmpty()) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.EMPTY_ALLOCATION_SET,
                    "requirements",
                    "AllocationSet requires at least one Requirement"
            );
        }
        if (canonical.size() > AllocationSchema.MAXIMUM_REQUIREMENTS_PER_SET) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.UNSUPPORTED_SCHEMA_CONCEPT,
                    "requirements",
                    "AllocationSet exceeds the schema-1 Requirement limit"
            );
        }

        Set<RequirementId> requirementIds = new HashSet<>();
        Set<ResourceId> exactResources = new HashSet<>();
        Set<String> capacityKeys = new HashSet<>();
        for (RequirementDefinition requirement : canonical) {
            if (!requirementIds.add(requirement.id())) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.DUPLICATE_REQUIREMENT,
                        "requirements",
                        "Duplicate Requirement: " + requirement.id().value()
                );
            }
            if (id != null && !requirement.allocationSetId().equals(id)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_SET_ASSOCIATION,
                        "requirements",
                        "Requirement " + requirement.id().value() + " belongs to another AllocationSet"
                );
            }
            if (executionWorkReference != null
                    && !requirement.executionWorkReference().equals(executionWorkReference)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_EXECUTION_REFERENCE,
                        "requirements",
                        "Requirement " + requirement.id().value() + " refers to different execution work"
                );
            }
            requirement.exactResourceId().ifPresent(resourceId -> {
                if (!exactResources.add(resourceId)) {
                    AllocationValidation.add(
                            failures,
                            AllocationValidationFailureCode.DUPLICATE_RESOURCE,
                            "requirements",
                            "Duplicate exact Resource: " + resourceId.value()
                    );
                }
            });
            if (!capacityKeys.add(requirement.selectorKey())) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.DUPLICATE_CAPACITY_KEY,
                        "requirements",
                        "Duplicate Requirement Capacity key: " + requirement.selectorKey()
                );
            }
        }

        if (sourceRequest != null) {
            if (id != null && !sourceRequest.allocationSetId().equals(id)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_REQUEST_ASSOCIATION,
                        "sourceRequest",
                        "Source request belongs to another AllocationSet"
                );
            }
            if (executionWorkReference != null
                    && !sourceRequest.executionWorkReference().equals(executionWorkReference)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_EXECUTION_REFERENCE,
                        "sourceRequest",
                        "Source request refers to different execution work"
                );
            }
            List<RequirementId> canonicalIds = canonical.stream()
                    .map(RequirementDefinition::id)
                    .toList();
            if (!sourceRequest.requirementIds().equals(canonicalIds)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_REQUEST_ASSOCIATION,
                        "sourceRequest",
                        "Source request Requirement ids do not match the AllocationSet"
                );
            }
            if (planningCycleReference != null
                    && !sourceRequest.orderingContext().planningCycleReference()
                    .equals(planningCycleReference)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCONSISTENT_REQUEST_ASSOCIATION,
                        "planningCycleReference",
                        "AllocationSet Planning Cycle does not match its source request"
                );
            }
        }
        if (creationSimulationTick < 0L) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                    "creationSimulationTick",
                    "AllocationSet creation tick must not be negative"
            );
        }
        if (sourceRequest != null && creationSimulationTick != sourceRequest.creationSimulationTick()) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INCONSISTENT_REQUEST_ASSOCIATION,
                    "creationSimulationTick",
                    "AllocationSet creation tick must match its source request"
            );
        }
        if (expirationSimulationTick != null
                && expirationSimulationTick.isPresent()
                && expirationSimulationTick.getAsLong() < creationSimulationTick) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_EXPIRATION,
                    "expirationSimulationTick",
                    "AllocationSet expiration cannot precede creation"
            );
        }
        if (schemaVersion != AllocationSchema.CURRENT_VERSION) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_SCHEMA_VERSION,
                    "schemaVersion",
                    "Unsupported Allocation schema version: " + schemaVersion
            );
        }
        if (sourceRequest != null && executionWorkReference != null && id != null
                && schemaVersion == AllocationSchema.CURRENT_VERSION) {
            AllocationSetId expected = AllocationIds.setId(
                    sourceRequest.orderingContext().sourceApprovedPlanReference(),
                    executionWorkReference,
                    sourceRequest.id(),
                    schemaVersion
            );
            if (!expected.equals(id)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.NONCANONICAL_INPUT,
                        "id",
                        "AllocationSet id does not match its canonical structural identity"
                );
            }
        }
        AllocationValidation.throwIfAny(failures);
        return new AllocationSetDefinition(
                id,
                executionWorkReference,
                sourceRequest.id(),
                canonical.stream().map(RequirementDefinition::id).toList(),
                planningCycleReference,
                creationSimulationTick,
                expirationSimulationTick,
                metadata,
                schemaVersion
        );
    }

    public AllocationSetId id() {
        return id;
    }

    public ExternalReference executionWorkReference() {
        return executionWorkReference;
    }

    public AllocationRequestId sourceRequestId() {
        return sourceRequestId;
    }

    public List<RequirementId> requirementIds() {
        return requirementIds;
    }

    public ExternalReference planningCycleReference() {
        return planningCycleReference;
    }

    public long creationSimulationTick() {
        return creationSimulationTick;
    }

    public OptionalLong expirationSimulationTick() {
        return expirationSimulationTick;
    }

    public AllocationMetadata metadata() {
        return metadata;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    @Override
    public int compareTo(AllocationSetDefinition other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AllocationSetDefinition that)) {
            return false;
        }
        return creationSimulationTick == that.creationSimulationTick
                && schemaVersion == that.schemaVersion
                && id.equals(that.id)
                && executionWorkReference.equals(that.executionWorkReference)
                && sourceRequestId.equals(that.sourceRequestId)
                && requirementIds.equals(that.requirementIds)
                && planningCycleReference.equals(that.planningCycleReference)
                && expirationSimulationTick.equals(that.expirationSimulationTick)
                && metadata.equals(that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                id,
                executionWorkReference,
                sourceRequestId,
                requirementIds,
                planningCycleReference,
                creationSimulationTick,
                expirationSimulationTick,
                metadata,
                schemaVersion
        );
    }

    @Override
    public String toString() {
        return "AllocationSetDefinition[id=" + id.value()
                + ", sourceRequestId=" + sourceRequestId.value()
                + ", requirementIds=" + requirementIds
                + ", creationSimulationTick=" + creationSimulationTick
                + ", schemaVersion=" + schemaVersion + "]";
    }
}
