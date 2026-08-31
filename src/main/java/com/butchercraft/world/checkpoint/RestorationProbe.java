package com.butchercraft.world.checkpoint;

import java.util.Optional;

@FunctionalInterface
public interface RestorationProbe {
    RestorationProbe NONE = (phase, ownerId, logicalFile) -> { };

    void reached(
            RestorationPhase phase,
            Optional<CheckpointOwnerId> ownerId,
            Optional<String> logicalFile
    );
}
