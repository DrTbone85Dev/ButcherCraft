package com.butchercraft.world.execution.checkpoint;

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

public final class ExecutionLegacyRecoveryOwnerPreparer implements LegacySplitRecoveryOwnerPreparer {
    @Override
    public CheckpointOwnerId ownerId() {
        return LegacySplitRecoveryParticipants.EXECUTION;
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
                    "existing_terminal_result_count",
                    request.plan().historicalAcknowledgements().size()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("new_operation_count", 0));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "preserved_authorized_child_count",
                    request.plan().preservedAuthorizedWork().size()
            ));
            for (int index = 0; index < request.plan().historicalAcknowledgements().size(); index++) {
                HistoricalCoordinationAcknowledgement acknowledgement =
                        request.plan().historicalAcknowledgements().get(index);
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        "terminal_result." + index + ".content_digest",
                        acknowledgement.executionResultContentDigest()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        "terminal_result." + index + ".identity",
                        acknowledgement.executionResultIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        "terminal_result." + index + ".outcome",
                        acknowledgement.terminalOutcome().name()
                ));
            }
            for (int index = 0; index < request.plan().preservedAuthorizedWork().size(); index++) {
                PreservedAuthorizedWork work = request.plan().preservedAuthorizedWork().get(index);
                String prefix = "preserved_child." + index + ".";
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(prefix + "automatic_admission", false));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(prefix + "child_identity", work.childIdentity()));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(prefix + "child_sequence", work.childSequence()));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "execution_operation_identity",
                        work.executionOperationIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "machine_run_generation",
                        work.machineRunGeneration()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "machine_run_identity",
                        work.machineRunIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "machine_run_lifecycle",
                        "SUSPENDED_RESTART_REQUIRED"
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(prefix + "operation_state", "AUTHORIZED"));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(prefix + "scheduler_invoked", false));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(prefix + "scheduler_work_created", false));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(prefix + "terminal", false));
            }
            LegacyRecoveryOwnerSnapshotDocument document = LegacyRecoveryOwnerSnapshotDocument.create(
                    ownerId(),
                    request,
                    source,
                    "butchercraft:recovery_owner_state/execution_policy_b",
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
