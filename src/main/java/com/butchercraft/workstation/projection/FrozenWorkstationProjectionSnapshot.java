package com.butchercraft.workstation.projection;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;

import java.util.Arrays;
import java.util.Objects;

public final class FrozenWorkstationProjectionSnapshot {
    private final WorkstationInstanceId instanceId;
    private final long projectionRevision;
    private final String stateDigest;
    private final byte[] frozenBytes;

    public FrozenWorkstationProjectionSnapshot(
            WorkstationInstanceId instanceId,
            long projectionRevision,
            String stateDigest,
            byte[] frozenBytes
    ) {
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        if (projectionRevision <= 0L) throw new IllegalArgumentException("Projection revision must be positive");
        this.projectionRevision = projectionRevision;
        this.stateDigest = Objects.requireNonNull(stateDigest, "stateDigest");
        this.frozenBytes = Objects.requireNonNull(frozenBytes, "frozenBytes").clone();
    }

    public WorkstationInstanceId instanceId() {
        return instanceId;
    }

    public long projectionRevision() {
        return projectionRevision;
    }

    public String stateDigest() {
        return stateDigest;
    }

    public byte[] frozenBytes() {
        return frozenBytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof FrozenWorkstationProjectionSnapshot snapshot)) return false;
        return projectionRevision == snapshot.projectionRevision
                && instanceId.equals(snapshot.instanceId)
                && stateDigest.equals(snapshot.stateDigest)
                && Arrays.equals(frozenBytes, snapshot.frozenBytes);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(instanceId, projectionRevision, stateDigest);
        return 31 * result + Arrays.hashCode(frozenBytes);
    }
}
