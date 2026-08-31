package com.butchercraft.world.checkpoint;

import java.util.Objects;
import java.util.Optional;
import java.nio.file.Path;

public record LegacyTempArtifactFinding(
        CheckpointOwnerId ownerId,
        String targetPath,
        Optional<String> artifactPath,
        Relation relation,
        Classification classification,
        OwnerValidation finalValidation,
        OwnerValidation artifactValidation,
        Optional<String> finalContentDigest,
        Optional<String> artifactContentDigest,
        String detail
) implements Comparable<LegacyTempArtifactFinding> {
    public LegacyTempArtifactFinding {
        ownerId = Objects.requireNonNull(ownerId, "ownerId");
        targetPath = CheckpointValidation.text(targetPath, "legacyTempTargetPath");
        artifactPath = Objects.requireNonNull(artifactPath, "artifactPath")
                .map(value -> CheckpointValidation.text(value, "legacyTempArtifactPath"));
        relation = Objects.requireNonNull(relation, "relation");
        classification = Objects.requireNonNull(classification, "classification");
        finalValidation = Objects.requireNonNull(finalValidation, "finalValidation");
        artifactValidation = Objects.requireNonNull(artifactValidation, "artifactValidation");
        finalContentDigest = CheckpointValidation.optionalDigest(finalContentDigest, "legacyTempFinalDigest");
        artifactContentDigest = CheckpointValidation.optionalDigest(
                artifactContentDigest,
                "legacyTempArtifactDigest"
        );
        detail = CheckpointValidation.text(detail, "legacyTempDetail");
        if (classification == Classification.ABSENT && artifactPath.isPresent()) {
            throw new IllegalArgumentException("Absent temp classification cannot reference an artifact");
        }
        if (relation == Relation.CURRENT_UNIQUE_ATTEMPT
                && classification != Classification.CURRENT_UNIQUE_ATTEMPT) {
            throw new IllegalArgumentException("Unique AtomicFilePublication attempt must remain distinctly classified");
        }
    }

    @Override
    public int compareTo(LegacyTempArtifactFinding other) {
        Objects.requireNonNull(other, "other");
        int ownerComparison = ownerId.compareTo(other.ownerId);
        if (ownerComparison != 0) return ownerComparison;
        int targetComparison = targetFileName().compareTo(other.targetFileName());
        if (targetComparison != 0) return targetComparison;
        int relationComparison = relation.compareTo(other.relation);
        if (relationComparison != 0) return relationComparison;
        int finalDigestComparison = finalContentDigest.orElse("")
                .compareTo(other.finalContentDigest.orElse(""));
        return finalDigestComparison != 0
                ? finalDigestComparison
                : artifactContentDigest.orElse("").compareTo(other.artifactContentDigest.orElse(""));
    }

    public String targetFileName() {
        return Path.of(targetPath).getFileName().toString();
    }

    public enum Relation {
        LEGACY_FIXED_TEMP,
        CURRENT_UNIQUE_ATTEMPT,
        ABSENT
    }

    public enum Classification {
        IDENTICAL_STALE_DEBRIS,
        DIFFERING_REQUIRES_OWNER_ANALYSIS,
        MALFORMED,
        ABSENT,
        CURRENT_UNIQUE_ATTEMPT,
        UNSUPPORTED_SCHEMA,
        IDENTITY_CONFLICT
    }

    public enum OwnerValidation {
        OWNER_VALID,
        NOT_ANALYZED,
        MALFORMED,
        UNSUPPORTED_SCHEMA,
        IDENTITY_CONFLICT
    }
}
