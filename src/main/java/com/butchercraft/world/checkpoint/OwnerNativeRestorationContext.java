package com.butchercraft.world.checkpoint;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public record OwnerNativeRestorationContext(
        Path worldRoot,
        Path ownerRoot,
        WorldIdentityRootReference worldIdentityRoot,
        CheckpointGenerationManifest generationManifest,
        RestorationSource source,
        Optional<LegacySplitRecoveryResult> legacyRecoveryResult
) {
    public OwnerNativeRestorationContext {
        worldRoot = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        ownerRoot = Objects.requireNonNull(ownerRoot, "ownerRoot").toAbsolutePath().normalize();
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        generationManifest = Objects.requireNonNull(generationManifest, "generationManifest");
        source = Objects.requireNonNull(source, "source");
        legacyRecoveryResult = Objects.requireNonNull(legacyRecoveryResult, "legacyRecoveryResult");
        if (!generationManifest.worldIdentityRoot().equals(worldIdentityRoot)) {
            throw new IllegalArgumentException("Restoration context crosses World Identity");
        }
        if ((source == RestorationSource.RECOVERY_GENERATION) != legacyRecoveryResult.isPresent()) {
            throw new IllegalArgumentException("Legacy Recovery Result presence must match restoration source");
        }
    }
}
