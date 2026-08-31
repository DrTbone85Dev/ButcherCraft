package com.butchercraft.world.simulation.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.LegacyRecoveryOwnerSnapshotDocument;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationRequest;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationResult;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparationSupport;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryOwnerPreparer;
import com.butchercraft.world.checkpoint.LegacySplitRecoveryPreparedOwnerSnapshot;
import com.butchercraft.world.checkpoint.RecoverySourceSnapshot;

import java.util.List;

public final class SimulationClockLegacyRecoveryOwnerPreparer implements LegacySplitRecoveryOwnerPreparer {
    @Override
    public CheckpointOwnerId ownerId() {
        return CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER;
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
            if (source.representedSimulationTick() != request.plan().authoritativeClockTick()) {
                throw new IllegalArgumentException("Clock source does not represent the authoritative recovery tick");
            }
            LegacyRecoveryOwnerSnapshotDocument document = LegacyRecoveryOwnerSnapshotDocument.create(
                    ownerId(),
                    request,
                    source,
                    "butchercraft:recovery_owner_state/clock_preserved",
                    List.of(
                            LegacyRecoveryOwnerSnapshotDocument.Field.of(
                                    "authoritative_clock_tick",
                                    request.plan().authoritativeClockTick()
                            ),
                            LegacyRecoveryOwnerSnapshotDocument.Field.of("clock_rollback", false),
                            LegacyRecoveryOwnerSnapshotDocument.Field.of("synthetic_tick_count", 0)
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
            return LegacySplitRecoveryOwnerPreparationSupport.failure(ownerId(), exception);
        }
    }
}
