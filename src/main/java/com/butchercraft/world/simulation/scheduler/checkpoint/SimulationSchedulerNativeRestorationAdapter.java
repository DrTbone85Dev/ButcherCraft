package com.butchercraft.world.simulation.scheduler.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotPayload;
import com.butchercraft.world.checkpoint.LegacyRecoverySourceBundle;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationAdapter;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationContext;
import com.butchercraft.world.checkpoint.OwnerNativeRestorationPlan;
import com.butchercraft.world.checkpoint.RestorationAdapterSupport;
import com.butchercraft.world.checkpoint.RestorationSource;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryState;
import com.butchercraft.world.simulation.scheduler.SchedulerSchema;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.persistence.SchedulerRecoveryStorage;
import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SimulationSchedulerNativeRestorationAdapter implements OwnerNativeRestorationAdapter {
    private final SimulationWorkHandlerRegistry handlerRegistry;

    public SimulationSchedulerNativeRestorationAdapter(SimulationWorkHandlerRegistry handlerRegistry) {
        this.handlerRegistry = java.util.Objects.requireNonNull(handlerRegistry, "handlerRegistry");
    }

    @Override
    public CheckpointOwnerId ownerId() {
        return CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER;
    }

    @Override
    public OwnerNativeRestorationPlan prepare(
            OwnerNativeRestorationContext context,
            CheckpointOwnerSnapshotPayload snapshot
    ) {
        var descriptor = RestorationAdapterSupport.requireSnapshot(context, ownerId(), snapshot);
        SimulationSchedulerManager manager;
        Optional<SchedulerRecoveryState> recoveryState = Optional.empty();
        if (context.source() == RestorationSource.CHECKPOINT) {
            var parsed = SimulationSchedulerCheckpointSnapshotCodec.deserialize(
                    snapshot.payloadBytes(), handlerRegistry);
            manager = parsed.manager();
            recoveryState = parsed.recoveryState();
        } else {
            var proof = RestorationAdapterSupport.requireLegacyDocument(context, ownerId(), snapshot);
            List<LegacyRecoverySourceBundle.SourceFile> files = RestorationAdapterSupport.readAndVerifyLegacySource(
                    context, ownerId(), List.of(SchedulerSchema.FILE_NAME));
            SimulationSchedulerManager source = storage(0L).deserialize(new String(
                    RestorationAdapterSupport.bytes(files, SchedulerSchema.FILE_NAME), StandardCharsets.UTF_8));
            var result = context.legacyRecoveryResult().orElseThrow();
            long cursor = result.publishedDiscontinuity().inclusiveEndTick();
            if (!field(proof, "admission_cursor_tick").equals(Long.toString(cursor))
                    || source.lastFinalizedSimulationTick()
                    != result.publishedDiscontinuity().inclusiveStartTick() - 1L) {
                throw new IllegalArgumentException("Legacy Scheduler proof does not bind the admitted discontinuity");
            }
            manager = new SimulationSchedulerManager(
                    source.stageRegistry(),
                    handlerRegistry,
                    source.registry(),
                    source.runtimeRecords(),
                    source.nextSubmissionSequence(),
                    cursor
            );
            recoveryState = Optional.of(SchedulerRecoveryState.fromResult(result));
        }
        if (manager.lastFinalizedSimulationTick() != context.generationManifest().authoritativeSimulationTick()) {
            throw new IllegalArgumentException("Scheduler native state differs from selected generation tick");
        }
        List<OwnerNativeRestorationPlan.NativeFile> nativeFiles = new ArrayList<>();
        nativeFiles.add(OwnerNativeRestorationPlan.NativeFile.of(
                SchedulerSchema.FILE_NAME,
                SchedulerSchema.FILE_NAME,
                storage(manager.lastFinalizedSimulationTick()).serialize(manager).getBytes(StandardCharsets.UTF_8)
        ));
        recoveryState.ifPresent(state -> nativeFiles.add(OwnerNativeRestorationPlan.NativeFile.of(
                SchedulerSchema.RECOVERY_FILE_NAME,
                SchedulerSchema.RECOVERY_FILE_NAME,
                recoveryStorage().serialize(state).getBytes(StandardCharsets.UTF_8)
        )));
        return OwnerNativeRestorationPlan.create(
                descriptor, SchedulerSchema.CURRENT_VERSION, nativeFiles, Optional.empty());
    }

    @Override
    public void verify(OwnerNativeRestorationContext context, OwnerNativeRestorationPlan plan) {
        OwnerNativeRestorationPlan.NativeFile scheduler = plan.nativeFiles().stream()
                .filter(file -> file.logicalName().equals(SchedulerSchema.FILE_NAME)).findFirst().orElseThrow();
        SimulationSchedulerManager manager = storage(0L).deserialize(
                new String(scheduler.bytes(), StandardCharsets.UTF_8));
        if (manager.lastFinalizedSimulationTick() != context.generationManifest().authoritativeSimulationTick()) {
            throw new IllegalArgumentException("Restored Scheduler state is not at the selected generation tick");
        }
        plan.nativeFiles().stream().filter(file -> file.logicalName().equals(SchedulerSchema.RECOVERY_FILE_NAME))
                .findFirst().ifPresent(file -> recoveryStorage().deserialize(
                        new String(file.bytes(), StandardCharsets.UTF_8)));
    }

    private SimulationSchedulerStorage storage(long tick) {
        return new SimulationSchedulerStorage(Path.of("restored_simulation_scheduler.json"), handlerRegistry, tick);
    }

    private SchedulerRecoveryStorage recoveryStorage() {
        return new SchedulerRecoveryStorage(Path.of("restored_simulation_scheduler_recovery.json"));
    }

    private static String field(
            com.butchercraft.world.checkpoint.LegacyRecoveryOwnerSnapshotDocument document,
            String key
    ) {
        return document.fields().stream().filter(value -> value.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Legacy Scheduler proof omits " + key)).value();
    }
}
