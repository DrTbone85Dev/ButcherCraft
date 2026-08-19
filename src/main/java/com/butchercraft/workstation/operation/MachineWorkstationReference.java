package com.butchercraft.workstation.operation;

import com.butchercraft.workstation.endpoint.WorkstationEndpointKey;
import com.butchercraft.workstation.endpoint.WorkstationInstanceId;

import java.util.Objects;

public record MachineWorkstationReference(
        WorkstationInstanceId instanceId,
        WorkstationEndpointKey endpointKey,
        long generation,
        String allocationConfigurationIdentity
) implements Comparable<MachineWorkstationReference> {
    public MachineWorkstationReference {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        endpointKey = Objects.requireNonNull(endpointKey, "endpointKey");
        if (generation <= 0L) throw new IllegalArgumentException("Workstation instance generation must be positive");
        allocationConfigurationIdentity = MachineOperatingValidation.id(
                allocationConfigurationIdentity,
                "Workstation instance allocation configuration identity"
        );
    }

    @Override
    public int compareTo(MachineWorkstationReference other) {
        return instanceId.compareTo(other.instanceId);
    }
}
