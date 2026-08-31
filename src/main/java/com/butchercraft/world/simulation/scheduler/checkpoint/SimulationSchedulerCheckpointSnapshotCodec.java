package com.butchercraft.world.simulation.scheduler.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointSnapshotDigest;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SchedulerRecoveryState;
import com.butchercraft.world.simulation.scheduler.SimulationStageDefinition;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.butchercraft.world.simulation.scheduler.persistence.SchedulerRecoveryStorage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

final class SimulationSchedulerCheckpointSnapshotCodec {
    static final int SNAPSHOT_SCHEMA_VERSION = 1;
    static final CheckpointOwnerId OWNER_ID = CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    private static final String SNAPSHOT_SCHEMA_VERSION_FIELD = "snapshot_schema_version";
    private static final String OWNER_ID_FIELD = "owner_id";
    private static final String CONFIGURATION_IDENTITY_FIELD = "configuration_identity";
    private static final String SCHEDULER_STATE_FIELD = "scheduler_state";
    private static final String RECOVERY_STATE_FIELD = "recovery_state";

    private SimulationSchedulerCheckpointSnapshotCodec() {
    }

    static byte[] serialize(SimulationSchedulerManager manager) {
        return serialize(manager, Optional.empty());
    }

    static byte[] serialize(
            SimulationSchedulerManager manager,
            Optional<SchedulerRecoveryState> recoveryState
    ) {
        Objects.requireNonNull(manager, "manager").validateForPersistence();
        Objects.requireNonNull(recoveryState, "recoveryState");
        SimulationSchedulerStorage storage = storage(manager.handlerRegistry(), manager.lastFinalizedSimulationTick());
        JsonObject root = new JsonObject();
        root.addProperty(SNAPSHOT_SCHEMA_VERSION_FIELD, SNAPSHOT_SCHEMA_VERSION);
        root.addProperty(OWNER_ID_FIELD, OWNER_ID.value());
        root.addProperty(CONFIGURATION_IDENTITY_FIELD, configurationIdentity(manager));
        root.add(SCHEDULER_STATE_FIELD, JsonParser.parseString(storage.serialize(manager)).getAsJsonObject());
        recoveryState.ifPresent(state -> root.add(
                RECOVERY_STATE_FIELD,
                JsonParser.parseString(recoveryStorage().serialize(state)).getAsJsonObject()
        ));
        return (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static ParsedSchedulerSnapshot deserialize(
            byte[] bytes,
            SimulationWorkHandlerRegistry handlerRegistry
    ) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        try {
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            int schemaVersion = root.get(SNAPSHOT_SCHEMA_VERSION_FIELD).getAsInt();
            if (schemaVersion != SNAPSHOT_SCHEMA_VERSION) {
                throw new UnsupportedSnapshotSchemaException(schemaVersion);
            }
            String owner = root.get(OWNER_ID_FIELD).getAsString();
            if (!OWNER_ID.value().equals(owner)) {
                throw new IllegalArgumentException("Scheduler snapshot owner id is invalid");
            }
            String configurationIdentity = root.get(CONFIGURATION_IDENTITY_FIELD).getAsString();
            SimulationSchedulerManager manager = storage(handlerRegistry, 0L)
                    .deserialize(GSON.toJson(root.get(SCHEDULER_STATE_FIELD)));
            Optional<SchedulerRecoveryState> recoveryState = root.has(RECOVERY_STATE_FIELD)
                    ? Optional.of(recoveryStorage().deserialize(GSON.toJson(root.get(RECOVERY_STATE_FIELD))))
                    : Optional.empty();
            if (!configurationIdentity(manager).equals(configurationIdentity)) {
                throw new IllegalArgumentException("Scheduler snapshot configuration identity differs");
            }
            return new ParsedSchedulerSnapshot(schemaVersion, configurationIdentity, manager, recoveryState);
        } catch (UnsupportedSnapshotSchemaException exception) {
            throw exception;
        } catch (JsonParseException | IllegalStateException | NullPointerException exception) {
            throw new IllegalArgumentException("Corrupt Scheduler checkpoint snapshot", exception);
        }
    }

    static String configurationIdentity(SimulationSchedulerManager manager) {
        Objects.requireNonNull(manager, "manager");
        StringBuilder builder = new StringBuilder("butchercraft:simulation_scheduler/configuration\n");
        manager.stageRegistry().definitions().stream()
                .sorted(Comparator.comparingInt(SimulationStageDefinition::executionOrder)
                        .thenComparing(stage -> stage.id().value()))
                .forEach(stage -> builder
                        .append("stage:")
                        .append(stage.schemaVersion()).append(':')
                        .append(stage.id().value()).append(':')
                        .append(stage.executionOrder()).append(':')
                        .append(stage.defaultFailurePolicy().serializedName()).append(':')
                        .append(stage.allowsSameTickEnqueue()).append('\n'));
        manager.handlerRegistry().handlers().stream()
                .sorted(Comparator.comparing(handler -> handler.supportedTypeId().value()))
                .forEach(handler -> appendHandler(builder, handler));
        String digest = CheckpointSnapshotDigest.sha256(builder.toString().getBytes(StandardCharsets.UTF_8));
        return "butchercraft:simulation_scheduler/configuration/" + CheckpointSnapshotDigest.shortHex(digest);
    }

    private static void appendHandler(StringBuilder builder, SimulationWorkHandler handler) {
        builder.append("handler:")
                .append(handler.supportedTypeId().value()).append(':')
                .append(handler.effectType().name()).append(':')
                .append(handler.effectPolicy().policyIdentity())
                .append('\n');
    }

    private static SimulationSchedulerStorage storage(
            SimulationWorkHandlerRegistry handlerRegistry,
            long initialFinalizedSimulationTick
    ) {
        return new SimulationSchedulerStorage(
                Path.of("checkpoint_scheduler_snapshot.json"),
                handlerRegistry,
                initialFinalizedSimulationTick
        );
    }

    private static SchedulerRecoveryStorage recoveryStorage() {
        return new SchedulerRecoveryStorage(Path.of("checkpoint_scheduler_recovery_snapshot.json"));
    }

    record ParsedSchedulerSnapshot(
            int snapshotSchemaVersion,
            String configurationIdentity,
            SimulationSchedulerManager manager,
            Optional<SchedulerRecoveryState> recoveryState
    ) {
        ParsedSchedulerSnapshot {
            recoveryState = Objects.requireNonNull(recoveryState, "recoveryState");
        }
    }

    static final class UnsupportedSnapshotSchemaException extends RuntimeException {
        private final int schemaVersion;

        private UnsupportedSnapshotSchemaException(int schemaVersion) {
            super("Unsupported Scheduler checkpoint snapshot schema: " + schemaVersion);
            this.schemaVersion = schemaVersion;
        }

        int schemaVersion() {
            return schemaVersion;
        }
    }
}
