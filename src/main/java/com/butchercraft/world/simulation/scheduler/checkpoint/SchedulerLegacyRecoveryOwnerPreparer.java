package com.butchercraft.world.simulation.scheduler.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.LegacyRecoveryOwnerSnapshotDocument;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationResult;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationSupport;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPreparedOwnerSnapshot;
import com.butchercraft.world.checkpoint.RecoverySourceSnapshot;
import com.butchercraft.world.simulation.scheduler.HistoricalCoordinationAcknowledgement;
import com.butchercraft.world.simulation.scheduler.OrdinaryWorkReconstructionProof;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryDiscontinuity;

import java.util.ArrayList;
import java.util.List;

public final class SchedulerLegacyRecoveryOwnerPreparer implements LegacySplitRecoveryOwnerPreparer {
    @Override
    public CheckpointOwnerId ownerId() {
        return CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER;
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
            SchedulerRecoveryDiscontinuity discontinuity = request.plan().recoveryDiscontinuity()
                    .orElseThrow(() -> new IllegalArgumentException("Split recovery requires a Scheduler discontinuity"));
            long nextAdmission = request.plan().nextNormalSchedulerAdmissionTick().orElseThrow();
            if (discontinuity.sourceSchedulerTick() != request.plan().schedulerLastNormallyFinalizedTick()
                    || discontinuity.authoritativeClockTick() != request.plan().authoritativeClockTick()
                    || discontinuity.nextNormalAdmissionTick() != nextAdmission) {
                throw new IllegalArgumentException("Scheduler recovery boundary does not match the exact analysis");
            }

            List<LegacyRecoveryOwnerSnapshotDocument.Field> fields = new ArrayList<>();
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "acknowledgement_count",
                    request.plan().historicalAcknowledgements().size()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "admission_cursor_tick",
                    request.plan().authoritativeClockTick()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "discontinuity_content_digest",
                    discontinuity.contentDigest()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "discontinuity_identity",
                    discontinuity.discontinuityIdentity()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "discontinuity_range",
                    discontinuity.inclusiveMissingStartTick() + "-" + discontinuity.inclusiveMissingEndTick()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("handler_invocation_count", 0));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                    "last_normally_finalized_tick",
                    request.plan().schedulerLastNormallyFinalizedTick()
            ));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("next_normal_admission_tick", nextAdmission));
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("synthetic_tick_count", 0));
            for (int index = 0; index < request.plan().historicalAcknowledgements().size(); index++) {
                HistoricalCoordinationAcknowledgement acknowledgement =
                        request.plan().historicalAcknowledgements().get(index);
                String prefix = "acknowledgement." + index + ".";
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "content_digest",
                        acknowledgement.contentDigest()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "execution_operation_identity",
                        acknowledgement.executionOperationIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "identity",
                        acknowledgement.acknowledgementIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        prefix + "terminal_outcome",
                        acknowledgement.terminalOutcome().name()
                ));
            }
            List<OrdinaryWorkReconstructionProof> ordinary = request.plan().ordinaryWorkProofs().stream()
                    .filter(proof -> proof.eligibility()
                            == OrdinaryWorkReconstructionProof.Eligibility.ORDINARY_WORK_RECONSTRUCTABLE)
                    .toList();
            fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of("ordinary_work_count", ordinary.size()));
            for (int index = 0; index < ordinary.size(); index++) {
                OrdinaryWorkReconstructionProof proof = ordinary.get(index);
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        "ordinary_work." + index + ".candidate_identity",
                        proof.candidateIdentity()
                ));
                fields.add(LegacyRecoveryOwnerSnapshotDocument.Field.of(
                        "ordinary_work." + index + ".submission_sequence",
                        proof.authoritativeSubmissionSequence().orElseThrow()
                ));
            }
            LegacyRecoveryOwnerSnapshotDocument document = LegacyRecoveryOwnerSnapshotDocument.create(
                    ownerId(),
                    request,
                    source,
                    "butchercraft:recovery_owner_state/scheduler_recovery_boundary",
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
