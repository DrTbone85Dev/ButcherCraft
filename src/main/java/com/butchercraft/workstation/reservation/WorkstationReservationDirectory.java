package com.butchercraft.workstation.reservation;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record WorkstationReservationDirectory(List<WorkstationReservationRecord> records) {
    public WorkstationReservationDirectory {
        records = Objects.requireNonNull(records, "records").stream()
                .map(record -> Objects.requireNonNull(record, "record"))
                .sorted(Comparator.comparing(WorkstationReservationRecord::workstationIdentity)
                        .thenComparing(WorkstationReservationRecord::employeeIdentity))
                .toList();
    }

    public static WorkstationReservationDirectory empty() {
        return new WorkstationReservationDirectory(List.of());
    }

    public static WorkstationReservationDirectory of(Collection<WorkstationReservationRecord> records) {
        return new WorkstationReservationDirectory(List.copyOf(Objects.requireNonNull(records, "records")));
    }
}
