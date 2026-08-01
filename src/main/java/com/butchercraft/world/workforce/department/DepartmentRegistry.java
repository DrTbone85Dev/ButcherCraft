package com.butchercraft.world.workforce.department;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DepartmentRegistry {
    private static final DepartmentRegistry EMPTY = new DepartmentRegistry(List.of(), Map.of());

    private final List<DepartmentRecord> records;
    private final Map<DepartmentId, DepartmentRecord> recordsById;

    private DepartmentRegistry(List<DepartmentRecord> records, Map<DepartmentId, DepartmentRecord> recordsById) {
        this.records = records;
        this.recordsById = recordsById;
    }

    public static DepartmentRegistry empty() {
        return EMPTY;
    }

    public static DepartmentRegistry of(Collection<DepartmentRecord> records) {
        Objects.requireNonNull(records, "records");
        if (records.isEmpty()) {
            return EMPTY;
        }
        List<DepartmentRecord> deterministicRecords = records.stream()
                .map(record -> Objects.requireNonNull(record, "record"))
                .sorted(Comparator.comparing(record -> record.departmentId().value()))
                .toList();
        rejectDuplicates(deterministicRecords);
        Map<DepartmentId, DepartmentRecord> byId = deterministicRecords.stream()
                .collect(Collectors.toUnmodifiableMap(DepartmentRecord::departmentId, Function.identity()));
        return new DepartmentRegistry(List.copyOf(deterministicRecords), byId);
    }

    public Optional<DepartmentRecord> find(DepartmentId departmentId) {
        return Optional.ofNullable(recordsById.get(Objects.requireNonNull(departmentId, "departmentId")));
    }

    public boolean contains(DepartmentId departmentId) {
        return recordsById.containsKey(Objects.requireNonNull(departmentId, "departmentId"));
    }

    public List<DepartmentRecord> records() {
        return records;
    }

    public DepartmentRegistry with(DepartmentRecord record) {
        Objects.requireNonNull(record, "record");
        List<DepartmentRecord> updated = new ArrayList<>(records);
        updated.removeIf(existing -> existing.departmentId().equals(record.departmentId()));
        updated.add(record);
        return of(updated);
    }

    private static void rejectDuplicates(List<DepartmentRecord> records) {
        Set<DepartmentId> seen = new HashSet<>();
        for (DepartmentRecord record : records) {
            if (!seen.add(record.departmentId())) {
                throw new IllegalArgumentException("Duplicate department id: " + record.departmentId().value());
            }
        }
    }
}
