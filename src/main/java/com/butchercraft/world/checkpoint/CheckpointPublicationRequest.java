package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointPublicationRequest(
        CheckpointGenerationId generationId,
        Optional<CheckpointGenerationId> predecessorGenerationId,
        Optional<String> predecessorManifestDigest,
        long authoritativeSimulationTick,
        List<CheckpointOwnerSnapshotPayload> ownerSnapshots,
        List<CheckpointOwnerId> requiredOwners,
        PlatformDeterminismManifestReference platformDeterminismManifest,
        WorldIdentityRootReference worldIdentityRoot
) {
    public CheckpointPublicationRequest {
        generationId = Objects.requireNonNull(generationId, "generationId");
        predecessorGenerationId = Objects.requireNonNull(predecessorGenerationId, "predecessorGenerationId");
        predecessorManifestDigest = CheckpointValidation.optionalDigest(
                predecessorManifestDigest,
                "predecessorManifestDigest"
        );
        authoritativeSimulationTick = CheckpointValidation.nonNegative(
                authoritativeSimulationTick,
                "authoritativeSimulationTick"
        );
        ownerSnapshots = Objects.requireNonNull(ownerSnapshots, "ownerSnapshots").stream()
                .map(snapshot -> Objects.requireNonNull(snapshot, "ownerSnapshot"))
                .sorted()
                .toList();
        requiredOwners = Objects.requireNonNull(requiredOwners, "requiredOwners").stream()
                .map(owner -> Objects.requireNonNull(owner, "requiredOwner"))
                .sorted()
                .toList();
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
    }

    CheckpointGenerationCandidate toCandidate() {
        return new CheckpointGenerationCandidate(
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                authoritativeSimulationTick,
                ownerSnapshots.stream()
                        .map(CheckpointOwnerSnapshotPayload::descriptor)
                        .toList(),
                platformDeterminismManifest,
                worldIdentityRoot,
                CheckpointPublicationState.COMPLETE_CANDIDATE
        );
    }
}
