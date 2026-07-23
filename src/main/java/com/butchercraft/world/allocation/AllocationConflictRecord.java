package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record AllocationConflictRecord(
        AllocationConflictType type,
        CapacityKey capacityKey,
        List<AllocationSetId> winnerSetIds,
        List<AllocationSetId> loserSetIds,
        AllocationQuantity exactShortfall,
        AllocationMetadata metadata
) implements Comparable<AllocationConflictRecord> {
    private static final Comparator<AllocationConflictRecord> ORDER = Comparator
            .comparing(AllocationConflictRecord::capacityKey)
            .thenComparing(AllocationConflictRecord::type)
            .thenComparing(record -> record.winnerSetIds().getFirst())
            .thenComparing(record -> record.loserSetIds().getFirst());

    public AllocationConflictRecord {
        type = AllocationValidation.required(type, "type");
        capacityKey = AllocationValidation.required(capacityKey, "capacityKey");
        winnerSetIds = canonicalSets(winnerSetIds, "winnerSetIds");
        loserSetIds = canonicalSets(loserSetIds, "loserSetIds");
        exactShortfall = AllocationValidation.required(
                exactShortfall,
                "exactShortfall"
        ).requirePositive("exactShortfall");
        metadata = AllocationValidation.required(metadata, "metadata");
        if (!exactShortfall.unitId().equals(capacityKey.capacityUnitId())) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    capacityKey.toString(),
                    "Conflict shortfall unit does not match its Capacity key"
            );
        }
        Set<AllocationSetId> overlap = new HashSet<>(winnerSetIds);
        overlap.retainAll(loserSetIds);
        if (!overlap.isEmpty()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    capacityKey.toString(),
                    "Conflict winner and loser sets must be disjoint"
            );
        }
    }

    @Override
    public int compareTo(AllocationConflictRecord other) {
        return ORDER.compare(this, AllocationValidation.required(other, "other"));
    }

    private static List<AllocationSetId> canonicalSets(
            List<AllocationSetId> source,
            String field
    ) {
        List<AllocationSetId> values = new ArrayList<>(
                AllocationValidation.required(source, field)
        );
        values.forEach(value -> AllocationValidation.required(value, field));
        values.sort(Comparator.naturalOrder());
        if (values.isEmpty()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    field,
                    "Conflict " + field + " requires at least one AllocationSet"
            );
        }
        if (values.stream().distinct().count() != values.size()) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_REPORT,
                    field,
                    "Conflict " + field + " contains duplicate AllocationSets"
            );
        }
        return List.copyOf(values);
    }
}
