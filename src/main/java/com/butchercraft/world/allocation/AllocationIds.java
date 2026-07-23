package com.butchercraft.world.allocation;

import java.util.Objects;
import java.util.Optional;

public final class AllocationIds {
    private AllocationIds() {
    }

    public static AllocationRequestId requestId(
            ExternalReference executionWorkReference,
            AllocationOrderingContext orderingContext,
            int schemaVersion
    ) {
        ExternalReference work = AllocationValidation.required(
                executionWorkReference,
                "executionWorkReference"
        );
        AllocationOrderingContext ordering = AllocationValidation.required(
                orderingContext,
                "orderingContext"
        );
        AllocationValidation.schema(schemaVersion);
        return AllocationRequestId.of(AllocationValidation.derivedId(
                "allocation_request",
                work.canonicalKey(),
                ordering.canonicalKey(),
                Integer.toString(schemaVersion)
        ));
    }

    public static AllocationSetId setId(
            ExternalReference sourceApprovedPlanReference,
            ExternalReference executionWorkReference,
            AllocationRequestId requestId,
            int schemaVersion
    ) {
        AllocationValidation.schema(schemaVersion);
        return AllocationSetId.of(AllocationValidation.derivedId(
                "allocation_set",
                AllocationValidation.required(
                        sourceApprovedPlanReference,
                        "sourceApprovedPlanReference"
                ).canonicalKey(),
                AllocationValidation.required(
                        executionWorkReference,
                        "executionWorkReference"
                ).canonicalKey(),
                AllocationValidation.required(requestId, "requestId").value(),
                Integer.toString(schemaVersion)
        ));
    }

    public static RequirementId requirementId(
            AllocationSetId allocationSetId,
            ExternalReference executionWorkReference,
            ResourceCategory resourceCategory,
            CapacityTypeId capacityTypeId,
            Optional<ResourceId> exactResourceId,
            AllocationQuantity requiredQuantity,
            int schemaVersion
    ) {
        AllocationValidation.schema(schemaVersion);
        return RequirementId.of(AllocationValidation.derivedId(
                "allocation_requirement",
                AllocationValidation.required(allocationSetId, "allocationSetId").value(),
                AllocationValidation.required(
                        executionWorkReference,
                        "executionWorkReference"
                ).canonicalKey(),
                AllocationValidation.required(resourceCategory, "resourceCategory").value(),
                AllocationValidation.required(capacityTypeId, "capacityTypeId").value(),
                AllocationValidation.required(exactResourceId, "exactResourceId")
                        .map(ResourceId::value)
                        .orElse(""),
                AllocationValidation.required(requiredQuantity, "requiredQuantity").canonicalValue(),
                Integer.toString(schemaVersion)
        ));
    }

    public static AllocationCommitmentId commitmentId(
            AllocationCycleId allocationCycleId,
            AllocationSetId allocationSetId,
            RequirementId requirementId,
            ResourceId resourceId,
            CapacityId capacityId,
            int schemaVersion
    ) {
        AllocationValidation.schema(schemaVersion);
        return AllocationCommitmentId.of(AllocationValidation.derivedId(
                "allocation_commitment",
                AllocationValidation.required(allocationCycleId, "allocationCycleId").value(),
                AllocationValidation.required(allocationSetId, "allocationSetId").value(),
                AllocationValidation.required(requirementId, "requirementId").value(),
                AllocationValidation.required(resourceId, "resourceId").value(),
                AllocationValidation.required(capacityId, "capacityId").value(),
                Integer.toString(schemaVersion)
        ));
    }
}
