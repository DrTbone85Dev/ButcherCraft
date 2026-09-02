package com.butchercraft.workstation.reservation;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Objects;

public record WorkstationReservationId(String value) implements Comparable<WorkstationReservationId> {
    private static final String PREFIX = "butchercraft:workstation_reservation/v2/";

    public WorkstationReservationId {
        value = WorkstationReservationValidation.requireIdentity(value, "workstation reservation identity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported Workstation Reservation Identity");
        }
    }

    public static WorkstationReservationId create(
            WorldIdentityRootIdentity worldIdentity,
            long sequence,
            WorkstationReservationRequest request
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        if (sequence <= 0L) {
            throw new IllegalArgumentException("Workstation reservation sequence must be positive");
        }
        Objects.requireNonNull(request, "request");
        String digest = WorkstationReservationCanonicalDigest.create("butchercraft:workstation_reservation")
                .add(WorkstationReservationSchema.CURRENT_VERSION)
                .add(worldIdentity.identity())
                .add(worldIdentity.schemaVersion())
                .add(worldIdentity.rootDigest())
                .add(sequence)
                .add(request.requestIdentity())
                .add(request.employeeIdentity())
                .add(request.workstationIdentity())
                .add(request.workstationGeneration())
                .add(request.role().serializedName())
                .add(request.assignmentReference().orElse(""))
                .add(request.transferReference().orElse(""))
                .add(request.endpointScope().purpose().serializedName())
                .add(request.endpointScope().direction().serializedName())
                .add(request.endpointScope().endpointIdentity().orElse(""))
                .add(request.lifecycleEvidence())
                .add(request.lifecycleEvidenceRevision())
                .add(request.configurationIdentity())
                .finish();
        return new WorkstationReservationId(PREFIX + WorkstationReservationCanonicalDigest.suffix(digest));
    }

    static WorkstationReservationId createLegacy(
            WorldIdentityRootIdentity worldIdentity,
            long sequence,
            String employeeIdentity,
            String workstationIdentity,
            long createdTick
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        if (sequence <= 0L) {
            throw new IllegalArgumentException("Workstation reservation sequence must be positive");
        }
        String digest = WorkstationReservationCanonicalDigest.create("butchercraft:legacy_workstation_reservation")
                .add(WorkstationReservationSchema.LEGACY_VERSION)
                .add(worldIdentity.identity())
                .add(worldIdentity.rootDigest())
                .add(sequence)
                .add(employeeIdentity)
                .add(workstationIdentity)
                .add(createdTick)
                .finish();
        return new WorkstationReservationId(PREFIX + WorkstationReservationCanonicalDigest.suffix(digest));
    }

    @Override
    public int compareTo(WorkstationReservationId other) {
        return value.compareTo(other.value);
    }
}
