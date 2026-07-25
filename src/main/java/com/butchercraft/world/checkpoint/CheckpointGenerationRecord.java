package com.butchercraft.world.checkpoint;

import java.util.Objects;

public record CheckpointGenerationRecord(
        CheckpointGenerationManifest manifest,
        CheckpointPublicationState publicationState
) implements Comparable<CheckpointGenerationRecord> {
    public CheckpointGenerationRecord {
        manifest = Objects.requireNonNull(manifest, "manifest");
        publicationState = Objects.requireNonNull(publicationState, "publicationState");
    }

    public CheckpointGenerationId generationId() {
        return manifest.generationId();
    }

    @Override
    public int compareTo(CheckpointGenerationRecord other) {
        Objects.requireNonNull(other, "other");
        int idComparison = generationId().compareTo(other.generationId());
        if (idComparison != 0) {
            return idComparison;
        }
        return manifest.manifestDigest().compareTo(other.manifest.manifestDigest());
    }
}
