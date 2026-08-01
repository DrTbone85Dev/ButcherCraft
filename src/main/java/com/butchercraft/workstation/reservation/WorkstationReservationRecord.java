package com.butchercraft.workstation.reservation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record WorkstationReservationRecord(
        int schemaVersion,
        String workstationIdentity,
        String workstationType,
        String employeeIdentity,
        WorkstationReservationState state,
        long createdTick,
        OptionalLong expirationTick,
        Optional<String> invalidationReason,
        String dimensionIdentity,
        int workstationX,
        int workstationY,
        int workstationZ,
        int operatingX,
        int operatingY,
        int operatingZ,
        int anchorRadius
) {
    public WorkstationReservationRecord {
        schemaVersion = WorkstationReservationValidation.requireSchema(schemaVersion, "workstation reservation");
        workstationIdentity = WorkstationReservationValidation.requireIdentity(
                workstationIdentity,
                "workstation identity"
        );
        workstationType = WorkstationReservationValidation.requireToken(workstationType, "workstation type");
        employeeIdentity = WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity");
        state = Objects.requireNonNull(state, "state");
        if (createdTick < 0L) {
            throw new IllegalArgumentException("Workstation reservation created tick must not be negative");
        }
        expirationTick = Objects.requireNonNull(expirationTick, "expirationTick");
        if (expirationTick.isPresent() && expirationTick.getAsLong() < createdTick) {
            throw new IllegalArgumentException("Workstation reservation expiration cannot precede creation");
        }
        invalidationReason = Objects.requireNonNull(invalidationReason, "invalidationReason")
                .map(reason -> WorkstationReservationValidation.requireText(reason, "invalidation reason", 512));
        dimensionIdentity = WorkstationReservationValidation.requireIdentity(dimensionIdentity, "dimension identity");
        if (anchorRadius < 1 || anchorRadius > 16) {
            throw new IllegalArgumentException("Workstation reservation anchor radius must be 1-16: " + anchorRadius);
        }
        if (state.active() && invalidationReason.isPresent()) {
            throw new IllegalArgumentException("Active workstation reservations cannot carry invalidation reasons");
        }
    }

    public static WorkstationReservationRecord enRoute(WorkstationReservationRequest request) {
        Objects.requireNonNull(request, "request");
        return new WorkstationReservationRecord(
                WorkstationReservationSchema.CURRENT_VERSION,
                request.workstationIdentity(),
                request.workstationType(),
                request.employeeIdentity(),
                WorkstationReservationState.EMPLOYEE_EN_ROUTE,
                request.createdTick(),
                OptionalLong.empty(),
                Optional.empty(),
                request.dimensionIdentity(),
                request.workstationX(),
                request.workstationY(),
                request.workstationZ(),
                request.operatingX(),
                request.operatingY(),
                request.operatingZ(),
                request.anchorRadius()
        );
    }

    public boolean active() {
        return state.active();
    }

    public WorkstationReservationRecord withState(WorkstationReservationState nextState) {
        return new WorkstationReservationRecord(
                schemaVersion,
                workstationIdentity,
                workstationType,
                employeeIdentity,
                nextState,
                createdTick,
                expirationTick,
                Optional.empty(),
                dimensionIdentity,
                workstationX,
                workstationY,
                workstationZ,
                operatingX,
                operatingY,
                operatingZ,
                anchorRadius
        );
    }

    public WorkstationReservationRecord withOperatingPosition(int x, int y, int z) {
        return new WorkstationReservationRecord(
                schemaVersion,
                workstationIdentity,
                workstationType,
                employeeIdentity,
                state,
                createdTick,
                expirationTick,
                invalidationReason,
                dimensionIdentity,
                workstationX,
                workstationY,
                workstationZ,
                x,
                y,
                z,
                anchorRadius
        );
    }

    public WorkstationReservationRecord released(String reason) {
        return terminal(WorkstationReservationState.RELEASED, reason);
    }

    public WorkstationReservationRecord invalidated(String reason) {
        return terminal(WorkstationReservationState.INVALIDATED, reason);
    }

    private WorkstationReservationRecord terminal(WorkstationReservationState terminalState, String reason) {
        return new WorkstationReservationRecord(
                schemaVersion,
                workstationIdentity,
                workstationType,
                employeeIdentity,
                terminalState,
                createdTick,
                expirationTick,
                Optional.ofNullable(reason),
                dimensionIdentity,
                workstationX,
                workstationY,
                workstationZ,
                operatingX,
                operatingY,
                operatingZ,
                anchorRadius
        );
    }
}
