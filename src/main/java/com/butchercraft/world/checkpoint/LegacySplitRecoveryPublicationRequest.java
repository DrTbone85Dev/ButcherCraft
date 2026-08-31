package com.butchercraft.world.checkpoint;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryPublicationRequest(
        LegacySplitRecoveryAnalysisSource analysisSource,
        RecoveryOperatorAuthorization authorization,
        Path targetWorldRoot,
        Path checkpointRoot,
        List<Path> protectedWorldRoots,
        List<LegacySplitRecoveryOwnerPreparer> ownerPreparers,
        Optional<String> completionTimestampMetadata
) {
    public LegacySplitRecoveryPublicationRequest {
        analysisSource = Objects.requireNonNull(analysisSource, "analysisSource");
        authorization = Objects.requireNonNull(authorization, "authorization");
        targetWorldRoot = normalize(targetWorldRoot, "targetWorldRoot");
        checkpointRoot = normalize(checkpointRoot, "checkpointRoot");
        protectedWorldRoots = Objects.requireNonNull(protectedWorldRoots, "protectedWorldRoots").stream()
                .map(path -> normalize(path, "protectedWorldRoot"))
                .distinct()
                .sorted()
                .toList();
        ownerPreparers = List.copyOf(Objects.requireNonNull(ownerPreparers, "ownerPreparers"));
        completionTimestampMetadata = Objects.requireNonNull(
                completionTimestampMetadata,
                "completionTimestampMetadata"
        ).map(value -> CheckpointValidation.text(value, "completionTimestampMetadata"));
    }

    private static Path normalize(Path path, String label) {
        return Objects.requireNonNull(path, label).toAbsolutePath().normalize();
    }
}
