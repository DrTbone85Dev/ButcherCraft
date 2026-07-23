package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public record RequirementDefinition(
        RequirementId id,
        AllocationSetId allocationSetId,
        ExternalReference executionWorkReference,
        ResourceCategory resourceCategory,
        CapacityTypeId capacityTypeId,
        Optional<ResourceId> exactResourceId,
        AllocationQuantity requiredQuantity,
        CapacityUnitId capacityUnitId,
        long creationSimulationTick,
        AllocationMetadata metadata,
        int schemaVersion
) implements Comparable<RequirementDefinition> {
    private static final Comparator<RequirementDefinition> ORDER = Comparator
            .comparing(RequirementDefinition::id);

    public RequirementDefinition {
        List<AllocationValidationFailure> failures = AllocationValidation.failures();
        AllocationValidation.required(id, "id", failures);
        AllocationValidation.required(allocationSetId, "allocationSetId", failures);
        AllocationValidation.required(executionWorkReference, "executionWorkReference", failures);
        AllocationValidation.required(resourceCategory, "resourceCategory", failures);
        AllocationValidation.required(capacityTypeId, "capacityTypeId", failures);
        AllocationValidation.required(exactResourceId, "exactResourceId", failures);
        AllocationValidation.required(requiredQuantity, "requiredQuantity", failures);
        AllocationValidation.required(capacityUnitId, "capacityUnitId", failures);
        AllocationValidation.required(metadata, "metadata", failures);
        if (requiredQuantity != null) {
            if (!requiredQuantity.isPositive()) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.ZERO_QUANTITY,
                        "requiredQuantity",
                        "Requirement quantity must be positive"
                );
            }
            if (capacityUnitId != null && !requiredQuantity.unitId().equals(capacityUnitId)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.INCOMPATIBLE_UNIT,
                        "capacityUnitId",
                        "Requirement quantity unit does not match capacityUnitId"
                );
            }
        }
        if (creationSimulationTick < 0L) {
            AllocationValidation.add(
                    failures,
                    AllocationValidationFailureCode.INVALID_SIMULATION_TICK,
                    "creationSimulationTick",
                    "Requirement creation tick must not be negative"
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
        if (failures.isEmpty()) {
            RequirementId expected = AllocationIds.requirementId(
                    allocationSetId,
                    executionWorkReference,
                    resourceCategory,
                    capacityTypeId,
                    exactResourceId,
                    requiredQuantity,
                    schemaVersion
            );
            if (!expected.equals(id)) {
                AllocationValidation.add(
                        failures,
                        AllocationValidationFailureCode.NONCANONICAL_INPUT,
                        "id",
                        "Requirement id does not match its canonical structural identity"
                );
            }
        }
        AllocationValidation.throwIfAny(failures);
    }

    public static RequirementDefinition create(
            AllocationSetId allocationSetId,
            ExternalReference executionWorkReference,
            ResourceCategory resourceCategory,
            CapacityTypeId capacityTypeId,
            Optional<ResourceId> exactResourceId,
            AllocationQuantity requiredQuantity,
            long creationSimulationTick,
            AllocationMetadata metadata
    ) {
        RequirementId id = AllocationIds.requirementId(
                allocationSetId,
                executionWorkReference,
                resourceCategory,
                capacityTypeId,
                exactResourceId,
                requiredQuantity,
                AllocationSchema.CURRENT_VERSION
        );
        return new RequirementDefinition(
                id,
                allocationSetId,
                executionWorkReference,
                resourceCategory,
                capacityTypeId,
                exactResourceId,
                requiredQuantity,
                requiredQuantity.unitId(),
                creationSimulationTick,
                metadata,
                AllocationSchema.CURRENT_VERSION
        );
    }

    public Optional<CapacityKey> exactCapacityKey() {
        return exactResourceId.map(resourceId ->
                new CapacityKey(resourceId, capacityTypeId, capacityUnitId));
    }

    public String selectorKey() {
        return exactResourceId.map(ResourceId::value).orElse(resourceCategory.value())
                + "|" + capacityTypeId.value() + "|" + capacityUnitId.value();
    }

    @Override
    public int compareTo(RequirementDefinition other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }
}
