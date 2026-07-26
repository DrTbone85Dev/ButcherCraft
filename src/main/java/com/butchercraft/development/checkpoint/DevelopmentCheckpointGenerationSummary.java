package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointGenerationManifest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record DevelopmentCheckpointGenerationSummary(
        CheckpointGenerationId generationId,
        long authoritativeSimulationTick,
        Optional<CheckpointGenerationId> predecessorGenerationId,
        Optional<String> predecessorManifestDigest,
        String manifestDigest,
        String worldIdentityRootIdentity,
        String platformDeterminismManifestIdentity,
        List<DevelopmentCheckpointOwnerSummary> owners
) implements Comparable<DevelopmentCheckpointGenerationSummary> {
    public DevelopmentCheckpointGenerationSummary {
        generationId = Objects.requireNonNull(generationId, "generationId");
        authoritativeSimulationTick = authoritativeSimulationTick < 0L
                ? throwNegativeTick()
                : authoritativeSimulationTick;
        predecessorGenerationId = Objects.requireNonNull(predecessorGenerationId, "predecessorGenerationId");
        predecessorManifestDigest = Objects.requireNonNull(predecessorManifestDigest, "predecessorManifestDigest")
                .map(String::strip)
                .filter(value -> !value.isEmpty());
        manifestDigest = clean(manifestDigest, "manifestDigest");
        worldIdentityRootIdentity = clean(worldIdentityRootIdentity, "worldIdentityRootIdentity");
        platformDeterminismManifestIdentity = clean(
                platformDeterminismManifestIdentity,
                "platformDeterminismManifestIdentity"
        );
        owners = Objects.requireNonNull(owners, "owners").stream()
                .map(owner -> Objects.requireNonNull(owner, "owner"))
                .sorted()
                .toList();
    }

    public static DevelopmentCheckpointGenerationSummary from(CheckpointGenerationManifest manifest) {
        Objects.requireNonNull(manifest, "manifest");
        return new DevelopmentCheckpointGenerationSummary(
                manifest.generationId(),
                manifest.authoritativeSimulationTick(),
                manifest.predecessorGenerationId(),
                manifest.predecessorManifestDigest(),
                manifest.manifestDigest(),
                manifest.worldIdentityRoot().identity(),
                manifest.platformDeterminismManifest().identity(),
                manifest.ownerSnapshots().stream()
                        .map(DevelopmentCheckpointOwnerSummary::from)
                        .toList()
        );
    }

    @Override
    public int compareTo(DevelopmentCheckpointGenerationSummary other) {
        Objects.requireNonNull(other, "other");
        return generationId.compareTo(other.generationId);
    }

    private static long throwNegativeTick() {
        throw new IllegalArgumentException("authoritativeSimulationTick cannot be negative");
    }

    private static String clean(String value, String field) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return cleaned;
    }
}
