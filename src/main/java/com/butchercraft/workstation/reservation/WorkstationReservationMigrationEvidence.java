package com.butchercraft.workstation.reservation;

import java.util.Objects;

public record WorkstationReservationMigrationEvidence(
        String requestIdentity,
        String workstationIdentity,
        long workstationGeneration,
        String assignmentReference,
        String transferReference,
        WorkstationReservationEndpointScope endpointScope,
        String lifecycleEvidence,
        long lifecycleEvidenceRevision
) {
    public WorkstationReservationMigrationEvidence {
        requestIdentity = WorkstationReservationValidation.requireIdentity(requestIdentity, "request identity");
        workstationIdentity = WorkstationReservationValidation.requireIdentity(workstationIdentity, "workstation identity");
        if (workstationGeneration <= 0L) throw new IllegalArgumentException("Workstation generation must be positive");
        assignmentReference = WorkstationReservationValidation.requireIdentity(assignmentReference, "assignment reference");
        transferReference = WorkstationReservationValidation.requireIdentity(transferReference, "transfer reference");
        endpointScope = Objects.requireNonNull(endpointScope, "endpointScope");
        if (endpointScope.purpose() == WorkstationReservationEndpointPurpose.NONE) {
            throw new IllegalArgumentException("Migration proof requires endpoint scope");
        }
        lifecycleEvidence = WorkstationReservationValidation.requireToken(lifecycleEvidence, "lifecycle evidence");
        if (lifecycleEvidenceRevision < 0L) throw new IllegalArgumentException("Lifecycle revision must not be negative");
    }
}
