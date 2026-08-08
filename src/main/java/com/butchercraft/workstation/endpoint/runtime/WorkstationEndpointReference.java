package com.butchercraft.workstation.endpoint.runtime;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;

import java.util.Objects;

public record WorkstationEndpointReference(
        WorkstationInstanceId instanceId,
        WorkstationEndpointKey endpointKey,
        long generation
) {
    public WorkstationEndpointReference {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        endpointKey = Objects.requireNonNull(endpointKey, "endpointKey");
        if (generation <= 0L) throw new IllegalArgumentException("Endpoint generation must be positive");
    }
}
