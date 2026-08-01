package com.butchercraft.workstation.reservation;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkstationReservationManager {
    private final Map<String, WorkstationReservationRecord> byWorkstation = new HashMap<>();
    private final Map<String, WorkstationReservationRecord> byEmployee = new HashMap<>();

    public WorkstationReservationManager(WorkstationReservationDirectory directory) {
        reconciled(Objects.requireNonNull(directory, "directory").records())
                .forEach(this::put);
    }

    public static WorkstationReservationManager empty() {
        return new WorkstationReservationManager(WorkstationReservationDirectory.empty());
    }

    public synchronized WorkstationReservationResult<WorkstationReservationRecord> reserve(
            WorkstationReservationRequest request
    ) {
        Objects.requireNonNull(request, "request");
        WorkstationReservationRecord employeeReservation = byEmployee.get(request.employeeIdentity());
        WorkstationReservationRecord workstationReservation = byWorkstation.get(request.workstationIdentity());
        if (employeeReservation != null
                && employeeReservation.workstationIdentity().equals(request.workstationIdentity())) {
            return WorkstationReservationResult.succeeded(employeeReservation);
        }
        if (employeeReservation != null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.EMPLOYEE_ALREADY_RESERVED,
                    "Employee already has an active workstation reservation: "
                            + employeeReservation.workstationIdentity()
            );
        }
        if (workstationReservation != null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.WORKSTATION_ALREADY_RESERVED,
                    "Workstation is already reserved by employee: "
                            + workstationReservation.employeeIdentity()
            );
        }
        WorkstationReservationRecord record = WorkstationReservationRecord.enRoute(request);
        put(record);
        return WorkstationReservationResult.succeeded(record);
    }

    public synchronized Optional<WorkstationReservationRecord> findByWorkstation(String workstationIdentity) {
        return Optional.ofNullable(byWorkstation.get(
                WorkstationReservationValidation.requireIdentity(workstationIdentity, "workstation identity")
        ));
    }

    public synchronized Optional<WorkstationReservationRecord> findByEmployee(String employeeIdentity) {
        return Optional.ofNullable(byEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity")
        ));
    }

    public synchronized List<WorkstationReservationRecord> activeReservations() {
        return byWorkstation.values().stream()
                .sorted(Comparator.comparing(WorkstationReservationRecord::workstationIdentity)
                        .thenComparing(WorkstationReservationRecord::employeeIdentity))
                .toList();
    }

    public synchronized WorkstationReservationDirectory directory() {
        return WorkstationReservationDirectory.of(activeReservations());
    }

    public synchronized Optional<WorkstationReservationRecord> markEnRoute(
            String employeeIdentity,
            String workstationIdentity
    ) {
        return transition(employeeIdentity, workstationIdentity, WorkstationReservationState.EMPLOYEE_EN_ROUTE);
    }

    public synchronized Optional<WorkstationReservationRecord> markArrived(
            String employeeIdentity,
            String workstationIdentity
    ) {
        return transition(employeeIdentity, workstationIdentity, WorkstationReservationState.EMPLOYEE_ARRIVED);
    }

    public synchronized Optional<WorkstationReservationRecord> updateOperatingPosition(
            String employeeIdentity,
            String workstationIdentity,
            int x,
            int y,
            int z
    ) {
        WorkstationReservationRecord existing = byEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity")
        );
        String normalizedWorkstation = WorkstationReservationValidation.requireIdentity(
                workstationIdentity,
                "workstation identity"
        );
        if (existing == null || !existing.workstationIdentity().equals(normalizedWorkstation)) {
            return Optional.empty();
        }
        if (existing.operatingX() == x && existing.operatingY() == y && existing.operatingZ() == z) {
            return Optional.of(existing);
        }
        WorkstationReservationRecord updated = existing.withOperatingPosition(x, y, z);
        remove(existing);
        put(updated);
        return Optional.of(updated);
    }

    public synchronized WorkstationReservationResult<WorkstationReservationRecord> releaseByEmployee(
            String employeeIdentity,
            String reason
    ) {
        WorkstationReservationRecord existing = byEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity")
        );
        if (existing == null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.UNKNOWN_RESERVATION,
                    "Employee has no active workstation reservation"
            );
        }
        remove(existing);
        return WorkstationReservationResult.succeeded(existing.released(reason));
    }

    public synchronized Optional<WorkstationReservationRecord> invalidateByEmployee(
            String employeeIdentity,
            String reason
    ) {
        WorkstationReservationRecord existing = byEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity")
        );
        if (existing == null) {
            return Optional.empty();
        }
        remove(existing);
        return Optional.of(existing.invalidated(reason));
    }

    public synchronized Optional<WorkstationReservationRecord> invalidateByWorkstation(
            String workstationIdentity,
            String reason
    ) {
        WorkstationReservationRecord existing = byWorkstation.get(
                WorkstationReservationValidation.requireIdentity(workstationIdentity, "workstation identity")
        );
        if (existing == null) {
            return Optional.empty();
        }
        remove(existing);
        return Optional.of(existing.invalidated(reason));
    }

    private Optional<WorkstationReservationRecord> transition(
            String employeeIdentity,
            String workstationIdentity,
            WorkstationReservationState nextState
    ) {
        WorkstationReservationRecord existing = byEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity")
        );
        String normalizedWorkstation = WorkstationReservationValidation.requireIdentity(
                workstationIdentity,
                "workstation identity"
        );
        if (existing == null || !existing.workstationIdentity().equals(normalizedWorkstation)) {
            return Optional.empty();
        }
        if (existing.state() == nextState) {
            return Optional.of(existing);
        }
        WorkstationReservationRecord updated = existing.withState(nextState);
        remove(existing);
        put(updated);
        return Optional.of(updated);
    }

    private void put(WorkstationReservationRecord record) {
        if (!record.active()) {
            return;
        }
        byWorkstation.put(record.workstationIdentity(), record);
        byEmployee.put(record.employeeIdentity(), record);
    }

    private void remove(WorkstationReservationRecord record) {
        byWorkstation.remove(record.workstationIdentity());
        byEmployee.remove(record.employeeIdentity());
    }

    private static List<WorkstationReservationRecord> reconciled(
            Collection<WorkstationReservationRecord> records
    ) {
        Map<String, WorkstationReservationRecord> acceptedByWorkstation = new HashMap<>();
        Map<String, WorkstationReservationRecord> acceptedByEmployee = new HashMap<>();
        Objects.requireNonNull(records, "records").stream()
                .filter(WorkstationReservationRecord::active)
                .sorted(Comparator.comparingLong(WorkstationReservationRecord::createdTick)
                        .thenComparing(WorkstationReservationRecord::workstationIdentity)
                        .thenComparing(WorkstationReservationRecord::employeeIdentity))
                .forEach(record -> {
                    if (!acceptedByWorkstation.containsKey(record.workstationIdentity())
                            && !acceptedByEmployee.containsKey(record.employeeIdentity())) {
                        acceptedByWorkstation.put(record.workstationIdentity(), record);
                        acceptedByEmployee.put(record.employeeIdentity(), record);
                    }
                });
        return acceptedByWorkstation.values().stream()
                .sorted(Comparator.comparing(WorkstationReservationRecord::workstationIdentity)
                        .thenComparing(WorkstationReservationRecord::employeeIdentity))
                .toList();
    }
}
