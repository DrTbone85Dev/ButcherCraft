package com.butchercraft.world.checkpoint;

import java.io.IOException;

@FunctionalInterface
public interface LegacySplitRecoveryPublicationProbe {
    LegacySplitRecoveryPublicationProbe NONE = phase -> {
    };

    void reached(LegacySplitRecoveryPublicationPhase phase) throws IOException;
}
