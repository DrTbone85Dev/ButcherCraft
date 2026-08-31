package com.butchercraft.world.checkpoint;

public interface LegacySplitRecoveryOwnerPreparer {
    CheckpointOwnerId ownerId();

    LegacySplitRecoveryOwnerPreparationResult prepare(LegacySplitRecoveryOwnerPreparationRequest request);
}
