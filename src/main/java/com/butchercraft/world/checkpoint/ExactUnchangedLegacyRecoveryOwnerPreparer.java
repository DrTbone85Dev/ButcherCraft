package com.butchercraft.world.checkpoint;

import java.util.List;
import java.util.Objects;

/** Packages exact owner-supplied source identity without interpreting or changing owner state. */
public final class ExactUnchangedLegacyRecoveryOwnerPreparer implements LegacySplitRecoveryOwnerPreparer {
    private final CheckpointOwnerId ownerId;

    public ExactUnchangedLegacyRecoveryOwnerPreparer(CheckpointOwnerId ownerId) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        if (ownerId.equals(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER)
                || ownerId.equals(LegacySplitRecoveryParticipants.EXECUTION)
                || ownerId.equals(LegacySplitRecoveryParticipants.WORKSTATION)
                || ownerId.equals(LegacySplitRecoveryParticipants.PLANNING)
                || ownerId.equals(LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY)) {
            throw new IllegalArgumentException("Recovery-mutated owners require their owner-specific preparer");
        }
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return ownerId;
    }

    @Override
    public LegacySplitRecoveryOwnerPreparationResult prepare(
            LegacySplitRecoveryOwnerPreparationRequest request
    ) {
        try {
            RecoverySourceSnapshot source = LegacySplitRecoveryOwnerPreparationSupport.requireExactSource(
                    request,
                    ownerId
            );
            LegacyRecoveryOwnerSnapshotDocument document = LegacyRecoveryOwnerSnapshotDocument.create(
                    ownerId,
                    request,
                    source,
                    "butchercraft:recovery_owner_state/exact_source_unchanged",
                    List.of(
                            LegacyRecoveryOwnerSnapshotDocument.Field.of(
                                    "authoritative_source_content_digest",
                                    source.contentDigest()
                            ),
                            LegacyRecoveryOwnerSnapshotDocument.Field.of(
                                    "authoritative_source_snapshot_identity",
                                    source.snapshotIdentity()
                            ),
                            LegacyRecoveryOwnerSnapshotDocument.Field.of("domain_mutation_count", 0),
                            LegacyRecoveryOwnerSnapshotDocument.Field.of("state_changed", false)
                    )
            );
            return LegacySplitRecoveryOwnerPreparationResult.prepared(
                    LegacySplitRecoveryPreparedOwnerSnapshot.fromDocument(
                            document,
                            request,
                            source.ownerRevisionOrSequence()
                    )
            );
        } catch (RuntimeException exception) {
            return LegacySplitRecoveryOwnerPreparationSupport.failure(ownerId, exception);
        }
    }
}
