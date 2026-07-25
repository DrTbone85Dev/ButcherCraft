package com.butchercraft.world.checkpoint;

public interface CheckpointOwnerSnapshotProvider {
    CheckpointOwnerId ownerId();

    CheckpointOwnerSnapshotCaptureResult capture(CheckpointOwnerSnapshotContext context);
}
