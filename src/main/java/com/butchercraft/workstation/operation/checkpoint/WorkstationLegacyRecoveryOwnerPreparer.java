package com.butchercraft.workstation.operation.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.LegacyRecoveryOwnerSnapshotDocument;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationResult;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationSupport;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryParticipants;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPreparedOwnerSnapshot;
import com.butchercraft.world.checkpoint.PreservedAuthorizedWork;
import com.butchercraft.world.checkpoint.RecoverySourceSnapshot;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAcknowledgement;

import java.util.ArrayList;
import java.util.List;

public final class WorkstationLegacyRecoveryOwnerPreparer implements LegacySplitRecoveryOwnerPreparer {
    @Override
    public CheckpointOwnerId ownerId() {
        return LegacySplitRecoveryParticipants.WORKSTATION;
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
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("inventory_effect_count", 0));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "inventory_projection_source_digest",
                    source.contentDigest()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("new_instance_count", 0));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "preserved_instance_count",
                    request.plan().preservedAuthorizedWork().size()
            ));
            for (int index = 0; index < request.plan().historicalAcknowledgements().size(); index++) {
                HistoricalCoordinationAcknowledgement acknowledgement =
                        request.plan().historicalAcknowledgements().get(index);
                if (acknowledgement.ownerResultIdentity().isPresent()) {
                    fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                            "owner_result." + index + ".content_digest",
                            acknowledgement.ownerResultContentDigest().orElseThrow()
                    ));
                    fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                            "owner_result." + index + ".identity",
                            acknowledgement.ownerResultIdentity().orElseThrow()
                    ));
                }
            }
            for (int index = 0; index < request.plan().preservedAuthorizedWork().size(); index++) {
                PreservedAuthorizedWork work = request.plan().preservedAuthorizedWork().get(index);
                String prefix = "machine." + index + ".";
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "instance_generation",
                        work.workstationInstanceGeneration()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "instance_identity",
                        work.workstationInstanceIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "operating_state",
                        "RESTART_REQUIRED"
                ));
            }
            LegacyRecoveryOwnerSnapshotDocument document = LegacyRecoveryOwnerSnapshotDocument.create(
                    ownerId(),
                    request,
                    source,
                    "butchercraft:recovery_owner_state/workstation_policy_b",
                    fields
            );
            long ownerSequence = request.plan().preservedAuthorizedWork().isEmpty()
                    ? source.ownerRevisionOrSequence()
                    : Math.addExact(source.ownerRevisionOrSequence(), 1L);
            return LegacySplitRecoveryOwnerPreparationResult.prepared(
                    LegacySplitRecoveryPreparedOwnerSnapshot.fromDocument(document, request, ownerSequence)
            );
        } catch (RuntimeException exception) {
            return LegacySplitRecoveryOwnerPreparationSupport.failure(ownerId(), exception);
        }
    }
}
