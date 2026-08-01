package com.butchercraft.world.workforce.employee;

import com.butchercraft.world.business.BusinessId;

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

public final class EmployeeRegistry {
    private static final EmployeeRegistry EMPTY = new EmployeeRegistry(List.of(), Map.of(), Map.of());

    private final List<EmployeeRecord> records;
    private final Map<EmployeeId, EmployeeRecord> recordsById;
    private final Map<BusinessId, List<EmployeeRecord>> recordsByBusinessId;

    private EmployeeRegistry(
            List<EmployeeRecord> records,
            Map<EmployeeId, EmployeeRecord> recordsById,
            Map<BusinessId, List<EmployeeRecord>> recordsByBusinessId
    ) {
        this.records = records;
        this.recordsById = recordsById;
        this.recordsByBusinessId = recordsByBusinessId;
    }

    public static EmployeeRegistry empty() {
        return EMPTY;
    }

    public static EmployeeRegistry of(Collection<EmployeeRecord> records) {
        Objects.requireNonNull(records, "records");
        if (records.isEmpty()) {
            return EMPTY;
        }
        List<EmployeeRecord> deterministicRecords = records.stream()
                .map(record -> Objects.requireNonNull(record, "record"))
                .sorted(Comparator.comparing((EmployeeRecord record) -> record.businessId().value())
                        .thenComparing(record -> record.employeeId().value()))
                .toList();
        rejectDuplicates(deterministicRecords);
        Map<EmployeeId, EmployeeRecord> byId = deterministicRecords.stream()
                .collect(Collectors.toUnmodifiableMap(EmployeeRecord::employeeId, Function.identity()));
        Map<BusinessId, List<EmployeeRecord>> byBusinessId = deterministicRecords.stream()
                .collect(Collectors.groupingBy(
                        EmployeeRecord::businessId,
                        Collectors.collectingAndThen(Collectors.toList(), List::copyOf)
                ));
        return new EmployeeRegistry(List.copyOf(deterministicRecords), byId, Map.copyOf(byBusinessId));
    }

    public Optional<EmployeeRecord> find(EmployeeId employeeId) {
        return Optional.ofNullable(recordsById.get(Objects.requireNonNull(employeeId, "employeeId")));
    }

    public boolean contains(EmployeeId employeeId) {
        return recordsById.containsKey(Objects.requireNonNull(employeeId, "employeeId"));
    }

    public List<EmployeeRecord> findByBusinessId(BusinessId businessId) {
        return recordsByBusinessId.getOrDefault(Objects.requireNonNull(businessId, "businessId"), List.of());
    }

    public List<EmployeeRecord> records() {
        return records;
    }

    public int size() {
        return records.size();
    }

    EmployeeRegistry with(EmployeeRecord record) {
        Objects.requireNonNull(record, "record");
        List<EmployeeRecord> updated = new ArrayList<>(records);
        updated.removeIf(existing -> existing.employeeId().equals(record.employeeId()));
        updated.add(record);
        return of(updated);
    }

    private static void rejectDuplicates(List<EmployeeRecord> records) {
        Set<EmployeeId> seen = new HashSet<>();
        for (EmployeeRecord record : records) {
            if (!seen.add(record.employeeId())) {
                throw new IllegalArgumentException("Duplicate employee id: " + record.employeeId().value());
            }
        }
    }
}
