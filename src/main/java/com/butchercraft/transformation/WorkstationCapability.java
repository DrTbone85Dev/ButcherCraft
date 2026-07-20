package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;

import java.util.Objects;
import java.util.Set;

/**
 * Pure Java workstation capability advertisement used by transformation evaluation.
 */
public record WorkstationCapability(
        EngineId id,
        Set<EngineId> advertisedCapabilities
) {
    public WorkstationCapability {
        Objects.requireNonNull(id, "id");
        advertisedCapabilities = Set.copyOf(Objects.requireNonNull(advertisedCapabilities, "advertisedCapabilities"));
    }

    public boolean advertises(EngineId capabilityId) {
        return advertisedCapabilities.contains(Objects.requireNonNull(capabilityId, "capabilityId"));
    }
}
