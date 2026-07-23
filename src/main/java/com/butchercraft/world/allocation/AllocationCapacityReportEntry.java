package com.butchercraft.world.allocation;

public record AllocationCapacityReportEntry(
        CapacityKey capacityKey,
        AllocationQuantity observedQuantity,
        AllocationQuantity committedQuantity,
        AllocationQuantity remainingQuantity
) implements Comparable<AllocationCapacityReportEntry> {
    public AllocationCapacityReportEntry {
        capacityKey = AllocationValidation.required(capacityKey, "capacityKey");
        observedQuantity = requireUnit(observedQuantity, capacityKey, "observedQuantity");
        committedQuantity = requireUnit(
                committedQuantity,
                capacityKey,
                "committedQuantity"
        );
        remainingQuantity = requireUnit(remainingQuantity, capacityKey, "remainingQuantity");
        if (!observedQuantity.equals(committedQuantity.add(remainingQuantity))) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    capacityKey.toString(),
                    "Capacity report quantities do not balance"
            );
        }
    }

    @Override
    public int compareTo(AllocationCapacityReportEntry other) {
        return capacityKey.compareTo(
                AllocationValidation.required(other, "other").capacityKey
        );
    }

    private static AllocationQuantity requireUnit(
            AllocationQuantity quantity,
            CapacityKey key,
            String field
    ) {
        AllocationQuantity value = AllocationValidation.required(quantity, field);
        if (!value.unitId().equals(key.capacityUnitId())) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    key.toString(),
                    field + " unit does not match Capacity key"
            );
        }
        return value;
    }
}
