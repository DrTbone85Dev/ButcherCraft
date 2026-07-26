package com.butchercraft.development.checkpoint;

import java.nio.file.Path;
import java.util.Objects;

public final class DevelopmentCheckpointRoots {
    public static final String BUTCHERCRAFT_DIRECTORY = "butchercraft";
    public static final String DEVELOPMENT_CHECKPOINT_DIRECTORY = "development_checkpoints";

    private DevelopmentCheckpointRoots() {
    }

    public static Path checkpointRoot(Path worldRoot) {
        Path normalizedWorldRoot = Objects.requireNonNull(worldRoot, "worldRoot")
                .toAbsolutePath()
                .normalize();
        Path checkpointRoot = normalizedWorldRoot
                .resolve(BUTCHERCRAFT_DIRECTORY)
                .resolve(DEVELOPMENT_CHECKPOINT_DIRECTORY)
                .normalize();
        if (!checkpointRoot.startsWith(normalizedWorldRoot)) {
            throw new IllegalArgumentException("Development checkpoint root escaped the active world root");
        }
        return checkpointRoot;
    }
}
