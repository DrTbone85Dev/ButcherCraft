package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record CheckpointRecoverySelectionRequest(
        List<CheckpointGenerationRecord> generations,
        List<CheckpointHeadRecord> headRecords,
        List<CheckpointOwnerId> requiredOwners,
        WorldIdentityRootReference expectedWorldIdentityRoot,
        PlatformDeterminismManifestReference expectedPlatformDeterminismManifest
) {
    public CheckpointRecoverySelectionRequest {
        generations = List.copyOf(Objects.requireNonNull(generations, "generations"));
        generations.forEach(generation -> Objects.requireNonNull(generation, "generation"));
        headRecords = List.copyOf(Objects.requireNonNull(headRecords, "headRecords"));
        headRecords.forEach(head -> Objects.requireNonNull(head, "headRecord"));
        requiredOwners = Objects.requireNonNull(requiredOwners, "requiredOwners").stream()
                .map(owner -> Objects.requireNonNull(owner, "requiredOwner"))
                .sorted()
                .toList();
        expectedWorldIdentityRoot = Objects.requireNonNull(
                expectedWorldIdentityRoot,
                "expectedWorldIdentityRoot"
        );
        expectedPlatformDeterminismManifest = Objects.requireNonNull(
                expectedPlatformDeterminismManifest,
                "expectedPlatformDeterminismManifest"
        );
    }
}
