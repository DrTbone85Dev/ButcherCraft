package com.butchercraft.world.checkpoint;

public interface OwnerNativeRestorationAdapter {
    CheckpointOwnerId ownerId();

    OwnerNativeRestorationPlan prepare(
            OwnerNativeRestorationContext context,
            CheckpointOwnerSnapshotPayload snapshot
    );

    void verify(OwnerNativeRestorationContext context, OwnerNativeRestorationPlan plan);

    default void reconcileProjection(
            OwnerNativeRestorationContext context,
            OwnerNativeRestorationPlan plan
    ) {
    }
}
