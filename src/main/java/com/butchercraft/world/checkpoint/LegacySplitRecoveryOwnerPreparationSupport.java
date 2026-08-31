package com.butchercraft.world.checkpoint;

import java.util.Objects;

public final class LegacySplitRecoveryOwnerPreparationSupport {
    private LegacySplitRecoveryOwnerPreparationSupport() {
    }

    public static RecoverySourceSnapshot requireExactSource(
            LegacySplitRecoveryOwnerPreparationRequest request,
            CheckpointOwnerId ownerId
    ) {
        Objects.requireNonNull(request, "request");
        RecoverySourceSnapshot source = request.requireSource(ownerId);
        if (!source.ownerValidated() || !source.ownerSchemaSupported()) {
            throw new IllegalArgumentException("Owner source is not validated and schema-supported: " + ownerId.value());
        }
        if (!source.worldIdentityRoot().equals(request.plan().worldIdentityRoot())) {
            throw new IllegalArgumentException("Owner source World Identity does not match recovery plan");
        }
        if (!request.authorization().sourceSnapshots().contains(source)) {
            throw new IllegalArgumentException("Owner source is absent from exact operator authorization");
        }
        return source;
    }

    public static LegacySplitRecoveryOwnerPreparationResult failure(
            CheckpointOwnerId ownerId,
            RuntimeException exception
    ) {
        return LegacySplitRecoveryOwnerPreparationResult.failed(
                ownerId,
                new LegacySplitRecoveryPublicationFailure(
                        LegacySplitRecoveryPublicationFailure.Code.OWNER_PREPARATION_FAILED,
                        ownerId.value(),
                        exception.getMessage() == null ? "Owner preparation failed" : exception.getMessage()
                )
        );
    }
}
