package com.butchercraft.world.checkpoint;

import java.util.ArrayList;
import java.util.List;

public final class CheckpointRecoveryLegacyRecoveryOwnerPreparer implements LegacySplitRecoveryOwnerPreparer {
    @Override
    public CheckpointOwnerId ownerId() {
        return LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY;
    }

    @Override
    public LegacySplitRecoveryOwnerPreparationResult prepare(
            LegacySplitRecoveryOwnerPreparationRequest request
    ) {
        try {
            List<LegacyRecoveryOwnerSnapshotDocument.Field> fields = new ArrayList<>();
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "analysis_digest",
                    request.plan().analysisDigest()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "authority_block_count",
                    request.plan().authorityBlocks().size()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "authorization_content_digest",
                    request.authorization().contentDigest()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "authorization_identity",
                    request.authorization().authorizationIdentity()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "operator_authority",
                    request.authorization().operatorEvidence().authority().name()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "recovery_identity",
                    request.plan().recoveryIdentity().value()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "source_snapshot_count",
                    request.plan().sourceSnapshots().size()
            ));
            LegacyRecoveryOwnerSnapshotDocument document = new LegacyRecoveryOwnerSnapshotDocument(
                    LegacyRecoveryOwnerSnapshotDocument.CURRENT_SCHEMA,
                    ownerId(),
                    request.plan().recoveryIdentity(),
                    request.plan().analysisDigest(),
                    request.plan().recoveryIdentity().value(),
                    request.plan().analysisDigest(),
                    0L,
                    request.plan().authoritativeClockTick(),
                    "butchercraft:recovery_owner_state/publication_intent",
                    fields
            );
            return LegacySplitRecoveryOwnerPreparationResult.prepared(
                    LegacySplitRecoveryPreparedOwnerSnapshot.fromDocument(
                            document,
                            request,
                            request.generationId().committedSequence()
                    )
            );
        } catch (RuntimeException exception) {
            return LegacySplitRecoveryOwnerPreparationSupport.failure(ownerId(), exception);
        }
    }
}
