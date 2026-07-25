package com.butchercraft.world.checkpoint;

import java.util.Objects;
import java.util.Optional;

public record CheckpointHeadRecord(
        int schemaVersion,
        long headSequence,
        CheckpointGenerationId selectedGenerationId,
        String selectedGenerationManifestDigest,
        Optional<CheckpointGenerationId> predecessorGenerationId,
        Optional<String> predecessorManifestDigest,
        String headRecordDigest
) implements Comparable<CheckpointHeadRecord> {
    public CheckpointHeadRecord {
        schemaVersion = CheckpointValidation.positive(schemaVersion, "schemaVersion");
        headSequence = CheckpointValidation.positive(headSequence, "headSequence");
        selectedGenerationId = Objects.requireNonNull(selectedGenerationId, "selectedGenerationId");
        selectedGenerationManifestDigest = CheckpointValidation.digest(
                selectedGenerationManifestDigest,
                "selectedGenerationManifestDigest"
        );
        predecessorGenerationId = Objects.requireNonNull(predecessorGenerationId, "predecessorGenerationId");
        predecessorManifestDigest = CheckpointValidation.optionalDigest(
                predecessorManifestDigest,
                "predecessorManifestDigest"
        );
        headRecordDigest = CheckpointValidation.digest(headRecordDigest, "headRecordDigest");
    }

    public static CheckpointHeadRecord forManifest(CheckpointGenerationManifest manifest) {
        CheckpointHeadRecord head = new CheckpointHeadRecord(
                CheckpointSchema.CURRENT_VERSION,
                manifest.generationId().committedSequence(),
                manifest.generationId(),
                manifest.manifestDigest(),
                manifest.predecessorGenerationId(),
                manifest.predecessorManifestDigest(),
                CheckpointValidation.zeroDigest()
        );
        return head.withCalculatedDigest();
    }

    public CheckpointHeadRecord withCalculatedDigest() {
        return new CheckpointHeadRecord(
                schemaVersion,
                headSequence,
                selectedGenerationId,
                selectedGenerationManifestDigest,
                predecessorGenerationId,
                predecessorManifestDigest,
                calculateDigest()
        );
    }

    public String calculateDigest() {
        CheckpointCanonicalDigest digest = CheckpointCanonicalDigest.create(
                "butchercraft:checkpoint_digest/head_record"
        );
        digest.add(schemaVersion)
                .add(headSequence)
                .add(selectedGenerationId.schemaVersion())
                .add(selectedGenerationId.committedSequence())
                .add(selectedGenerationId.authoritativeSimulationTick())
                .add(selectedGenerationManifestDigest)
                .add(predecessorGenerationId.isPresent());
        predecessorGenerationId.ifPresent(predecessor -> digest
                .add(predecessor.schemaVersion())
                .add(predecessor.committedSequence())
                .add(predecessor.authoritativeSimulationTick()));
        digest.add(predecessorManifestDigest.orElse(""));
        return digest.finish();
    }

    public boolean digestMatches() {
        return headRecordDigest.equals(calculateDigest());
    }

    @Override
    public int compareTo(CheckpointHeadRecord other) {
        Objects.requireNonNull(other, "other");
        int sequenceComparison = Long.compare(headSequence, other.headSequence);
        if (sequenceComparison != 0) {
            return sequenceComparison;
        }
        return selectedGenerationId.compareTo(other.selectedGenerationId);
    }
}
