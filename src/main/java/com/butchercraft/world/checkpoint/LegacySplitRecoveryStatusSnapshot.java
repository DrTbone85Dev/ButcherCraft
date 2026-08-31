package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LegacySplitRecoveryStatusSnapshot(
        State state,
        Optional<LegacySplitRecoveryIdentity> lastRecoveryIdentity,
        Optional<CheckpointGenerationId> committedRecoveryGeneration,
        List<RecoveryAuthorityBlock> remainingAuthorityBlocks,
        boolean worldFullyMutationUnblocked,
        String detail
) {
    public LegacySplitRecoveryStatusSnapshot {
        state = Objects.requireNonNull(state, "state");
        lastRecoveryIdentity = Objects.requireNonNull(lastRecoveryIdentity, "lastRecoveryIdentity");
        committedRecoveryGeneration = Objects.requireNonNull(
                committedRecoveryGeneration,
                "committedRecoveryGeneration"
        );
        remainingAuthorityBlocks = Objects.requireNonNull(
                remainingAuthorityBlocks,
                "remainingAuthorityBlocks"
        ).stream().sorted().toList();
        detail = CheckpointValidation.text(detail, "recoveryStatusDetail");
    }

    public enum State {
        NO_SPLIT_DETECTED,
        ANALYSIS_AVAILABLE,
        AUTHORIZATION_REQUIRED,
        AUTHORIZATION_STALE,
        RECOVERY_PREPARING,
        RECOVERY_COMMITTED,
        RECOVERY_BLOCKED
    }
}
