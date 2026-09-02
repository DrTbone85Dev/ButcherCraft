package com.butchercraft.workstation.reservation;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Objects;
import java.util.Optional;

public record WorkstationReservationRequest(
        WorldIdentityRootIdentity worldIdentity,
        String requestIdentity,
        String workstationIdentity,
        long workstationGeneration,
        String workstationType,
        String employeeIdentity,
        WorkstationReservationRole role,
        Optional<String> assignmentReference,
        Optional<String> transferReference,
        WorkstationReservationEndpointScope endpointScope,
        String lifecycleEvidence,
        long lifecycleEvidenceRevision,
        long createdTick,
        String dimensionIdentity,
        int workstationX,
        int workstationY,
        int workstationZ,
        int operatingX,
        int operatingY,
        int operatingZ,
        int anchorRadius,
        String configurationIdentity
) {
    public WorkstationReservationRequest {
        worldIdentity = Objects.requireNonNull(worldIdentity, "worldIdentity");
        requestIdentity = WorkstationReservationValidation.requireIdentity(requestIdentity, "request identity");
        workstationIdentity = WorkstationReservationValidation.requireIdentity(
                workstationIdentity,
                "workstation identity"
        );
        if (workstationGeneration <= 0L) {
            throw new IllegalArgumentException("Workstation generation must be positive");
        }
        workstationType = WorkstationReservationValidation.requireToken(workstationType, "workstation type");
        employeeIdentity = WorkstationReservationValidation.requireIdentity(employeeIdentity, "employee identity");
        role = Objects.requireNonNull(role, "role");
        if (role == WorkstationReservationRole.LEGACY_EXCLUSIVE) {
            throw new IllegalArgumentException("LEGACY_EXCLUSIVE may only be created by schema-1 migration");
        }
        assignmentReference = normalizeIdentity(assignmentReference, "assignment reference");
        transferReference = normalizeIdentity(transferReference, "transfer reference");
        endpointScope = Objects.requireNonNull(endpointScope, "endpointScope");
        lifecycleEvidence = WorkstationReservationValidation.requireToken(lifecycleEvidence, "lifecycle evidence");
        if (lifecycleEvidenceRevision < 0L) {
            throw new IllegalArgumentException("Lifecycle evidence revision must not be negative");
        }
        if (createdTick < 0L) {
            throw new IllegalArgumentException("Workstation reservation created tick must not be negative");
        }
        dimensionIdentity = WorkstationReservationValidation.requireIdentity(dimensionIdentity, "dimension identity");
        if (anchorRadius < 1 || anchorRadius > 16) {
            throw new IllegalArgumentException("Workstation reservation anchor radius must be 1-16: " + anchorRadius);
        }
        configurationIdentity = WorkstationReservationValidation.requireIdentity(
                configurationIdentity,
                "configuration identity"
        );
        validateRoleBinding(role, assignmentReference, transferReference, endpointScope);
        String canonicalIdentity = canonicalRequestIdentity(
                worldIdentity,
                workstationIdentity,
                employeeIdentity,
                role,
                assignmentReference,
                transferReference,
                endpointScope
        );
        if (!canonicalIdentity.equals(requestIdentity)) {
            throw new IllegalArgumentException("Reservation request identity is not canonical");
        }
    }

    public static WorkstationReservationRequest machineOperator(
            WorldIdentityRootIdentity worldIdentity,
            String requestIdentity,
            String workstationIdentity,
            long workstationGeneration,
            String workstationType,
            String employeeIdentity,
            Optional<String> assignmentReference,
            long createdTick,
            String dimensionIdentity,
            int workstationX,
            int workstationY,
            int workstationZ,
            int operatingX,
            int operatingY,
            int operatingZ,
            int anchorRadius
    ) {
        return new WorkstationReservationRequest(
                worldIdentity,
                requestIdentity,
                workstationIdentity,
                workstationGeneration,
                workstationType,
                employeeIdentity,
                WorkstationReservationRole.MACHINE_OPERATOR,
                assignmentReference,
                Optional.empty(),
                WorkstationReservationEndpointScope.none(),
                "operator_assignment_active",
                0L,
                createdTick,
                dimensionIdentity,
                workstationX,
                workstationY,
                workstationZ,
                operatingX,
                operatingY,
                operatingZ,
                anchorRadius,
                WorkstationReservationSchema.CONFIGURATION_IDENTITY
        );
    }

    public static WorkstationReservationRequest materialHandler(
            WorldIdentityRootIdentity worldIdentity,
            String requestIdentity,
            String workstationIdentity,
            long workstationGeneration,
            String workstationType,
            String employeeIdentity,
            String assignmentReference,
            String transferReference,
            WorkstationReservationEndpointScope endpointScope,
            String lifecycleEvidence,
            long lifecycleEvidenceRevision,
            long createdTick,
            String dimensionIdentity,
            int workstationX,
            int workstationY,
            int workstationZ,
            int operatingX,
            int operatingY,
            int operatingZ,
            int anchorRadius
    ) {
        return new WorkstationReservationRequest(
                worldIdentity,
                requestIdentity,
                workstationIdentity,
                workstationGeneration,
                workstationType,
                employeeIdentity,
                WorkstationReservationRole.MATERIAL_HANDLER,
                Optional.of(assignmentReference),
                Optional.of(transferReference),
                endpointScope,
                lifecycleEvidence,
                lifecycleEvidenceRevision,
                createdTick,
                dimensionIdentity,
                workstationX,
                workstationY,
                workstationZ,
                operatingX,
                operatingY,
                operatingZ,
                anchorRadius,
                WorkstationReservationSchema.CONFIGURATION_IDENTITY
        );
    }

    public static String canonicalRequestIdentity(
            WorldIdentityRootIdentity worldIdentity,
            String workstationIdentity,
            String employeeIdentity,
            WorkstationReservationRole role,
            Optional<String> assignmentReference,
            Optional<String> transferReference,
            WorkstationReservationEndpointScope endpointScope
    ) {
        String digest = WorkstationReservationCanonicalDigest.create("butchercraft:workstation_reservation_request")
                .add(WorkstationReservationSchema.CURRENT_VERSION)
                .add(Objects.requireNonNull(worldIdentity, "worldIdentity").identity())
                .add(worldIdentity.rootDigest())
                .add(workstationIdentity)
                .add(employeeIdentity)
                .add(Objects.requireNonNull(role, "role").serializedName())
                .add(Objects.requireNonNull(assignmentReference, "assignmentReference").orElse(""))
                .add(Objects.requireNonNull(transferReference, "transferReference").orElse(""))
                .add(Objects.requireNonNull(endpointScope, "endpointScope").purpose().serializedName())
                .add(endpointScope.direction().serializedName())
                .add(endpointScope.endpointIdentity().orElse(""))
                .finish();
        return "butchercraft:workstation_reservation_request/v2/"
                + WorkstationReservationCanonicalDigest.suffix(digest);
    }

    public boolean sameLogicalBinding(WorkstationReservationRecord record) {
        return requestIdentity.equals(record.requestIdentity())
                && worldIdentity.equals(record.worldIdentity())
                && workstationIdentity.equals(record.workstationIdentity())
                && workstationGeneration == record.workstationGeneration()
                && workstationType.equals(record.workstationType())
                && employeeIdentity.equals(record.employeeIdentity())
                && role == record.role()
                && assignmentReference.equals(record.assignmentReference())
                && transferReference.equals(record.transferReference())
                && endpointScope.equals(record.endpointScope())
                && lifecycleEvidence.equals(record.lifecycleEvidence())
                && lifecycleEvidenceRevision == record.lifecycleEvidenceRevision()
                && configurationIdentity.equals(record.configurationIdentity());
    }

    private static void validateRoleBinding(
            WorkstationReservationRole role,
            Optional<String> assignmentReference,
            Optional<String> transferReference,
            WorkstationReservationEndpointScope endpointScope
    ) {
        if (role == WorkstationReservationRole.MACHINE_OPERATOR) {
            if (transferReference.isPresent() || !endpointScope.equals(WorkstationReservationEndpointScope.none())) {
                throw new IllegalArgumentException("MACHINE_OPERATOR cannot contain transfer endpoint authority");
            }
        } else if (assignmentReference.isEmpty()
                || transferReference.isEmpty()
                || endpointScope.purpose() == WorkstationReservationEndpointPurpose.NONE) {
            throw new IllegalArgumentException("MATERIAL_HANDLER requires assignment, transfer, and endpoint scope");
        }
    }

    private static Optional<String> normalizeIdentity(Optional<String> value, String label) {
        return Objects.requireNonNull(value, label).map(identity ->
                WorkstationReservationValidation.requireIdentity(identity, label));
    }
}
