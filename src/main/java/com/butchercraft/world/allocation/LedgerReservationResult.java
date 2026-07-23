package com.butchercraft.world.allocation;

import java.util.List;
import java.util.Optional;

record LedgerReservationResult(
        boolean accepted,
        Optional<AllocationLedgerEntryView> entry,
        Optional<AllocationCycleFailure> failure,
        Optional<AllocationConflictRecord> conflict,
        List<AllocationSetId> blockingSetIds
) {
    LedgerReservationResult {
        entry = AllocationValidation.required(entry, "entry");
        failure = AllocationValidation.required(failure, "failure");
        conflict = AllocationValidation.required(conflict, "conflict");
        blockingSetIds = AllocationValidation.required(
                blockingSetIds,
                "blockingSetIds"
        ).stream().sorted().distinct().toList();
        if (accepted != entry.isPresent()
                || accepted == failure.isPresent()
                || (accepted && (conflict.isPresent()
                || !blockingSetIds.isEmpty()))) {
            throw new IllegalArgumentException("Ledger reservation result is inconsistent");
        }
    }

    static LedgerReservationResult accepted(AllocationLedgerEntryView entry) {
        return new LedgerReservationResult(
                true,
                Optional.of(entry),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
    }

    static LedgerReservationResult rejected(
            AllocationCycleFailure failure,
            Optional<AllocationConflictRecord> conflict,
            List<AllocationSetId> blockingSetIds
    ) {
        return new LedgerReservationResult(
                false,
                Optional.empty(),
                Optional.of(failure),
                conflict,
                blockingSetIds
        );
    }
}
