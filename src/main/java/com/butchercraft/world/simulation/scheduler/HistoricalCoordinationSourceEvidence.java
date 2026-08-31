package com.butchercraft.world.simulation.scheduler;

import java.util.Objects;

/** Exact immutable owner evidence used to prove missing Scheduler history. */
public record HistoricalCoordinationSourceEvidence(
        String ownerSubsystemId,
        String sourceSnapshotIdentity,
        String sourceSnapshotDigest,
        String evidenceIdentity,
        String evidenceContentDigest
) implements Comparable<HistoricalCoordinationSourceEvidence> {
    public HistoricalCoordinationSourceEvidence {
        ownerSubsystemId = SchedulerValidation.requireId(ownerSubsystemId, "Historical evidence owner id");
        sourceSnapshotIdentity = SchedulerValidation.requireId(
                sourceSnapshotIdentity,
                "Historical evidence source snapshot identity"
        );
        sourceSnapshotDigest = SchedulerIdentityDigest.requireDigest(
                sourceSnapshotDigest,
                "Historical evidence source snapshot digest"
        );
        evidenceIdentity = SchedulerValidation.requireId(evidenceIdentity, "Historical evidence identity");
        evidenceContentDigest = SchedulerIdentityDigest.requireDigest(
                evidenceContentDigest,
                "Historical evidence content digest"
        );
    }

    @Override
    public int compareTo(HistoricalCoordinationSourceEvidence other) {
        Objects.requireNonNull(other, "other");
        int ownerComparison = ownerSubsystemId.compareTo(other.ownerSubsystemId);
        if (ownerComparison != 0) return ownerComparison;
        int snapshotComparison = sourceSnapshotIdentity.compareTo(other.sourceSnapshotIdentity);
        if (snapshotComparison != 0) return snapshotComparison;
        return evidenceIdentity.compareTo(other.evidenceIdentity);
    }
}
