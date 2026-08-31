package com.butchercraft.workstation.checkpoint;

import com.butchercraft.workstation.endpoint.WorkstationInstanceId;
import com.butchercraft.workstation.projection.WorkstationProjectionReadCode;

import java.util.Objects;

public record WorkstationCheckpointBlocker(
        WorkstationInstanceId instanceId,
        WorkstationProjectionReadCode projectionState,
        String detail
) implements Comparable<WorkstationCheckpointBlocker> {
    public WorkstationCheckpointBlocker {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        projectionState = Objects.requireNonNull(projectionState, "projectionState");
        detail = Objects.requireNonNull(detail, "detail").trim();
        if (detail.isEmpty()) throw new IllegalArgumentException("detail must not be blank");
    }

    @Override
    public int compareTo(WorkstationCheckpointBlocker other) {
        int identity = instanceId.compareTo(other.instanceId);
        if (identity != 0) return identity;
        int state = projectionState.compareTo(other.projectionState);
        return state != 0 ? state : detail.compareTo(other.detail);
    }
}
