package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointGenerationCandidate(
        CheckpointGenerationId generationId,
        Optional<CheckpointGenerationId> predecessorGenerationId,
        Optional<String> predecessorManifestDigest,
        long authoritativeSimulationTick,
        List<OwnerSnapshotDescriptor> ownerSnapshots,
        PlatformDeterminismManifestReference platformDeterminismManifest,
        WorldIdentityRootReference worldIdentityRoot,
        CheckpointPublicationState candidateState
) {
    public CheckpointGenerationCandidate {
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
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        candidateState = Objects.requireNonNull(candidateState, "candidateState");
    }

    public CheckpointGenerationManifest toManifest() {
        CheckpointGenerationManifest candidate = new CheckpointGenerationManifest(
                CheckpointSchema.CURRENT_VERSION,
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                authoritativeSimulationTick,
                ownerSnapshots,
                platformDeterminismManifest,
                worldIdentityRoot,
                CheckpointValidation.zeroDigest()
        );
        return candidate.withCalculatedDigest();
    }
}
