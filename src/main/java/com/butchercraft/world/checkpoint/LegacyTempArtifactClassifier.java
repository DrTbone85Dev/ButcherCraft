package com.butchercraft.world.checkpoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/** Read-only classifier. Names and timestamps never grant authority. */
public final class LegacyTempArtifactClassifier {
    public LegacyTempArtifactFinding classify(
            CheckpointOwnerId ownerId,
            Path targetFile,
            Path artifactFile,
            LegacyTempArtifactFinding.OwnerValidation finalValidation,
            LegacyTempArtifactFinding.OwnerValidation artifactValidation
    ) {
        Path target = normalize(targetFile);
        Path artifact = normalize(artifactFile);
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(finalValidation, "finalValidation");
        Objects.requireNonNull(artifactValidation, "artifactValidation");
        if (!Files.exists(artifact)) {
            return new LegacyTempArtifactFinding(
                    ownerId,
                    target.toString(),
                    Optional.empty(),
                    LegacyTempArtifactFinding.Relation.ABSENT,
                    LegacyTempArtifactFinding.Classification.ABSENT,
                    finalValidation,
                    artifactValidation,
                    digestIfPresent(target),
                    Optional.empty(),
                    "No related temp artifact exists"
            );
        }

        LegacyTempArtifactFinding.Relation relation = relation(target, artifact);
        byte[] finalBytes = bytesIfPresent(target);
        byte[] artifactBytes = read(artifact);
        Optional<String> finalDigest = finalBytes == null
                ? Optional.empty()
                : Optional.of(CheckpointSnapshotDigest.sha256(finalBytes));
        String artifactDigest = CheckpointSnapshotDigest.sha256(artifactBytes);
        if (relation == LegacyTempArtifactFinding.Relation.CURRENT_UNIQUE_ATTEMPT) {
            return finding(
                    ownerId,
                    target,
                    artifact,
                    relation,
                    LegacyTempArtifactFinding.Classification.CURRENT_UNIQUE_ATTEMPT,
                    finalValidation,
                    artifactValidation,
                    finalDigest,
                    artifactDigest,
                    "Current unique AtomicFilePublication attempt; no legacy authority classification applied"
            );
        }
        if (relation != LegacyTempArtifactFinding.Relation.LEGACY_FIXED_TEMP) {
            throw new IllegalArgumentException("Artifact is not a recognized temp for target: " + artifact);
        }

        LegacyTempArtifactFinding.Classification classification;
        String detail;
        if (finalBytes != null && Arrays.equals(finalBytes, artifactBytes)) {
            classification = LegacyTempArtifactFinding.Classification.IDENTICAL_STALE_DEBRIS;
            detail = "Legacy fixed temp is byte-identical stale non-authoritative debris";
        } else if (finalValidation == LegacyTempArtifactFinding.OwnerValidation.UNSUPPORTED_SCHEMA
                || artifactValidation == LegacyTempArtifactFinding.OwnerValidation.UNSUPPORTED_SCHEMA) {
            classification = LegacyTempArtifactFinding.Classification.UNSUPPORTED_SCHEMA;
            detail = "Owner schema cannot be analyzed safely";
        } else if (finalValidation == LegacyTempArtifactFinding.OwnerValidation.IDENTITY_CONFLICT
                || artifactValidation == LegacyTempArtifactFinding.OwnerValidation.IDENTITY_CONFLICT) {
            classification = LegacyTempArtifactFinding.Classification.IDENTITY_CONFLICT;
            detail = "Owner identity evidence conflicts";
        } else if (artifactValidation == LegacyTempArtifactFinding.OwnerValidation.MALFORMED) {
            classification = LegacyTempArtifactFinding.Classification.MALFORMED;
            detail = "Legacy fixed temp is malformed and remains non-authoritative";
        } else {
            classification = LegacyTempArtifactFinding.Classification.DIFFERING_REQUIRES_OWNER_ANALYSIS;
            detail = "Differing legacy fixed temp requires owner-specific proof; timestamps are ignored";
        }
        return finding(
                ownerId,
                target,
                artifact,
                relation,
                classification,
                finalValidation,
                artifactValidation,
                finalDigest,
                artifactDigest,
                detail
        );
    }

    private LegacyTempArtifactFinding finding(
            CheckpointOwnerId ownerId,
            Path target,
            Path artifact,
            LegacyTempArtifactFinding.Relation relation,
            LegacyTempArtifactFinding.Classification classification,
            LegacyTempArtifactFinding.OwnerValidation finalValidation,
            LegacyTempArtifactFinding.OwnerValidation artifactValidation,
            Optional<String> finalDigest,
            String artifactDigest,
            String detail
    ) {
        return new LegacyTempArtifactFinding(
                ownerId,
                target.toString(),
                Optional.of(artifact.toString()),
                relation,
                classification,
                finalValidation,
                artifactValidation,
                finalDigest,
                Optional.of(artifactDigest),
                detail
        );
    }

    private LegacyTempArtifactFinding.Relation relation(Path target, Path artifact) {
        String targetName = target.getFileName().toString();
        String artifactName = artifact.getFileName().toString();
        if (artifactName.equals(targetName + ".tmp")) {
            return LegacyTempArtifactFinding.Relation.LEGACY_FIXED_TEMP;
        }
        if (artifactName.startsWith(targetName + ".tmp-") && artifactName.length() > targetName.length() + 5) {
            return LegacyTempArtifactFinding.Relation.CURRENT_UNIQUE_ATTEMPT;
        }
        throw new IllegalArgumentException("Artifact name is unrelated to target: " + artifactName);
    }

    private Optional<String> digestIfPresent(Path path) {
        byte[] bytes = bytesIfPresent(path);
        return bytes == null ? Optional.empty() : Optional.of(CheckpointSnapshotDigest.sha256(bytes));
    }

    private byte[] bytesIfPresent(Path path) {
        return Files.exists(path) ? read(path) : null;
    }

    private byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed read-only temp artifact inspection: " + path, exception);
        }
    }

    private Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}
