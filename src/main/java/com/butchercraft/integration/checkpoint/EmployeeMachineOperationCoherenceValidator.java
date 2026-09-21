package com.butchercraft.integration.checkpoint;

import com.butchercraft.workstation.endpoint.WorkstationInstanceLifecycle;
import com.butchercraft.workstation.endpoint.WorkstationInstanceRegistry;
import com.butchercraft.workstation.reservation.WorkstationReservationDirectory;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationRole;
import com.butchercraft.world.EmployeeMachineOperationAssignmentService;
import com.butchercraft.world.execution.MachineRunChildState;
import com.butchercraft.world.execution.MachineRunIdentity;
import com.butchercraft.world.execution.MachineRunRecord;
import com.butchercraft.world.execution.MachineRunRegistry;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignment;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentDirectory;

import java.util.Objects;

/** Read-only cross-owner verification for Workforce machine-operation references. */
final class EmployeeMachineOperationCoherenceValidator {
    private EmployeeMachineOperationCoherenceValidator() {
    }

    static void validate(
            EmployeeMachineOperationAssignmentDirectory assignments,
            WorkstationReservationDirectory reservations,
            MachineRunRegistry runs,
            WorkstationInstanceRegistry instances
    ) {
        Objects.requireNonNull(assignments, "assignments");
        Objects.requireNonNull(reservations, "reservations");
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(instances, "instances");
        for (EmployeeMachineOperationAssignment assignment : assignments.assignments()) {
            validateWorkstation(assignment, instances);
            validateReservation(assignment, reservations);
            validateRun(assignment, runs);
        }
    }

    private static void validateWorkstation(
            EmployeeMachineOperationAssignment assignment,
            WorkstationInstanceRegistry instances
    ) {
        var instance = instances.find(assignment.workstation().instanceId()).orElseThrow(() ->
                new IllegalArgumentException("Machine-operation assignment references an unknown Workstation"));
        if (!instance.endpointKey().equals(assignment.workstation().endpointKey())
                || instance.generation() != assignment.workstation().generation()) {
            throw new IllegalArgumentException("Machine-operation assignment Workstation reference is incoherent");
        }
        if (assignment.active() && instance.lifecycle() != WorkstationInstanceLifecycle.ACTIVE) {
            throw new IllegalArgumentException("Active machine-operation assignment references an inactive Workstation");
        }
    }

    private static void validateReservation(
            EmployeeMachineOperationAssignment assignment,
            WorkstationReservationDirectory reservations
    ) {
        if (assignment.reservationId().isEmpty()) return;
        WorkstationReservationRecord reservation = reservations.records().stream()
                .filter(value -> value.reservationId().equals(assignment.reservationId().orElseThrow()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Machine-operation assignment references an unknown reservation"));
        if (!reservation.employeeIdentity().equals(assignment.employeeId().value())
                || reservation.role() != WorkstationReservationRole.MACHINE_OPERATOR
                || reservation.assignmentReference().filter(assignment.assignmentId().value()::equals).isEmpty()
                || !reservation.workstationIdentity().equals(assignment.workstation().instanceId().value())
                || reservation.workstationGeneration() != assignment.workstation().generation()) {
            throw new IllegalArgumentException("Machine-operation assignment reservation binding is incoherent");
        }
        if (assignment.state().requiresReservation() && !reservation.active()) {
            throw new IllegalArgumentException("Active machine-operation lifecycle references a terminal reservation");
        }
    }

    private static void validateRun(
            EmployeeMachineOperationAssignment assignment,
            MachineRunRegistry runs
    ) {
        if (assignment.runIdentity().isEmpty()) return;
        MachineRunRecord run = runs.find(new MachineRunIdentity(assignment.runIdentity().orElseThrow()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "Machine-operation assignment references an unknown Machine Run"));
        if (!run.workstationInstanceIdentity().equals(assignment.workstation().instanceId().value())
                || !run.operatingPolicyIdentity().equals(assignment.operatingPolicyIdentity())
                || !run.startEvidence().sourceOwner().equals(
                EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER)
                || !run.startEvidence().sourceRequestIdentity().equals(
                EmployeeMachineOperationAssignmentService.startRequestIdentity(
                        assignment.assignmentId().value()))) {
            throw new IllegalArgumentException("Machine-operation assignment Run binding is incoherent");
        }
        long completedChildren = run.terminalChildren().stream()
                .filter(child -> child.state() == MachineRunChildState.COMPLETED)
                .count();
        int completedQuantity = Math.toIntExact(Math.multiplyExact(
                completedChildren, assignment.inputQuantityPerChild()));
        if (assignment.completedQuantity() != completedQuantity
                || assignment.observedRunRevision() > run.revision()
                || assignment.observedChildSequence() >= run.nextChildSequence()) {
            throw new IllegalArgumentException("Machine-operation assignment Run freshness is incoherent");
        }
    }
}
