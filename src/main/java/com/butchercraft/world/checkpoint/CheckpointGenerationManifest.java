package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CheckpointGenerationManifest(
        int schemaVersion,
        CheckpointGenerationId generationId,
        Optional<CheckpointGenerationId> predecessorGenerationId,
        Optional<String> predecessorManifestDigest,
        long authoritativeSimulationTick,
        List<OwnerSnapshotDescriptor> ownerSnapshots,
        PlatformDeterminismManifestReference platformDeterminismManifest,
        WorldIdentityRootReference worldIdentityRoot,
        String manifestDigest
) implements Comparable<CheckpointGenerationManifest> {
    public CheckpointGenerationManifest {
        schemaVersion = CheckpointValidation.positive(schemaVersion, "schemaVersion");
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
        manifestDigest = CheckpointValidation.digest(manifestDigest, "manifestDigest");
    }

    public CheckpointGenerationManifest withCalculatedDigest() {
        return new CheckpointGenerationManifest(
                schemaVersion,
                generationId,
                predecessorGenerationId,
                predecessorManifestDigest,
                authoritativeSimulationTick,
                ownerSnapshots,
                platformDeterminismManifest,
                worldIdentityRoot,
                calculateDigest()
        );
    }

    public String calculateDigest() {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:checkpoint_digest/generation_manifest"
        );
        digest.add(schemaVersion)
                .add(generationId.schemaVersion())
                .add(generationId.committedSequence())
                .add(generationId.authoritativeSimulationTick())
                .add(predecessorGenerationId.isPresent());
        predecessorGenerationId.ifPresent(predecessor -> digest
                .add(predecessor.schemaVersion())
                .add(predecessor.committedSequence())
                .add(predecessor.authoritativeSimulationTick()));
        digest.add(predecessorManifestDigest.orElse(""))
                .add(authoritativeSimulationTick)
                .add(platformDeterminismManifest.identity())
                .add(platformDeterminismManifest.schemaVersion())
                .add(platformDeterminismManifest.manifestDigest())
                .add(worldIdentityRoot.identity())
                .add(worldIdentityRoot.schemaVersion())
                .add(worldIdentityRoot.rootDigest())
                .add(ownerSnapshots.size());
        for (OwnerSnapshotDescriptor snapshot : ownerSnapshots) {
            digest.add(snapshot.ownerId().value())
                    .add(snapshot.snapshotSchemaVersion())
                    .add(snapshot.snapshotIdentity())
                    .add(snapshot.contentDigest())
                    .add(snapshot.participation().name())
                    .add(snapshot.configurationIdentity())
                    .add(snapshot.worldIdentityRoot().identity())
                    .add(snapshot.worldIdentityRoot().schemaVersion())
                    .add(snapshot.worldIdentityRoot().rootDigest())
                    .add(snapshot.generationId().schemaVersion())
                    .add(snapshot.generationId().committedSequence())
                    .add(snapshot.generationId().authoritativeSimulationTick())
                    .add(snapshot.representedSimulationTick())
                    .add(snapshot.ownerSequence());
        }
        return digest.finish();
    }

    public boolean digestMatches() {
        return manifestDigest.equals(calculateDigest());
    }

    @Override
    public int compareTo(CheckpointGenerationManifest other) {
        Objects.requireNonNull(other, "other");
        int idComparison = generationId.compareTo(other.generationId);
        if (idComparison != 0) {
            return idComparison;
        }
        return manifestDigest.compareTo(other.manifestDigest);
    }
}
