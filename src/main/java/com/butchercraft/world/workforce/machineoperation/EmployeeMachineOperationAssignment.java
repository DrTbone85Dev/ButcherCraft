package com.butchercraft.world.workforce.machineoperation;

import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointReference;
import com.butchercraft.workstation.reservation.WorkstationReservationId;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.workforce.employee.EmployeeId;

import java.util.Objects;
import java.util.Optional;

public record EmployeeMachineOperationAssignment(
        int schemaVersion,
        EmployeeMachineOperationAssignmentId assignmentId,
        long assignmentSequence,
        WorldIdentityRootIdentity worldIdentity,
        EmployeeId employeeId,
        WorkstationEndpointReference workstation,
        String machineType,
        String operatingPolicyIdentity,
        String operationIdentity,
        String inputMaterialIdentity,
        int inputQuantityPerChild,
        int targetQuantity,
        int completedQuantity,
        Optional<String> runIdentity,
        Optional<WorkstationReservationId> reservationId,
        Optional<String> pendingSupplyTransferIdentity,
        EmployeeMachineOperationAssignmentState state,
        long revision,
        long createdTick,
        long lastUpdatedTick,
        long observedRunRevision,
        long observedChildSequence,
        Optional<EmployeeMachineOperationFailure> failure,
        String configurationIdentity,
        String contentDigest
) implements Comparable<EmployeeMachineOperationAssignment> {
    public EmployeeMachineOperationAssignment {
        if (schemaVersion != EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported employee machine-operation assignment schema: "
                    + schemaVersion);
        }
        assignmentId = Objects.requireNonNull(assignmentId, "assignmentId");
        if (assignmentSequence <= 0L) throw new IllegalArgumentException("Assignment sequence must be positive");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        employeeId = Objects.requireNonNull(employeeId, "employeeId");
        workstation = Objects.requireNonNull(workstation, "workstation");
        machineType = requireText(machineType, "machineType");
        operatingPolicyIdentity = EmployeeMachineOperationDigest.requireIdentity(
                operatingPolicyIdentity, "operatingPolicyIdentity");
        operationIdentity = EmployeeMachineOperationDigest.requireIdentity(operationIdentity, "operationIdentity");
        inputMaterialIdentity = EmployeeMachineOperationDigest.requireIdentity(
                inputMaterialIdentity, "inputMaterialIdentity");
        if (inputQuantityPerChild <= 0 || targetQuantity <= 0 || completedQuantity < 0
                || completedQuantity > targetQuantity) {
            throw new IllegalArgumentException("Machine-operation quantities are invalid");
        }
        runIdentity = normalizeIdentity(runIdentity, "runIdentity");
        reservationId = Objects.requireNonNull(reservationId, "reservationId");
        pendingSupplyTransferIdentity = normalizeIdentity(
                pendingSupplyTransferIdentity, "pendingSupplyTransferIdentity");
        state = Objects.requireNonNull(state, "state");
        if (revision <= 0L || createdTick < 0L || lastUpdatedTick < createdTick) {
            throw new IllegalArgumentException("Machine-operation assignment revisions or ticks are invalid");
        }
        if (observedRunRevision < 0L || observedChildSequence < 0L) {
            throw new IllegalArgumentException("Observed Run freshness must not be negative");
        }
        failure = Objects.requireNonNull(failure, "failure");
        configurationIdentity = EmployeeMachineOperationDigest.requireIdentity(
                configurationIdentity, "configurationIdentity");
        contentDigest = Objects.requireNonNull(contentDigest, "contentDigest");
        if (!assignmentId.equals(EmployeeMachineOperationAssignmentId.create(
                worldIdentity, assignmentSequence, employeeId, workstation, configurationIdentity))) {
            throw new IllegalArgumentException("Employee machine-operation assignment identity is not canonical");
        }
        if (state.requiresRunReference() && runIdentity.isEmpty()) {
            throw new IllegalArgumentException("Machine-operation lifecycle requires an exact Run reference");
        }
        if (state.requiresReservation() && reservationId.isEmpty()) {
            throw new IllegalArgumentException("Machine-operation lifecycle requires an exact reservation reference");
        }
        if ((state == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED
                || state == EmployeeMachineOperationAssignmentState.FAILED
                || state == EmployeeMachineOperationAssignmentState.INTERRUPTED) && failure.isEmpty()) {
            throw new IllegalArgumentException("Failure, interruption, and recovery states require a typed reason");
        }
        String expectedDigest = calculateDigest(
                assignmentId, assignmentSequence, employeeId, workstation, machineType, operatingPolicyIdentity,
                operationIdentity, inputMaterialIdentity, inputQuantityPerChild, targetQuantity, completedQuantity,
                runIdentity, reservationId, pendingSupplyTransferIdentity, state, revision, createdTick,
                lastUpdatedTick, observedRunRevision,
                observedChildSequence, failure, configurationIdentity);
        if (!expectedDigest.equals(contentDigest)) {
            throw new IllegalArgumentException("Employee machine-operation assignment digest is not canonical");
        }
    }

    public static EmployeeMachineOperationAssignment create(
            WorldIdentityRootIdentity worldIdentity,
            long assignmentSequence,
            EmployeeId employeeId,
            WorkstationEndpointReference workstation,
            String machineType,
            String operatingPolicyIdentity,
            String operationIdentity,
            String inputMaterialIdentity,
            int inputQuantityPerChild,
            int targetQuantity,
            long revision,
            long createdTick
    ) {
        String configuration = EmployeeMachineOperationAssignmentSchema.CONFIGURATION_IDENTITY;
        EmployeeMachineOperationAssignmentId id = EmployeeMachineOperationAssignmentId.create(
                worldIdentity, assignmentSequence, employeeId, workstation, configuration);
        return build(id, assignmentSequence, worldIdentity, employeeId, workstation, machineType,
                operatingPolicyIdentity, operationIdentity, inputMaterialIdentity, inputQuantityPerChild,
                targetQuantity, 0, Optional.empty(), Optional.empty(),
                Optional.empty(),
                EmployeeMachineOperationAssignmentState.CREATED, revision, createdTick, createdTick,
                0L, 0L, Optional.empty(), configuration);
    }

    public EmployeeMachineOperationAssignment evolve(
            EmployeeMachineOperationAssignmentState nextState,
            int nextCompletedQuantity,
            Optional<String> nextRunIdentity,
            Optional<WorkstationReservationId> nextReservationId,
            Optional<String> nextPendingSupplyTransferIdentity,
            long nextObservedRunRevision,
            long nextObservedChildSequence,
            Optional<EmployeeMachineOperationFailure> nextFailure,
            long nextRevision,
            long tick
    ) {
        if (!state.canTransitionTo(nextState)) {
            throw new IllegalArgumentException("Illegal employee machine-operation transition: " + state
                    + " -> " + nextState);
        }
        if (nextCompletedQuantity < completedQuantity) {
            throw new IllegalArgumentException("Completed machine-operation quantity cannot regress");
        }
        if (nextObservedRunRevision < observedRunRevision || nextObservedChildSequence < observedChildSequence) {
            throw new IllegalArgumentException("Machine-operation observation freshness cannot regress");
        }
        if (nextRevision <= revision || tick < lastUpdatedTick) {
            throw new IllegalArgumentException("Machine-operation assignment publication must advance");
        }
        return build(assignmentId, assignmentSequence, worldIdentity, employeeId, workstation, machineType,
                operatingPolicyIdentity, operationIdentity, inputMaterialIdentity, inputQuantityPerChild,
                targetQuantity, nextCompletedQuantity, nextRunIdentity, nextReservationId,
                nextPendingSupplyTransferIdentity, nextState,
                nextRevision, createdTick, tick, nextObservedRunRevision, nextObservedChildSequence,
                nextFailure, configurationIdentity);
    }

    public boolean active() {
        return !state.terminal();
    }

    public int remainingQuantity() {
        return targetQuantity - completedQuantity;
    }

    public boolean isConsequenceFreeReplacementCancellationCandidate() {
        return state == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED
                && failure.filter(value -> value.code()
                        == EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED).isPresent()
                && completedQuantity == 0
                && runIdentity.isEmpty()
                && pendingSupplyTransferIdentity.isEmpty()
                && observedRunRevision == 0L
                && observedChildSequence == 0L;
    }

    public boolean sameRequest(
            EmployeeId requestedEmployee,
            WorkstationEndpointReference requestedWorkstation,
            String requestedMachineType,
            String requestedOperation,
            int requestedTarget
    ) {
        return employeeId.equals(requestedEmployee)
                && workstation.equals(requestedWorkstation)
                && machineType.equals(requestedMachineType)
                && operationIdentity.equals(requestedOperation)
                && targetQuantity == requestedTarget;
    }

    private static EmployeeMachineOperationAssignment build(
            EmployeeMachineOperationAssignmentId id,
            long sequence,
            WorldIdentityRootIdentity worldIdentity,
            EmployeeId employeeId,
            WorkstationEndpointReference workstation,
            String machineType,
            String policyIdentity,
            String operationIdentity,
            String inputMaterialIdentity,
            int inputQuantityPerChild,
            int targetQuantity,
            int completedQuantity,
            Optional<String> runIdentity,
            Optional<WorkstationReservationId> reservationId,
            Optional<String> pendingSupplyTransferIdentity,
            EmployeeMachineOperationAssignmentState state,
            long revision,
            long createdTick,
            long updatedTick,
            long observedRunRevision,
            long observedChildSequence,
            Optional<EmployeeMachineOperationFailure> failure,
            String configurationIdentity
    ) {
        String digest = calculateDigest(id, sequence, employeeId, workstation, machineType, policyIdentity,
                operationIdentity, inputMaterialIdentity, inputQuantityPerChild, targetQuantity, completedQuantity,
                runIdentity, reservationId, pendingSupplyTransferIdentity, state, revision, createdTick, updatedTick,
                observedRunRevision,
                observedChildSequence, failure, configurationIdentity);
        return new EmployeeMachineOperationAssignment(
                EmployeeMachineOperationAssignmentSchema.CURRENT_VERSION, id, sequence, worldIdentity, employeeId,
                workstation, machineType, policyIdentity, operationIdentity, inputMaterialIdentity,
                inputQuantityPerChild, targetQuantity, completedQuantity, runIdentity, reservationId,
                pendingSupplyTransferIdentity, state,
                revision, createdTick, updatedTick, observedRunRevision, observedChildSequence, failure,
                configurationIdentity, digest);
    }

    private static String calculateDigest(
            EmployeeMachineOperationAssignmentId id,
            long sequence,
            EmployeeId employeeId,
            WorkstationEndpointReference workstation,
            String machineType,
            String policyIdentity,
            String operationIdentity,
            String inputMaterialIdentity,
            int inputQuantityPerChild,
            int targetQuantity,
            int completedQuantity,
            Optional<String> runIdentity,
            Optional<WorkstationReservationId> reservationId,
            Optional<String> pendingSupplyTransferIdentity,
            EmployeeMachineOperationAssignmentState state,
            long revision,
            long createdTick,
            long updatedTick,
            long observedRunRevision,
            long observedChildSequence,
            Optional<EmployeeMachineOperationFailure> failure,
            String configurationIdentity
    ) {
        return EmployeeMachineOperationDigest.sha256(String.join("\n",
                id.value(), Long.toString(sequence), employeeId.value(), workstation.instanceId().value(),
                workstation.endpointKey().canonicalValue(), Long.toString(workstation.generation()), machineType,
                policyIdentity, operationIdentity, inputMaterialIdentity, Integer.toString(inputQuantityPerChild),
                Integer.toString(targetQuantity), Integer.toString(completedQuantity), runIdentity.orElse("none"),
                reservationId.map(WorkstationReservationId::value).orElse("none"),
                pendingSupplyTransferIdentity.orElse("none"), state.name(),
                Long.toString(revision), Long.toString(createdTick), Long.toString(updatedTick),
                Long.toString(observedRunRevision), Long.toString(observedChildSequence),
                failure.map(value -> value.code().serializedName()).orElse("none"),
                failure.map(EmployeeMachineOperationFailure::detail).orElse("none"), configurationIdentity));
    }

    private static String requireText(String value, String label) {
        String normalized = Objects.requireNonNull(value, label).strip();
        if (normalized.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return normalized;
    }

    private static Optional<String> normalizeIdentity(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label)
                .map(identity -> EmployeeMachineOperationDigest.requireIdentity(identity, label));
    }

    @Override
    public int compareTo(EmployeeMachineOperationAssignment other) {
        return assignmentId.compareTo(other.assignmentId);
    }
}
