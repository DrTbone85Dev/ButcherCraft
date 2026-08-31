package com.butchercraft.world.planning.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.LegacyRecoveryOwnerSnapshotDocument;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationResult;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationSupport;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPreparedOwnerSnapshot;
import com.butchercraft.world.checkpoint.RecoverySourceSnapshot;
import com.butchercraft.world.planning.PlanningRecoveryAuthorityBlock;

import java.util.ArrayList;
import java.util.List;

public final class PlanningLegacyRecoveryOwnerPreparer implements LegacySplitRecoveryOwnerPreparer {
    @Override
    public CheckpointOwnerId ownerId() {
        return LegacySplitRecoveryParticipants.PLANNING;
    }

    @Override
    public LegacySplitRecoveryOwnerPreparationResult prepare(
            LegacySplitRecoveryOwnerPreparationRequest request
    ) {
        try {
            RecoverySourceSnapshot source = LegacySplitRecoveryOwnerPreparationSupport.requireExactSource(
                    request,
                    ownerId()
            );
            List<LegacyRecoveryOwnerSnapshotDocument.Field> fields = new ArrayList<>();
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "authority_block_count",
                    request.plan().planningAuthorityBlocks().size()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("planning_replay_count", 0));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "whole_world_mutation_blocked",
                    request.plan().planningAuthorityBlocks().stream()
                            .anyMatch(PlanningRecoveryAuthorityBlock::wholeWorldMutationBlocked)
            ));
            for (int index = 0; index < request.plan().planningAuthorityBlocks().size(); index++) {
                PlanningRecoveryAuthorityBlock block = request.plan().planningAuthorityBlocks().get(index);
                String prefix = "authority_block." + index + ".";
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "content_digest",
                        block.contentDigest()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "dependency_closure_proven",
                        block.dependencyClosureProven()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "identity",
                        block.blockIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "planning_work_identity",
                        block.planningWorkIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "scope",
                        block.dependencyScope().name()
                ));
            }
            LegacyRecoveryOwnerSnapshotDocument document = LegacyRecoveryOwnerSnapshotDocument.create(
                    ownerId(),
                    request,
                    source,
                    "butchercraft:recovery_owner_state/planning_recovery_blocked",
                    fields
            );
            return LegacySplitRecoveryOwnerPreparationResult.prepared(
                    LegacySplitRecoveryPreparedOwnerSnapshot.fromDocument(
                            document,
                            request,
                            Math.addExact(source.ownerRevisionOrSequence(), 1L)
                    )
            );
        } catch (RuntimeException exception) {
            return LegacySplitRecoveryOwnerPreparationSupport.failure(ownerId(), exception);
        }
    }
}
