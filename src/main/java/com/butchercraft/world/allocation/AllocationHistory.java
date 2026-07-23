package com.butchercraft.world.allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AllocationHistory {
    private static final AllocationHistory EMPTY = new AllocationHistory(List.of());

    private final List<AllocationRuntimeTransitionRecord> records;
    private final Map<AllocationSetId, List<AllocationRuntimeTransitionRecord>> bySet;

    private AllocationHistory(List<AllocationRuntimeTransitionRecord> records) {
        this.records = records;
        Map<AllocationSetId, List<AllocationRuntimeTransitionRecord>> groups =
                new LinkedHashMap<>();
        records.forEach(record -> groups.computeIfAbsent(
                record.allocationSetId(),
                ignored -> new ArrayList<>()
        ).add(record));
        Map<AllocationSetId, List<AllocationRuntimeTransitionRecord>> immutable =
                new LinkedHashMap<>();
        groups.forEach((id, values) -> immutable.put(id, List.copyOf(values)));
        bySet = java.util.Collections.unmodifiableMap(immutable);
    }

    public static AllocationHistory empty() {
        return EMPTY;
    }

    public static AllocationHistory of(
            Collection<AllocationRuntimeTransitionRecord> source
    ) {
        List<AllocationRuntimeTransitionRecord> records = new ArrayList<>(
                AllocationValidation.required(source, "records")
        );
        records.forEach(record -> AllocationValidation.required(record, "record"));
        records.sort(Comparator.naturalOrder());
        validateChains(records);
        return records.isEmpty()
                ? EMPTY
                : new AllocationHistory(List.copyOf(records));
    }

    public int size() {
        return records.size();
    }

    public List<AllocationRuntimeTransitionRecord> records() {
        return records;
    }

    public List<AllocationRuntimeTransitionRecord> findBySet(AllocationSetId id) {
        return bySet.getOrDefault(AllocationValidation.required(id, "id"), List.of());
    }

    public List<AllocationRuntimeTransitionRecord> findByStatus(
            AllocationRuntimeStatus status
    ) {
        AllocationRuntimeStatus target = AllocationValidation.required(status, "status");
        return records.stream().filter(record -> record.status() == target).toList();
    }

    public List<AllocationRuntimeTransitionRecord> findBetween(
            long firstInclusive,
            long lastInclusive
    ) {
        AllocationValidation.tick(firstInclusive, "firstInclusive");
        AllocationValidation.tick(lastInclusive, "lastInclusive");
        if (lastInclusive < firstInclusive) {
            throw AllocationRuntimeValidation.failure(
                    AllocationRuntimeFailureCode.INVALID_SIMULATION_TICK,
                    "history",
                    "Allocation history tick range is reversed"
            );
        }
        return records.stream()
                .filter(record -> record.transitionSimulationTick() >= firstInclusive
                        && record.transitionSimulationTick() <= lastInclusive)
                .toList();
    }

    public Optional<AllocationRuntimeTransitionRecord> latest(AllocationSetId id) {
        List<AllocationRuntimeTransitionRecord> history = findBySet(id);
        return history.isEmpty() ? Optional.empty() : Optional.of(history.getLast());
    }

    AllocationHistory append(AllocationRuntimeTransitionRecord record) {
        List<AllocationRuntimeTransitionRecord> updated = new ArrayList<>(records);
        updated.add(AllocationValidation.required(record, "record"));
        return of(updated);
    }

    private static void validateChains(
            List<AllocationRuntimeTransitionRecord> records
    ) {
        Map<AllocationSetId, List<AllocationRuntimeTransitionRecord>> groups =
                new LinkedHashMap<>();
        records.forEach(record -> groups.computeIfAbsent(
                record.allocationSetId(),
                ignored -> new ArrayList<>()
        ).add(record));
        for (Map.Entry<AllocationSetId, List<AllocationRuntimeTransitionRecord>> entry
                : groups.entrySet()) {
            List<AllocationRuntimeTransitionRecord> chain = entry.getValue().stream()
                    .sorted(Comparator.comparingLong(
                            AllocationRuntimeTransitionRecord::revision
                    ))
                    .toList();
            for (int index = 0; index < chain.size(); index++) {
                AllocationRuntimeTransitionRecord record = chain.get(index);
                if (record.revision() != index) {
                    throw AllocationRuntimeValidation.failure(
                            AllocationRuntimeFailureCode.INVALID_HISTORY,
                            entry.getKey().value(),
                            "Allocation history revisions must be contiguous from zero"
                    );
                }
                if (index == 0) {
                    if (record.previousStatus().isPresent()) {
                        invalidChain(entry.getKey(), "Initial history has a previous status");
                    }
                    continue;
                }
                AllocationRuntimeTransitionRecord previous = chain.get(index - 1);
                if (!record.previousStatus().equals(Optional.of(previous.status()))
                        || record.transitionSimulationTick()
                        < previous.transitionSimulationTick()) {
                    invalidChain(
                            entry.getKey(),
                            "Allocation history status or tick chain is inconsistent"
                    );
                }
            }
        }
    }

    private static void invalidChain(AllocationSetId id, String message) {
        throw AllocationRuntimeValidation.failure(
                AllocationRuntimeFailureCode.INVALID_HISTORY,
                id.value(),
                message
        );
    }
}
