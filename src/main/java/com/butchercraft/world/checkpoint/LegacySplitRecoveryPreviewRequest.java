package com.butchercraft.world.checkpoint;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record LegacySplitRecoveryPreviewRequest(
        LegacySplitRecoveryAnalysisSource analysisSource,
        Path targetWorldRoot,
        Path checkpointRoot,
        List<Path> protectedWorldRoots
) {
    public LegacySplitRecoveryPreviewRequest {
        analysisSource = Objects.requireNonNull(analysisSource, "analysisSource");
        targetWorldRoot = Objects.requireNonNull(targetWorldRoot, "targetWorldRoot").toAbsolutePath().normalize();
        checkpointRoot = Objects.requireNonNull(checkpointRoot, "checkpointRoot").toAbsolutePath().normalize();
        protectedWorldRoots = Objects.requireNonNull(protectedWorldRoots, "protectedWorldRoots").stream()
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .sorted()
                .toList();
    }
}
