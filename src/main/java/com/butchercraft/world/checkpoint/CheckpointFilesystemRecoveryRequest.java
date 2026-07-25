package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record CheckpointFilesystemRecoveryRequest(
        List<CheckpointOwnerId> requiredOwners,
        WorldIdentityRootReference expectedWorldIdentityRoot,
        PlatformDeterminismManifestReference expectedPlatformDeterminismManifest
) {
    public CheckpointFilesystemRecoveryRequest {
        requiredOwners = Objects.requireNonNull(requiredOwners, "requiredOwners").stream()
                .map(owner -> Objects.requireNonNull(owner, "requiredOwner"))
                .sorted()
                .toList();
        expectedWorldIdentityRoot = Objects.requireNonNull(expectedWorldIdentityRoot, "expectedWorldIdentityRoot");
        expectedPlatformDeterminismManifest = Objects.requireNonNull(
                expectedPlatformDeterminismManifest,
                "expectedPlatformDeterminismManifest"
        );
    }
}
