package com.butchercraft.world.checkpoint;

import java.util.Arrays;
import java.util.Objects;

public final class CheckpointOwnerRestorationRequest {
    private final OwnerSnapshotDescriptor descriptor;
    private final byte[] payloadBytes;
    private final CheckpointGenerationId selectedGenerationId;
    private final PlatformDeterminismManifestReference platformDeterminismManifest;
    private final WorldIdentityRootReference worldIdentityRoot;

    public CheckpointOwnerRestorationRequest(
            OwnerSnapshotDescriptor descriptor,
            byte[] payloadBytes,
            CheckpointGenerationId selectedGenerationId,
            PlatformDeterminismManifestReference platformDeterminismManifest,
            WorldIdentityRootReference worldIdentityRoot
    ) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.payloadBytes = Objects.requireNonNull(payloadBytes, "payloadBytes").clone();
        this.selectedGenerationId = Objects.requireNonNull(selectedGenerationId, "selectedGenerationId");
        this.platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        this.worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
    }

    public OwnerSnapshotDescriptor descriptor() {
        return descriptor;
    }

    public byte[] payloadBytes() {
        return payloadBytes.clone();
    }

    public CheckpointGenerationId selectedGenerationId() {
        return selectedGenerationId;
    }

    public PlatformDeterminismManifestReference platformDeterminismManifest() {
        return platformDeterminismManifest;
    }

    public WorldIdentityRootReference worldIdentityRoot() {
        return worldIdentityRoot;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CheckpointOwnerRestorationRequest other)) {
            return false;
        }
        return descriptor.equals(other.descriptor)
                && Arrays.equals(payloadBytes, other.payloadBytes)
                && selectedGenerationId.equals(other.selectedGenerationId)
                && platformDeterminismManifest.equals(other.platformDeterminismManifest)
                && worldIdentityRoot.equals(other.worldIdentityRoot);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(
                descriptor,
                selectedGenerationId,
                platformDeterminismManifest,
                worldIdentityRoot
        );
        result = 31 * result + Arrays.hashCode(payloadBytes);
        return result;
    }
}
