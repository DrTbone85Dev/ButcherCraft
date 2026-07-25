package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.PlatformDeterminismManifestReference;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;

import java.nio.file.Path;
import java.util.Objects;

public record DevelopmentCheckpointRequestContext(
        boolean developmentEnabled,
        Path worldRoot,
        Path checkpointRoot,
        WorldIdentityRootReference worldIdentityRoot,
        PlatformDeterminismManifestReference platformDeterminismManifest
) {
    public DevelopmentCheckpointRequestContext {
        worldRoot = Objects.requireNonNull(worldRoot, "worldRoot").toAbsolutePath().normalize();
        checkpointRoot = Objects.requireNonNull(checkpointRoot, "checkpointRoot").toAbsolutePath().normalize();
        if (!checkpointRoot.startsWith(worldRoot)) {
            throw new IllegalArgumentException("Development checkpoint root must remain inside the world root");
        }
        worldIdentityRoot = Objects.requireNonNull(worldIdentityRoot, "worldIdentityRoot");
        platformDeterminismManifest = Objects.requireNonNull(
                platformDeterminismManifest,
                "platformDeterminismManifest"
        );
    }
}
