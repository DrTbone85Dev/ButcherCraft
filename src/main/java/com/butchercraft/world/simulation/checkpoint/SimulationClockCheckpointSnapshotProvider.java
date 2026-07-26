package com.butchercraft.world.simulation.checkpoint;

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
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationState;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SimulationClockCheckpointSnapshotProvider implements CheckpointOwnerSnapshotProvider {
    private final SimulationClock clock;

    public SimulationClockCheckpointSnapshotProvider(SimulationClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return SimulationClockCheckpointSnapshotCodec.OWNER_ID;
    }

    @Override
    public CheckpointOwnerSnapshotCaptureResult capture(CheckpointOwnerSnapshotContext context) {
        Objects.requireNonNull(context, "context");
        try {
            SimulationState state = clock.state();
            if (state.simulationTick() != context.authoritativeSimulationTick()) {
                return CheckpointOwnerSnapshotCaptureResult.failed(ownerId(), List.of(failure(
                        CheckpointFailureCode.INVALID_SIMULATION_TICK_PROGRESSION,
                        "simulationTick",
                        "Clock snapshot tick does not match checkpoint authoritative tick"
                )));
            }
            byte[] payload = SimulationClockCheckpointSnapshotCodec.serialize(state, clock.configuration());
            String digest = CheckpointSnapshotDigest.sha256(payload);
            String snapshotIdentity = "butchercraft:simulation_clock/snapshot/%020d/%s".formatted(
                    state.simulationTick(),
                    CheckpointSnapshotDigest.shortHex(digest)
            );
            String configurationIdentity = SimulationClockCheckpointSnapshotCodec.configurationIdentity(
                    clock.configuration()
            );
            OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                    ownerId(),
                    SimulationClockCheckpointSnapshotCodec.SNAPSHOT_SCHEMA_VERSION,
                    snapshotIdentity,
                    digest,
                    CheckpointSnapshotParticipation.REQUIRED,
                    configurationIdentity,
                    context.worldIdentityRoot(),
                    context.generationId(),
                    state.simulationTick(),
                    state.simulationTick()
            );
            CheckpointOwnerSnapshotPayload snapshotPayload = new CheckpointOwnerSnapshotPayload(
                    descriptor,
                    payload,
                    digest
            );
            CheckpointOwnerValidationMetadata metadata = new CheckpointOwnerValidationMetadata(
                    ownerId(),
                    Map.of(
                            CheckpointOwnerSnapshotCoordinator.CLOCK_TICK_KEY,
                            Long.toString(state.simulationTick()),
                            CheckpointOwnerSnapshotCoordinator.CONFIGURATION_IDENTITY_KEY,
                            configurationIdentity,
                            CheckpointOwnerSnapshotCoordinator.SNAPSHOT_IDENTITY_KEY,
                            snapshotIdentity
                    )
            );
            return CheckpointOwnerSnapshotCaptureResult.captured(
                    new CheckpointCapturedOwnerSnapshot(snapshotPayload, metadata)
            );
        } catch (RuntimeException exception) {
            return CheckpointOwnerSnapshotCaptureResult.failed(ownerId(), List.of(failure(
                    CheckpointFailureCode.OWNER_SNAPSHOT_SERIALIZATION_FAILURE,
                    ownerId().value(),
                    "Clock owner could not serialize its checkpoint snapshot"
            )));
        }
    }

    private static CheckpointFailure failure(CheckpointFailureCode code, String field, String message) {
        return new CheckpointFailure(code, field, message);
    }
}
