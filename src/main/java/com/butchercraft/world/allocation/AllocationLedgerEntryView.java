package com.butchercraft.world.allocation;

import java.util.Comparator;
import java.util.List;

public record AllocationLedgerEntryView(
        CapacityId capacityId,
        CapacityKey capacityKey,
        ResourceCategory resourceCategory,
        ResourceAvailability availability,
        ResourceExclusivityMode exclusivityMode,
        AllocationQuantity observedQuantity,
        AllocationQuantity existingCommittedQuantity,
        AllocationQuantity proposedCommittedQuantity,
        AllocationQuantity remainingQuantity,
        long observationSimulationTick,
        ExternalReference resourceObservationReference,
        ExternalReference capacityObservationReference,
        List<AllocationCommitmentId> existingCommitmentIds,
        List<AllocationCommitmentId> proposedCommitmentIds,
        List<AllocationSetId> consumingSetIds
) implements Comparable<AllocationLedgerEntryView> {
    private static final Comparator<AllocationLedgerEntryView> ORDER =
            Comparator.comparing(AllocationLedgerEntryView::capacityKey)
                    .thenComparing(AllocationLedgerEntryView::capacityId);

    public AllocationLedgerEntryView {
        capacityId = AllocationValidation.required(capacityId, "capacityId");
        capacityKey = AllocationValidation.required(capacityKey, "capacityKey");
        resourceCategory = AllocationValidation.required(
                resourceCategory,
                "resourceCategory"
        );
        availability = AllocationValidation.required(availability, "availability");
        exclusivityMode = AllocationValidation.required(
                exclusivityMode,
                "exclusivityMode"
        );
        observedQuantity = requireUnit(
                observedQuantity,
                capacityKey,
                "observedQuantity"
        );
        existingCommittedQuantity = requireUnit(
                existingCommittedQuantity,
                capacityKey,
                "existingCommittedQuantity"
        );
        proposedCommittedQuantity = requireUnit(
                proposedCommittedQuantity,
                capacityKey,
                "proposedCommittedQuantity"
        );
        remainingQuantity = requireUnit(
                remainingQuantity,
                capacityKey,
                "remainingQuantity"
        );
        AllocationQuantity balanced = existingCommittedQuantity
                .add(proposedCommittedQuantity)
                .add(remainingQuantity);
        if (!observedQuantity.equals(balanced)) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.CYCLE,
                    capacityId.value(),
                    "Working Capacity ledger quantities do not balance"
            );
        }
        observationSimulationTick = AllocationValidation.tick(
                observationSimulationTick,
                "observationSimulationTick"
        );
        resourceObservationReference = AllocationValidation.required(
                resourceObservationReference,
                "resourceObservationReference"
        );
        capacityObservationReference = AllocationValidation.required(
                capacityObservationReference,
                "capacityObservationReference"
        );
        existingCommitmentIds = canonical(
                existingCommitmentIds,
                "existingCommitmentIds"
        );
        proposedCommitmentIds = canonical(
                proposedCommitmentIds,
                "proposedCommitmentIds"
        );
        consumingSetIds = canonical(consumingSetIds, "consumingSetIds");
    }

    public AllocationQuantity totalCommittedQuantity() {
        return existingCommittedQuantity.add(proposedCommittedQuantity);
    }

    @Override
    public int compareTo(AllocationLedgerEntryView other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    private static AllocationQuantity requireUnit(
            AllocationQuantity quantity,
            CapacityKey key,
            String field
    ) {
        AllocationQuantity value = AllocationValidation.required(quantity, field);
        if (!value.unitId().equals(key.capacityUnitId())) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_CAPACITY_UNIT,
                    AllocationCycleFailureScope.CYCLE,
                    key.toString(),
                    field + " unit does not match Capacity key"
            );
        }
        return value;
    }

    private static <T extends Comparable<? super T>> List<T> canonical(
            List<T> source,
            String field
    ) {
        List<T> values = AllocationValidation.required(source, field).stream()
                .map(value -> AllocationValidation.required(value, field))
                .sorted()
                .toList();
        if (values.stream().distinct().count() != values.size()) {
            throw AllocationCycleValidation.failure(
                    AllocationCycleFailureCode.INVALID_RESULT,
                    AllocationCycleFailureScope.CYCLE,
                    field,
                    "Ledger view contains duplicate " + field
            );
        }
        return values;
    }
}
