package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record CheckpointFilesystemRecoveryRequest(
        List<CheckpointOwnerId> requiredOwners,
        WorldIdentityRootReference expectedWorldIdentityRoot,
        PlatformDeterminismManifestReference expectedPlatformDeterminismManifest,
        List<PlatformDeterminismManifestReference> acceptedPlatformDeterminismManifests
) {
    public CheckpointFilesystemRecoveryRequest(
            List<CheckpointOwnerId> requiredOwners,
            WorldIdentityRootReference expectedWorldIdentityRoot,
            PlatformDeterminismManifestReference expectedPlatformDeterminismManifest
    ) {
        this(requiredOwners, expectedWorldIdentityRoot, expectedPlatformDeterminismManifest,
                List.of(expectedPlatformDeterminismManifest));
    }

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
        acceptedPlatformDeterminismManifests = Objects.requireNonNull(
                acceptedPlatformDeterminismManifests,
                "acceptedPlatformDeterminismManifests"
        ).stream().map(value -> Objects.requireNonNull(value, "acceptedPlatformDeterminismManifest"))
                .distinct().sorted(java.util.Comparator.comparing(PlatformDeterminismManifestReference::identity)
                        .thenComparingInt(PlatformDeterminismManifestReference::schemaVersion)
                        .thenComparing(PlatformDeterminismManifestReference::manifestDigest))
                .toList();
        if (!acceptedPlatformDeterminismManifests.contains(expectedPlatformDeterminismManifest)) {
            throw new IllegalArgumentException("Accepted platform manifests must include the current manifest");
        }
    }
}
