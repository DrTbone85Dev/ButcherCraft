package com.butchercraft.workstation.reservation;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record WorkstationReservationRecord(
        int schemaVersion,
        WorkstationReservationId reservationId,
        long sequence,
        WorldIdentityRootIdentity worldIdentity,
        String requestIdentity,
        String workstationIdentity,
        boolean exactWorkstationInstance,
        long workstationGeneration,
        String workstationType,
        String employeeIdentity,
        WorkstationReservationRole role,
        Optional<String> assignmentReference,
        Optional<String> transferReference,
        WorkstationReservationEndpointScope endpointScope,
        String lifecycleEvidence,
        long lifecycleEvidenceRevision,
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
        int anchorRadius,
        long creationRevision,
        long lastUpdateRevision,
        String configurationIdentity
) implements Comparable<WorkstationReservationRecord> {
    public WorkstationReservationRecord {
        schemaVersion = WorkstationReservationValidation.requireSchema(schemaVersion, "workstation reservation");
        reservationId = Objects.requireNonNull(reservationId, "reservationId");
        if (sequence <= 0L) throw new IllegalArgumentException("Reservation sequence must be positive");
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        requestIdentity = WorkstationReservationValidation.requireIdentity(requestIdentity, "request identity");
        workstationIdentity = WorkstationReservationValidation.requireIdentity(workstationIdentity, "workstation identity");
        if (exactWorkstationInstance && workstationGeneration <= 0L) {
            throw new IllegalArgumentException("Exact Workstation Instance requires a positive generation");
        }
        if (!exactWorkstationInstance && role != WorkstationReservationRole.LEGACY_EXCLUSIVE) {
            throw new IllegalArgumentException("Modern reservation roles require exact Workstation Instance identity");
        }
        workstationType = WorkstationReservationValidation.requireToken(workstationType, "workstation type");
        employeeIdentity = WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity");
        role = Objects.requireNonNull(role, "role");
        assignmentReference = normalizeIdentity(assignmentReference, "assignment reference");
        transferReference = normalizeIdentity(transferReference, "transfer reference");
        endpointScope = Objects.requireNonNull(endpointScope, "endpointScope");
        lifecycleEvidence = WorkstationReservationValidation.requireToken(lifecycleEvidence, "lifecycle evidence");
        if (lifecycleEvidenceRevision < 0L) throw new IllegalArgumentException("Lifecycle revision must not be negative");
        state = Objects.requireNonNull(state, "state");
        if (createdTick < 0L) throw new IllegalArgumentException("Created tick must not be negative");
        expirationTick = Objects.requireNonNull(expirationTick, "expirationTick");
        if (expirationTick.isPresent() && expirationTick.getAsLong() < createdTick) {
            throw new IllegalArgumentException("Expiration cannot precede creation");
        }
        invalidationReason = Objects.requireNonNull(invalidationReason, "invalidationReason")
                .map(reason -> WorkstationReservationValidation.requireText(reason, "invalidation reason", 512));
        dimensionIdentity = WorkstationReservationValidation.requireIdentity(dimensionIdentity, "dimension identity");
        if (anchorRadius < 1 || anchorRadius > 16) throw new IllegalArgumentException("Anchor radius must be 1-16");
        if (creationRevision <= 0L || lastUpdateRevision < creationRevision) {
            throw new IllegalArgumentException("Reservation revisions must be positive and monotonic");
        }
        configurationIdentity = WorkstationReservationValidation.requireIdentity(
                configurationIdentity,
                "configuration identity"
        );
        if (!configurationIdentity.equals(WorkstationReservationSchema.CONFIGURATION_IDENTITY)) {
            throw new IllegalArgumentException("Unsupported workstation reservation configuration identity");
        }
        if (state.active() == invalidationReason.isPresent()) {
            throw new IllegalArgumentException("Only terminal reservations require a terminal reason");
        }
        validateRoleBinding(role, assignmentReference, transferReference, endpointScope);
        if (exactWorkstationInstance) {
            WorkstationReservationRequest canonicalRequest = new WorkstationReservationRequest(
                    worldIdentity, requestIdentity, workstationIdentity, workstationGeneration, workstationType,
                    employeeIdentity, role, assignmentReference, transferReference, endpointScope, lifecycleEvidence,
                    lifecycleEvidenceRevision, createdTick, dimensionIdentity,
                    workstationX, workstationY, workstationZ, operatingX, operatingY, operatingZ,
                    anchorRadius, configurationIdentity
            );
            WorkstationReservationId expected = WorkstationReservationId.create(
                    worldIdentity, sequence, canonicalRequest);
            if (!expected.equals(reservationId)) {
                throw new IllegalArgumentException("Reservation identity does not match canonical inputs");
            }
        }
    }

    public static WorkstationReservationRecord acquire(
            WorkstationReservationRequest request,
            long sequence,
            long ownerRevision
    ) {
        Objects.requireNonNull(request, "request");
        return new WorkstationReservationRecord(
                WorkstationReservationSchema.CURRENT_VERSION,
                WorkstationReservationId.create(request.worldIdentity(), sequence, request),
                sequence,
                request.worldIdentity(),
                request.requestIdentity(),
                request.workstationIdentity(),
                true,
                request.workstationGeneration(),
                request.workstationType(),
                request.employeeIdentity(),
                request.role(),
                request.assignmentReference(),
                request.transferReference(),
                request.endpointScope(),
                request.lifecycleEvidence(),
                request.lifecycleEvidenceRevision(),
                WorkstationReservationState.EMPLOYEE_EN_ROUTE,
                request.createdTick(),
                OptionalLong.empty(),
                Optional.empty(),
                request.dimensionIdentity(),
                request.workstationX(), request.workstationY(), request.workstationZ(),
                request.operatingX(), request.operatingY(), request.operatingZ(),
                request.anchorRadius(),
                ownerRevision,
                ownerRevision,
                request.configurationIdentity()
        );
    }

    public static WorkstationReservationRecord migrateLegacy(
            WorldIdentityRootIdentity worldIdentity,
            long sequence,
            long ownerRevision,
            String legacyWorkstationIdentity,
            String workstationType,
            String employeeIdentity,
            WorkstationReservationState state,
            long createdTick,
            OptionalLong expirationTick,
            Optional<String> terminalReason,
            String dimensionIdentity,
            int workstationX,
            int workstationY,
            int workstationZ,
            int operatingX,
            int operatingY,
            int operatingZ,
            int anchorRadius,
            Optional<WorkstationReservationMigrationEvidence> evidence
    ) {
        if (evidence.isPresent()) {
            WorkstationReservationMigrationEvidence proof = evidence.orElseThrow();
            WorkstationReservationRequest request = WorkstationReservationRequest.materialHandler(
                    worldIdentity,
                    proof.requestIdentity(),
                    proof.workstationIdentity(),
                    proof.workstationGeneration(),
                    workstationType,
                    employeeIdentity,
                    proof.assignmentReference(),
                    proof.transferReference(),
                    proof.endpointScope(),
                    proof.lifecycleEvidence(),
                    proof.lifecycleEvidenceRevision(),
                    createdTick,
                    dimensionIdentity,
                    workstationX, workstationY, workstationZ,
                    operatingX, operatingY, operatingZ,
                    anchorRadius
            );
            WorkstationReservationRecord acquired = acquire(request, sequence, ownerRevision);
            return acquired.copy(state, operatingX, operatingY, operatingZ, ownerRevision, terminalReason);
        }
        String requestIdentity = "butchercraft:legacy_workstation_reservation_request/v1/" + sequence;
        return new WorkstationReservationRecord(
                WorkstationReservationSchema.CURRENT_VERSION,
                WorkstationReservationId.createLegacy(
                        worldIdentity, sequence, employeeIdentity, legacyWorkstationIdentity, createdTick),
                sequence,
                worldIdentity,
                requestIdentity,
                legacyWorkstationIdentity,
                false,
                0L,
                workstationType,
                employeeIdentity,
                WorkstationReservationRole.LEGACY_EXCLUSIVE,
                Optional.empty(),
                Optional.empty(),
                WorkstationReservationEndpointScope.none(),
                "schema_1_unproven",
                0L,
                state,
                createdTick,
                expirationTick,
                state.active() ? Optional.empty() : terminalReason.or(() -> Optional.of("schema-1 terminal state")),
                dimensionIdentity,
                workstationX, workstationY, workstationZ,
                operatingX, operatingY, operatingZ,
                anchorRadius,
                ownerRevision,
                ownerRevision,
                WorkstationReservationSchema.CONFIGURATION_IDENTITY
        );
    }

    public boolean active() {
        return state.active();
    }

    public String locationIdentity() {
        return dimensionIdentity + "/" + workstationX + "/" + workstationY + "/" + workstationZ;
    }

    public WorkstationReservationRecord withState(WorkstationReservationState nextState, long ownerRevision) {
        return copy(nextState, operatingX, operatingY, operatingZ, ownerRevision, Optional.empty());
    }

    public WorkstationReservationRecord withOperatingPosition(int x, int y, int z, long ownerRevision) {
        return copy(state, x, y, z, ownerRevision, invalidationReason);
    }

    public WorkstationReservationRecord released(String reason, long ownerRevision) {
        return copy(WorkstationReservationState.RELEASED, operatingX, operatingY, operatingZ, ownerRevision,
                Optional.ofNullable(reason));
    }

    public WorkstationReservationRecord invalidated(String reason, long ownerRevision) {
        return copy(WorkstationReservationState.INVALIDATED, operatingX, operatingY, operatingZ, ownerRevision,
                Optional.ofNullable(reason));
    }

    private WorkstationReservationRecord copy(
            WorkstationReservationState nextState,
            int nextOperatingX,
            int nextOperatingY,
            int nextOperatingZ,
            long ownerRevision,
            Optional<String> reason
    ) {
        if (ownerRevision < lastUpdateRevision) {
            throw new IllegalArgumentException("Reservation owner revision cannot move backward");
        }
        Optional<String> normalizedReason = nextState.active() ? Optional.empty() : reason;
        return new WorkstationReservationRecord(
                schemaVersion, reservationId, sequence, worldIdentity, requestIdentity, workstationIdentity,
                exactWorkstationInstance, workstationGeneration, workstationType, employeeIdentity, role,
                assignmentReference, transferReference, endpointScope, lifecycleEvidence, lifecycleEvidenceRevision,
                nextState, createdTick, expirationTick, normalizedReason, dimensionIdentity,
                workstationX, workstationY, workstationZ,
                nextOperatingX, nextOperatingY, nextOperatingZ, anchorRadius,
                creationRevision, ownerRevision, configurationIdentity
        );
    }

    private static void validateRoleBinding(
            WorkstationReservationRole role,
            Optional<String> assignmentReference,
            Optional<String> transferReference,
            WorkstationReservationEndpointScope endpointScope
    ) {
        if (role == WorkstationReservationRole.MATERIAL_HANDLER) {
            if (assignmentReference.isEmpty() || transferReference.isEmpty()
                    || endpointScope.purpose() == WorkstationReservationEndpointPurpose.NONE) {
                throw new IllegalArgumentException("MATERIAL_HANDLER requires exact transfer-bound endpoint evidence");
            }
        } else if (transferReference.isPresent() || !endpointScope.equals(WorkstationReservationEndpointScope.none())) {
            throw new IllegalArgumentException(role + " cannot carry Material Handling endpoint authority");
        }
    }

    private static Optional<String> normalizeIdentity(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(identity ->
                WorkstationReservationValidation.requireIdentity(identity, label));
    }

    @Override
    public int compareTo(WorkstationReservationRecord other) {
        return reservationId.compareTo(other.reservationId);
    }
}
