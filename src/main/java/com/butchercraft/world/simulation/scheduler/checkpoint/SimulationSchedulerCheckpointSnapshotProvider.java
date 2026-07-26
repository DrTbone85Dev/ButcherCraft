package com.butchercraft.world.simulation.scheduler.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointCapturedOwnerSnapshot;
import com.butchercraft.world.checkpoint.CheckpointFailure;
import com.butchercraft.world.checkpoint.CheckpointFailureCode;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCaptureResult;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotContext;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotPayload;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotProvider;
import com.butchercraft.world.checkpoint.CheckpointOwnerValidationMetadata;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.checkpoint.CheckpointSnapshotParticipation;
import com.butchercraft.world.checkpoint.OwnerSnapshotDescriptor;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SimulationSchedulerCheckpointSnapshotProvider implements CheckpointOwnerSnapshotProvider {
    private final SimulationSchedulerManager manager;

    public SimulationSchedulerCheckpointSnapshotProvider(SimulationSchedulerManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return SimulationSchedulerCheckpointSnapshotCodec.OWNER_ID;
    }

    @Override
    public CheckpointOwnerSnapshotCaptureResult capture(CheckpointOwnerSnapshotContext context) {
        Objects.requireNonNull(context, "context");
        try {
            if (manager.lastFinalizedSimulationTick() != context.authoritativeSimulationTick()) {
                return CheckpointOwnerSnapshotCaptureResult.failed(ownerId(), List.of(failure(
                        CheckpointFailureCode.CLOCK_SCHEDULER_TICK_MISMATCH,
                        "lastFinalizedSimulationTick",
                        "Scheduler finalized tick does not match checkpoint authoritative tick"
                )));
            }
            manager.validateForPersistence();
            byte[] payload = SimulationSchedulerCheckpointSnapshotCodec.serialize(manager);
            String digest = CheckpointSnapshotDigest.sha256(payload);
            String snapshotIdentity = "butchercraft:simulation_scheduler/snapshot/%020d/%s".formatted(
                    manager.lastFinalizedSimulationTick(),
                    CheckpointSnapshotDigest.shortHex(digest)
            );
            String configurationIdentity = SimulationSchedulerCheckpointSnapshotCodec.configurationIdentity(manager);
            OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                    ownerId(),
                    SimulationSchedulerCheckpointSnapshotCodec.SNAPSHOT_SCHEMA_VERSION,
                    snapshotIdentity,
                    digest,
                    CheckpointSnapshotParticipation.REQUIRED,
                    configurationIdentity,
                    context.worldIdentityRoot(),
                    context.generationId(),
                    manager.lastFinalizedSimulationTick(),
                    manager.nextSubmissionSequence()
            );
            CheckpointOwnerSnapshotPayload snapshotPayload = new CheckpointOwnerSnapshotPayload(
                    descriptor,
                    payload,
                    digest
            );
            CheckpointOwnerValidationMetadata metadata = new CheckpointOwnerValidationMetadata(
                    ownerId(),
                    Map.of(
                            CheckpointOwnerSnapshotCoordinator.SCHEDULER_FINALIZED_TICK_KEY,
                            Long.toString(manager.lastFinalizedSimulationTick()),
                            CheckpointOwnerSnapshotCoordinator.CONFIGURATION_IDENTITY_KEY,
                            configurationIdentity,
                            CheckpointOwnerSnapshotCoordinator.SNAPSHOT_IDENTITY_KEY,
                            snapshotIdentity
                    )
            );
            return CheckpointOwnerSnapshotCaptureResult.captured(
                    new CheckpointCapturedOwnerSnapshot(snapshotPayload, metadata)
            );
        } catch (IllegalStateException exception) {
            return CheckpointOwnerSnapshotCaptureResult.failed(ownerId(), List.of(failure(
                    CheckpointFailureCode.SCHEDULER_INVARIANT_VIOLATION,
                    ownerId().value(),
                    "Scheduler state is not valid for checkpoint capture"
            )));
        } catch (RuntimeException exception) {
            return CheckpointOwnerSnapshotCaptureResult.failed(ownerId(), List.of(failure(
                    CheckpointFailureCode.OWNER_SNAPSHOT_SERIALIZATION_FAILURE,
                    ownerId().value(),
                    "Scheduler owner could not serialize its checkpoint snapshot"
            )));
        }
    }

    private static CheckpointFailure failure(CheckpointFailureCode code, String field, String message) {
        return new CheckpointFailure(code, field, message);
    }
}
