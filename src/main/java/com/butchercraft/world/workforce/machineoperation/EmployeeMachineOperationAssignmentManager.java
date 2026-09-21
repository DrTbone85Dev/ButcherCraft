package com.butchercraft.world.workforce.machineoperation;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.reservation.WorkstationReservationId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.workforce.employee.EmployeeId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class EmployeeMachineOperationAssignmentManager {
    private long ownerRevision;
    private long nextAssignmentSequence;
    private final Map<EmployeeMachineOperationAssignmentId, EmployeeMachineOperationAssignment> assignments =
            new LinkedHashMap<>();

    public EmployeeMachineOperationAssignmentManager(EmployeeMachineOperationAssignmentDirectory directory) {
        EmployeeMachineOperationAssignmentDirectory value = Objects.requireNonNull(directory, "directory");
        ownerRevision = value.ownerRevision();
        nextAssignmentSequence = value.nextAssignmentSequence();
        value.assignments().forEach(assignment -> assignments.put(assignment.assignmentId(), assignment));
    }

    public static EmployeeMachineOperationAssignmentManager empty() {
        return new EmployeeMachineOperationAssignmentManager(EmployeeMachineOperationAssignmentDirectory.empty());
    }

    public synchronized CreateResult createOrObserve(
            WorldIdentityRootIdentity worldIdentity,
            EmployeeId employeeId,
            WorkstationEndpointReference workstation,
            String machineType,
            String policyIdentity,
            String operationIdentity,
            String inputMaterialIdentity,
            int inputQuantityPerChild,
            int targetQuantity,
            long tick
    ) {
        Optional<EmployeeMachineOperationAssignment> active = activeFor(employeeId);
        if (active.isPresent()) {
            EmployeeMachineOperationAssignment existing = active.orElseThrow();
            if (existing.sameRequest(employeeId, workstation, machineType, operationIdentity, targetQuantity)) {
                return CreateResult.observed(existing);
            }
            return CreateResult.conflict(existing);
        }
        long sequence = nextAssignmentSequence;
        long revision = Math.addExact(ownerRevision, 1L);
        EmployeeMachineOperationAssignment created = EmployeeMachineOperationAssignment.create(
                worldIdentity, sequence, employeeId, workstation, machineType, policyIdentity, operationIdentity,
                inputMaterialIdentity, inputQuantityPerChild, targetQuantity, revision, tick);
        assignments.put(created.assignmentId(), created);
        ownerRevision = revision;
        nextAssignmentSequence = Math.addExact(sequence, 1L);
        return CreateResult.created(created);
    }

    public synchronized EmployeeMachineOperationAssignment publish(
            EmployeeMachineOperationAssignmentId assignmentId,
            EmployeeMachineOperationAssignmentState state,
            int completedQuantity,
            Optional<String> runIdentity,
            Optional<WorkstationReservationId> reservationId,
            Optional<String> pendingSupplyTransferIdentity,
            long observedRunRevision,
            long observedChildSequence,
            Optional<EmployeeMachineOperationFailure> failure,
            long tick
    ) {
        EmployeeMachineOperationAssignment existing = assignments.get(Objects.requireNonNull(assignmentId));
        if (existing == null) {
            throw new IllegalArgumentException("Unknown employee machine-operation assignment: "
                    + assignmentId.value());
        }
        if (existing.state() == state
                && existing.completedQuantity() == completedQuantity
                && existing.runIdentity().equals(runIdentity)
                && existing.reservationId().equals(reservationId)
                && existing.pendingSupplyTransferIdentity().equals(pendingSupplyTransferIdentity)
                && existing.observedRunRevision() == observedRunRevision
                && existing.observedChildSequence() == observedChildSequence
                && existing.failure().equals(failure)) {
            return existing;
        }
        long revision = Math.addExact(ownerRevision, 1L);
        EmployeeMachineOperationAssignment updated = existing.evolve(
                state, completedQuantity, runIdentity, reservationId, pendingSupplyTransferIdentity,
                observedRunRevision,
                observedChildSequence, failure, revision, tick);
        assignments.put(updated.assignmentId(), updated);
        ownerRevision = revision;
        return updated;
    }

    public synchronized Optional<EmployeeMachineOperationAssignment> find(
            EmployeeMachineOperationAssignmentId assignmentId
    ) {
        return Optional.ofNullable(assignments.get(Objects.requireNonNull(assignmentId)));
    }

    public synchronized Optional<EmployeeMachineOperationAssignment> activeFor(EmployeeId employeeId) {
        return assignments.values().stream().filter(EmployeeMachineOperationAssignment::active)
                .filter(assignment -> assignment.employeeId().equals(employeeId)).findFirst();
    }

    public synchronized Optional<EmployeeMachineOperationAssignment> activeForWorkstation(String instanceIdentity) {
        return assignments.values().stream().filter(EmployeeMachineOperationAssignment::active)
                .filter(assignment -> assignment.workstation().instanceId().value().equals(instanceIdentity))
                .findFirst();
    }

    public synchronized Optional<EmployeeMachineOperationAssignment> activeForRun(String runIdentity) {
        return assignments.values().stream().filter(EmployeeMachineOperationAssignment::active)
                .filter(assignment -> assignment.runIdentity().filter(runIdentity::equals).isPresent())
                .findFirst();
    }

    public synchronized Optional<EmployeeMachineOperationAssignment> latestFor(EmployeeId employeeId) {
        return assignments.values().stream().filter(assignment -> assignment.employeeId().equals(employeeId))
                .max((left, right) -> Long.compare(left.revision(), right.revision()));
    }

    public synchronized List<EmployeeMachineOperationAssignment> assignments() {
        return assignments.values().stream().sorted().toList();
    }

    public synchronized EmployeeMachineOperationAssignmentDirectory directory() {
        return new EmployeeMachineOperationAssignmentDirectory(
                EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION, ownerRevision, nextAssignmentSequence,
                new ArrayList<>(assignments.values()));
    }

    public record CreateResult(CreateStatus status, EmployeeMachineOperationAssignment assignment) {
        public CreateResult {
            status = Objects.requireNonNull(status, "status");
            assignment = Objects.requireNonNull(assignment, "assignment");
        }

        static CreateResult created(EmployeeMachineOperationAssignment assignment) {
            return new CreateResult(CreateStatus.CREATED, assignment);
        }

        static CreateResult observed(EmployeeMachineOperationAssignment assignment) {
            return new CreateResult(CreateStatus.OBSERVED, assignment);
        }

        static CreateResult conflict(EmployeeMachineOperationAssignment assignment) {
            return new CreateResult(CreateStatus.CONFLICT, assignment);
        }
    }

    public enum CreateStatus {
        CREATED,
        OBSERVED,
        CONFLICT
    }
}
