package com.butchercraft.world.checkpoint;

import java.nio.file.Path;
import java.util.List;

public interface LegacySplitRecoveryAnalysisSource {
    SplitSnapshotRecoveryAnalysisInput reloadReadOnly();

    default List<Path> sourceRoots() {
        return List.of();
    }
}
