package com.butchercraft.workstation.reservation;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorkstationReservationManager {
    private final WorldIdentityRootIdentity worldIdentity;
    private final Map<WorkstationReservationId, WorkstationReservationRecord> recordsById = new HashMap<>();
    private final Map<String, WorkstationReservationRecord> activeByEmployee = new HashMap<>();
    private final Map<String, WorkstationReservationRecord> activeByRequest = new HashMap<>();
    private final Map<String, List<WorkstationReservationRecord>> activeByWorkstation = new HashMap<>();
    private long nextSequence;
    private long ownerRevision;

    public WorkstationReservationManager(WorkstationReservationDirectory directory) {
        WorkstationReservationDirectory source = Objects.requireNonNull(directory, "directory");
        worldIdentity = source.worldIdentity();
        nextSequence = source.nextSequence();
        ownerRevision = source.ownerRevision();
        source.records().forEach(record -> recordsById.put(record.reservationId(), record));
        rebuildActiveIndexes();
    }

    public static WorkstationReservationManager empty(WorldIdentityRootIdentity worldIdentity) {
        return new WorkstationReservationManager(WorkstationReservationDirectory.empty(worldIdentity));
    }

    public synchronized WorkstationReservationCompatibility compatibility(WorkstationReservationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!worldIdentity.equals(request.worldIdentity())) {
            return WorkstationReservationCompatibility.rejected(
                    WorkstationReservationFailureCode.WORKSTATION_INSTANCE_CONFLICT,
                    "Reservation request belongs to a different World Identity"
            );
        }
        if (request.role() == WorkstationReservationRole.LEGACY_EXCLUSIVE) {
            return WorkstationReservationCompatibility.rejected(
                    WorkstationReservationFailureCode.UNSUPPORTED_ROLE,
                    "LEGACY_EXCLUSIVE cannot be acquired by new runtime requests"
            );
        }
        if (request.role() == WorkstationReservationRole.MATERIAL_HANDLER && !validHandlerLifecycle(request)) {
            return WorkstationReservationCompatibility.rejected(
                    WorkstationReservationFailureCode.INVALID_HANDLER_TRANSFER,
                    "Material handler lifecycle does not permit the requested endpoint access"
            );
        }
        WorkstationReservationRecord sameRequest = activeByRequest.get(request.requestIdentity());
        if (sameRequest != null) {
            if (request.sameLogicalBinding(sameRequest)) {
                return WorkstationReservationCompatibility.duplicate(sameRequest);
            }
            return WorkstationReservationCompatibility.rejected(
                    WorkstationReservationFailureCode.WORKSTATION_INSTANCE_CONFLICT,
                    "Reservation request identity is already bound to different canonical inputs"
            );
        }
        WorkstationReservationRecord employeeReservation = activeByEmployee.get(request.employeeIdentity());
        if (employeeReservation != null) {
            return WorkstationReservationCompatibility.rejected(
                    WorkstationReservationFailureCode.EMPLOYEE_ALREADY_RESERVED,
                    "Employee already has active reservation " + employeeReservation.reservationId().value()
            );
        }
        for (WorkstationReservationRecord active : activeRecords()) {
            if (active.locationIdentity().equals(locationIdentity(request))
                    && !active.workstationIdentity().equals(request.workstationIdentity())) {
                if (active.role() == WorkstationReservationRole.LEGACY_EXCLUSIVE) {
                    return WorkstationReservationCompatibility.rejected(
                            WorkstationReservationFailureCode.LEGACY_EXCLUSIVE_CONFLICT,
                            "Workstation location is held by a migrated legacy-exclusive reservation"
                    );
                }
                return WorkstationReservationCompatibility.rejected(
                        WorkstationReservationFailureCode.WORKSTATION_INSTANCE_CONFLICT,
                        "Workstation location is bound to a different instance identity"
                );
            }
        }
        List<WorkstationReservationRecord> occupants = activeByWorkstation.getOrDefault(
                request.workstationIdentity(), List.of());
        for (WorkstationReservationRecord occupant : occupants) {
            if (occupant.role() == WorkstationReservationRole.LEGACY_EXCLUSIVE) {
                return WorkstationReservationCompatibility.rejected(
                        WorkstationReservationFailureCode.LEGACY_EXCLUSIVE_CONFLICT,
                        "Workstation is held by a migrated legacy-exclusive reservation"
                );
            }
            if (occupant.role() == request.role()) {
                WorkstationReservationFailureCode code = request.role() == WorkstationReservationRole.MACHINE_OPERATOR
                        ? WorkstationReservationFailureCode.OPERATOR_ALREADY_RESERVED
                        : WorkstationReservationFailureCode.HANDLER_ALREADY_RESERVED;
                return WorkstationReservationCompatibility.rejected(
                        code,
                        "Workstation already has an active " + request.role().serializedName()
                );
            }
        }
        return WorkstationReservationCompatibility.allowed();
    }

    public synchronized WorkstationReservationResult<WorkstationReservationRecord> reserve(
            WorkstationReservationRequest request
    ) {
        WorkstationReservationCompatibility compatibility = compatibility(request);
        if (compatibility.decision() == WorkstationReservationCompatibilityDecision.DUPLICATE) {
            return WorkstationReservationResult.succeeded(
                    compatibility.existingReservation().orElseThrow(),
                    WorkstationReservationSuccessCode.EXISTING_RESERVATION_OBSERVED
            );
        }
        if (compatibility.decision() == WorkstationReservationCompatibilityDecision.REJECTED) {
            WorkstationReservationFailure failure = compatibility.failure().orElseThrow();
            return WorkstationReservationResult.failed(failure.code(), failure.detail());
        }
        long revision = nextRevision();
        WorkstationReservationRecord record = WorkstationReservationRecord.acquire(request, nextSequence++, revision);
        recordsById.put(record.reservationId(), record);
        index(record);
        return WorkstationReservationResult.succeeded(record, WorkstationReservationSuccessCode.ACQUIRED);
    }

    public synchronized Optional<WorkstationReservationRecord> findByEmployee(String employeeIdentity) {
        return Optional.ofNullable(activeByEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity")));
    }

    public synchronized Optional<WorkstationReservationRecord> findById(WorkstationReservationId reservationId) {
        return Optional.ofNullable(recordsById.get(Objects.requireNonNull(reservationId, "reservationId")));
    }

    public synchronized Optional<WorkstationReservationRecord> operatorForWorkstation(String workstationIdentity) {
        return roleForWorkstation(workstationIdentity, WorkstationReservationRole.MACHINE_OPERATOR);
    }

    public synchronized Optional<WorkstationReservationRecord> handlerForWorkstation(String workstationIdentity) {
        return roleForWorkstation(workstationIdentity, WorkstationReservationRole.MATERIAL_HANDLER);
    }

    public synchronized List<WorkstationReservationRecord> reservationsForWorkstation(String workstationIdentity) {
        String normalized = WorkstationReservationValidation.requireIdentity(workstationIdentity, "workstation identity");
        return activeByWorkstation.getOrDefault(normalized, List.of()).stream().sorted(roleOrder()).toList();
    }

    public synchronized List<WorkstationReservationRecord> activeReservations() {
        return activeRecords().stream()
                .sorted(Comparator.comparing(WorkstationReservationRecord::workstationIdentity)
                        .thenComparing(roleOrder()))
                .toList();
    }

    public synchronized List<WorkstationReservationRecord> allRecords() {
        return recordsById.values().stream().sorted().toList();
    }

    public synchronized WorkstationReservationDirectory directory() {
        return WorkstationReservationDirectory.of(
                worldIdentity, nextSequence, ownerRevision, allRecords());
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
        WorkstationReservationRecord existing = matchingEmployeeWorkstation(employeeIdentity, workstationIdentity);
        if (existing == null) return Optional.empty();
        if (existing.operatingX() == x && existing.operatingY() == y && existing.operatingZ() == z) {
            return Optional.of(existing);
        }
        return Optional.of(replace(existing, existing.withOperatingPosition(x, y, z, nextRevision())));
    }

    public synchronized WorkstationReservationResult<WorkstationReservationRecord> release(
            WorkstationReservationId reservationId,
            WorkstationReservationRole expectedRole,
            String reason
    ) {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(expectedRole, "expectedRole");
        WorkstationReservationRecord existing = recordsById.get(reservationId);
        if (existing == null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.STALE_RESERVATION,
                    "Reservation identity is unknown or superseded"
            );
        }
        if (existing.role() != expectedRole) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.STALE_RESERVATION,
                    "Reservation role does not match exact release request"
            );
        }
        if (!existing.active()) {
            return WorkstationReservationResult.succeeded(
                    existing,
                    WorkstationReservationSuccessCode.EXISTING_TERMINAL_RESULT_OBSERVED
            );
        }
        WorkstationReservationRecord released = existing.released(reason, nextRevision());
        replace(existing, released);
        return WorkstationReservationResult.succeeded(released, WorkstationReservationSuccessCode.RELEASED);
    }

    public synchronized WorkstationReservationResult<WorkstationReservationRecord> invalidate(
            WorkstationReservationId reservationId,
            WorkstationReservationRole expectedRole,
            String reason
    ) {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(expectedRole, "expectedRole");
        WorkstationReservationRecord existing = recordsById.get(reservationId);
        if (existing == null || existing.role() != expectedRole) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.STALE_RESERVATION,
                    "Reservation identity or role does not match exact invalidation request"
            );
        }
        if (!existing.active()) {
            return WorkstationReservationResult.succeeded(
                    existing,
                    WorkstationReservationSuccessCode.EXISTING_TERMINAL_RESULT_OBSERVED
            );
        }
        WorkstationReservationRecord invalidated = existing.invalidated(reason, nextRevision());
        replace(existing, invalidated);
        return WorkstationReservationResult.succeeded(
                invalidated,
                WorkstationReservationSuccessCode.INVALIDATED
        );
    }

    public synchronized WorkstationReservationResult<WorkstationReservationRecord> releaseByEmployee(
            String employeeIdentity,
            String reason
    ) {
        WorkstationReservationRecord existing = activeByEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity"));
        if (existing == null) {
            return WorkstationReservationResult.failed(
                    WorkstationReservationFailureCode.UNKNOWN_RESERVATION,
                    "Employee has no active workstation reservation"
            );
        }
        return release(existing.reservationId(), existing.role(), reason);
    }

    public synchronized Optional<WorkstationReservationRecord> invalidateByEmployee(
            String employeeIdentity,
            String reason
    ) {
        WorkstationReservationRecord existing = activeByEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity"));
        if (existing == null) return Optional.empty();
        return invalidate(existing.reservationId(), existing.role(), reason).value();
    }

    public synchronized List<WorkstationReservationRecord> invalidateByWorkstation(
            String workstationIdentity,
            String reason
    ) {
        List<WorkstationReservationRecord> existing = reservationsForWorkstation(workstationIdentity);
        List<WorkstationReservationRecord> invalidated = new ArrayList<>();
        for (WorkstationReservationRecord record : existing) {
            invalidated.add(replace(record, record.invalidated(reason, nextRevision())));
        }
        return List.copyOf(invalidated);
    }

    private Optional<WorkstationReservationRecord> transition(
            String employeeIdentity,
            String workstationIdentity,
            WorkstationReservationState nextState
    ) {
        WorkstationReservationRecord existing = matchingEmployeeWorkstation(employeeIdentity, workstationIdentity);
        if (existing == null) return Optional.empty();
        if (existing.state() == nextState) return Optional.of(existing);
        return Optional.of(replace(existing, existing.withState(nextState, nextRevision())));
    }

    private Optional<WorkstationReservationRecord> roleForWorkstation(
            String workstationIdentity,
            WorkstationReservationRole role
    ) {
        return reservationsForWorkstation(workstationIdentity).stream()
                .filter(record -> record.role() == role)
                .findFirst();
    }

    private WorkstationReservationRecord matchingEmployeeWorkstation(String employeeIdentity, String workstationIdentity) {
        WorkstationReservationRecord existing = activeByEmployee.get(
                WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity"));
        String normalizedWorkstation = WorkstationReservationValidation.requireIdentity(
                workstationIdentity, "workstation identity");
        return existing != null && existing.workstationIdentity().equals(normalizedWorkstation) ? existing : null;
    }

    private WorkstationReservationRecord replace(
            WorkstationReservationRecord existing,
            WorkstationReservationRecord updated
    ) {
        unindex(existing);
        recordsById.put(updated.reservationId(), updated);
        index(updated);
        return updated;
    }

    private void rebuildActiveIndexes() {
        activeByEmployee.clear();
        activeByRequest.clear();
        activeByWorkstation.clear();
        recordsById.values().stream().filter(WorkstationReservationRecord::active).forEach(this::index);
    }

    private void index(WorkstationReservationRecord record) {
        if (!record.active()) return;
        if (activeByEmployee.putIfAbsent(record.employeeIdentity(), record) != null
                || activeByRequest.putIfAbsent(record.requestIdentity(), record) != null) {
            throw new IllegalArgumentException("Persisted reservation indexes contain duplicate active authority");
        }
        List<WorkstationReservationRecord> workstation = new ArrayList<>(
                activeByWorkstation.getOrDefault(record.workstationIdentity(), List.of()));
        workstation.add(record);
        activeByWorkstation.put(record.workstationIdentity(), List.copyOf(workstation));
    }

    private void unindex(WorkstationReservationRecord record) {
        if (!record.active()) return;
        activeByEmployee.remove(record.employeeIdentity(), record);
        activeByRequest.remove(record.requestIdentity(), record);
        List<WorkstationReservationRecord> remaining = activeByWorkstation
                .getOrDefault(record.workstationIdentity(), List.of())
                .stream()
                .filter(value -> !value.reservationId().equals(record.reservationId()))
                .toList();
        if (remaining.isEmpty()) activeByWorkstation.remove(record.workstationIdentity());
        else activeByWorkstation.put(record.workstationIdentity(), remaining);
    }

    private Collection<WorkstationReservationRecord> activeRecords() {
        return activeByEmployee.values();
    }

    private long nextRevision() {
        return ++ownerRevision;
    }

    private static boolean validHandlerLifecycle(WorkstationReservationRequest request) {
        String lifecycle = request.lifecycleEvidence();
        return switch (request.endpointScope().purpose()) {
            case SOURCE -> lifecycle.equals("requested")
                    || lifecycle.equals("source_bound")
                    || lifecycle.equals("source_withdraw_prepared");
            case DESTINATION -> lifecycle.equals("source_withdraw_committed")
                    || lifecycle.equals("in_transit")
                    || lifecycle.equals("destination_bound")
                    || lifecycle.equals("destination_deposit_prepared");
            case SOURCE_RETURN -> lifecycle.equals("cancellation_requested")
                    || lifecycle.equals("cancellation_return_prepared")
                    || lifecycle.equals("recovery_required")
                    || lifecycle.equals("source_withdraw_committed")
                    || lifecycle.equals("in_transit")
                    || lifecycle.equals("destination_bound")
                    || lifecycle.equals("destination_deposit_prepared");
            case NONE -> false;
        };
    }

    private static String locationIdentity(WorkstationReservationRequest request) {
        return request.dimensionIdentity() + "/" + request.workstationX() + "/"
                + request.workstationY() + "/" + request.workstationZ();
    }

    private static Comparator<WorkstationReservationRecord> roleOrder() {
        return Comparator.comparingInt(record -> switch (record.role()) {
            case MACHINE_OPERATOR -> 0;
            case MATERIAL_HANDLER -> 1;
            case LEGACY_EXCLUSIVE -> 2;
        });
    }
}
