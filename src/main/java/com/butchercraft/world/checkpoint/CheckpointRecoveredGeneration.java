package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

public record CheckpointRecoveredGeneration(
        CheckpointGenerationRecord generationRecord,
        List<CheckpointOwnerSnapshotPayload> ownerSnapshots
) {
    public CheckpointRecoveredGeneration {
        generationRecord = Objects.requireNonNull(generationRecord, "generationRecord");
        ownerSnapshots = Objects.requireNonNull(ownerSnapshots, "ownerSnapshots").stream()
                .map(snapshot -> Objects.requireNonNull(snapshot, "ownerSnapshot"))
                .sorted()
                .toList();
    }

    public CheckpointGenerationManifest manifest() {
        return generationRecord.manifest();
    }
}
