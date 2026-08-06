package com.butchercraft.workstation.endpoint;

import com.butchercraft.world.identity.WorldIdentityRootIdentity;

import java.util.Objects;

public record WorkstationInstanceId(String value) implements Comparable<WorkstationInstanceId> {
    private static final String PREFIX = "butchercraft:workstation_instance/v1/";

    public WorkstationInstanceId {
        value = WorkstationEndpointValidation.id(value, "workstation instance identity");
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Workstation instance identity has unsupported prefix");
        }
    }

    public static WorkstationInstanceId create(
            WorldIdentityRootIdentity worldIdentity,
            WorkstationEndpointKey key,
            long generation,
            String allocationConfigurationIdentity
    ) {
        Objects.requireNonNull(worldIdentity, "worldIdentity");
        Objects.requireNonNull(key, "key");
        WorkstationEndpointValidation.positive(generation, "workstation instance generation");
        String digest = WorkstationEndpointCanonicalDigest.create("butchercraft:workstation_instance")
                .add(WorkstationEndpointSchema.CURRENT_VERSION)
                .add(worldIdentity.identity())
                .add(worldIdentity.schemaVersion())
                .add(worldIdentity.rootDigest())
                .add(key.workstationTypeIdentity())
                .add(key.dimensionIdentity())
                .add(key.x())
                .add(key.y())
                .add(key.z())
                .add(generation)
                .add(allocationConfigurationIdentity)
                .finish();
        return new WorkstationInstanceId(PREFIX + WorkstationEndpointCanonicalDigest.suffix(digest));
    }

    @Override
    public int compareTo(WorkstationInstanceId other) {
        return value.compareTo(other.value);
    }
}
