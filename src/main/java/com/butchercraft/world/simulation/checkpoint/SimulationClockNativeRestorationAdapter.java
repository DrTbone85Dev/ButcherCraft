package com.butchercraft.world.simulation.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotPayload;
import com.butchercraft.world.checkpoint.LegacyRecoverySourceBundle;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationAdapter;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationContext;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationPlan;
import com.butchercraft.world.checkpoint.RestorationAdapterSupport;
import com.butchercraft.world.checkpoint.RestorationSource;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationSchema;
import com.butchercraft.world.simulation.SimulationState;
import com.butchercraft.world.simulation.SimulationStateStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class SimulationClockNativeRestorationAdapter implements OwnerNativeRestorationAdapter {
    private final SimulationConfiguration configuration;

    public SimulationClockNativeRestorationAdapter(SimulationConfiguration configuration) {
        this.configuration = java.util.Objects.requireNonNull(configuration, "configuration");
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER;
    }

    @Override
    public OwnerNativeRestorationPlan prepare(
            OwnerNativeRestorationContext context,
            CheckpointOwnerSnapshotPayload snapshot
    ) {
        var descriptor = RestorationAdapterSupport.requireSnapshot(context, ownerId(), snapshot);
        SimulationState state;
        if (context.source() == RestorationSource.CHECKPOINT) {
            state = SimulationClockCheckpointSnapshotCodec.deserialize(snapshot.payloadBytes(), configuration).state();
        } else {
            var proof = RestorationAdapterSupport.requireLegacyDocument(context, ownerId(), snapshot);
            List<LegacyRecoverySourceBundle.SourceFile> files = RestorationAdapterSupport.readAndVerifyLegacySource(
                    context, ownerId(), List.of(SimulationSchema.FILE_NAME));
            state = storage().deserialize(new String(
                    RestorationAdapterSupport.bytes(files, SimulationSchema.FILE_NAME), StandardCharsets.UTF_8));
            if (state.simulationTick() != context.legacyRecoveryResult().orElseThrow()
                    .publishedDiscontinuity().inclusiveEndTick()
                    || !field(proof, "authoritative_clock_tick").equals(Long.toString(state.simulationTick()))) {
                throw new IllegalArgumentException("Legacy Clock proof does not preserve the authoritative tick");
            }
        }
        if (state.simulationTick() != context.generationManifest().authoritativeSimulationTick()) {
            throw new IllegalArgumentException("Clock native state differs from selected generation tick");
        }
        byte[] nativeBytes = storage().serialize(state).getBytes(StandardCharsets.UTF_8);
        return OwnerNativeRestorationPlan.create(
                descriptor,
                SimulationSchema.CURRENT_VERSION,
                List.of(OwnerNativeRestorationPlan.NativeFile.of(
                        SimulationSchema.FILE_NAME, SimulationSchema.FILE_NAME, nativeBytes)),
                Optional.empty()
        );
    }

    @Override
    public void verify(OwnerNativeRestorationContext context, OwnerNativeRestorationPlan plan) {
        SimulationState state = storage().deserialize(new String(
                plan.nativeFiles().getFirst().bytes(), StandardCharsets.UTF_8));
        if (state.simulationTick() != context.generationManifest().authoritativeSimulationTick()) {
            throw new IllegalArgumentException("Restored Clock state is not at the selected generation tick");
        }
    }

    private SimulationStateStorage storage() {
        return new SimulationStateStorage(Path.of("restored_simulation_state.json"), configuration);
    }

    private static String field(
            com.butchercraft.world.checkpoint.LegacyRecoveryOwnerSnapshotDocument document,
            String key
    ) {
        return document.fields().stream().filter(value -> value.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Legacy Clock proof omits " + key)).value();
    }
}
