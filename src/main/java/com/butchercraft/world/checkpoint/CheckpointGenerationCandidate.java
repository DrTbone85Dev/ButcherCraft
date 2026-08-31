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
        List<String> triggerCauses,
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
        triggerCauses = Objects.requireNonNull(triggerCauses, "triggerCauses").stream()
                .map(cause -> CheckpointValidation.id(cause, "triggerCause"))
                .distinct()
                .sorted()
                .toList();
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        candidateState = Objects.requireNonNull(candidateState, "candidateState");
    }

    public CheckpointGenerationCandidate(
            CheckpointGenerationId generationId,
            Optional<CheckpointGenerationId> predecessorGenerationId,
            Optional<String> predecessorManifestDigest,
            long authoritativeSimulationTick,
            List<OwnerSnapshotDescriptor> ownerSnapshots,
            PlatformDeterminismManifestReference platformDeterminismManifest,
            WorldIdentityRootReference worldIdentityRoot,
            CheckpointPublicationState candidateState
    ) {
        this(
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                authoritativeSimulationTick,
                ownerSnapshots,
                List.of(),
                platformDeterminismManifest,
                worldIdentityRoot,
                candidateState
        );
    }

    public CheckpointGenerationManifest toManifest() {
        CheckpointGenerationManifest candidate = new CheckpointGenerationManifest(
                CheckpointSchema.CURRENT_VERSION,
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                authoritativeSimulationTick,
                ownerSnapshots,
                triggerCauses,
                platformDeterminismManifest,
                worldIdentityRoot,
                CheckpointValidation.zeroDigest()
        );
        return candidate.withCalculatedDigest();
    }
}
