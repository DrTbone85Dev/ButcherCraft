package com.butchercraft.world.checkpoint;

public interface CheckpointOwnerSnapshotRestorer {
    CheckpointOwnerId ownerId();

    CheckpointOwnerRestorationPreparation prepare(CheckpointOwnerRestorationRequest request);
}
