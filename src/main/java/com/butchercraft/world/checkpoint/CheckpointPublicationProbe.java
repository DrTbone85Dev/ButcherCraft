package com.butchercraft.world.checkpoint;

import java.io.IOException;

@FunctionalInterface
interface CheckpointPublicationProbe {
    CheckpointPublicationProbe NONE = phase -> {
    };

    void reached(CheckpointPublicationPhase phase) throws IOException;
}
