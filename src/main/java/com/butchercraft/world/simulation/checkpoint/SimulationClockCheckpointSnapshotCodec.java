package com.butchercraft.world.simulation.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationState;
import com.butchercraft.world.simulation.SimulationStateStorage;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

final class SimulationClockCheckpointSnapshotCodec {
    static final int SNAPSHOT_SCHEMA_VERSION = 1;
    static final CheckpointOwnerId OWNER_ID = CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER;

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();
    private static final String SNAPSHOT_SCHEMA_VERSION_FIELD = "snapshot_schema_version";
    private static final String OWNER_ID_FIELD = "owner_id";
    private static final String CONFIGURATION_IDENTITY_FIELD = "configuration_identity";
    private static final String SIMULATION_STATE_FIELD = "simulation_state";

    private SimulationClockCheckpointSnapshotCodec() {
    }

    static byte[] serialize(SimulationState state, SimulationConfiguration configuration) {
        Objects.requireNonNull(state, "state").validate(configuration);
        SimulationStateStorage storage = storage(configuration);
        JsonObject root = new JsonObject();
        root.addProperty(SNAPSHOT_SCHEMA_VERSION_FIELD, SNAPSHOT_SCHEMA_VERSION);
        root.addProperty(OWNER_ID_FIELD, OWNER_ID.value());
        root.addProperty(CONFIGURATION_IDENTITY_FIELD, configurationIdentity(configuration));
        root.add(
                SIMULATION_STATE_FIELD,
                JsonParser.parseString(storage.serialize(state)).getAsJsonObject()
        );
        return (GSON.toJson(root) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static ParsedClockSnapshot deserialize(byte[] bytes, SimulationConfiguration configuration) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(configuration, "configuration");
        try {
            JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            int schemaVersion = root.get(SNAPSHOT_SCHEMA_VERSION_FIELD).getAsInt();
            if (schemaVersion != SNAPSHOT_SCHEMA_VERSION) {
                throw new UnsupportedSnapshotSchemaException(schemaVersion);
            }
            String owner = root.get(OWNER_ID_FIELD).getAsString();
            if (!OWNER_ID.value().equals(owner)) {
                throw new IllegalArgumentException("Clock snapshot owner id is invalid");
            }
            String configurationIdentity = root.get(CONFIGURATION_IDENTITY_FIELD).getAsString();
            if (!configurationIdentity(configuration).equals(configurationIdentity)) {
                throw new IllegalArgumentException("Clock snapshot configuration identity differs");
            }
            SimulationState state = storage(configuration).deserialize(GSON.toJson(root.get(SIMULATION_STATE_FIELD)));
            return new ParsedClockSnapshot(schemaVersion, configurationIdentity, state);
        } catch (UnsupportedSnapshotSchemaException exception) {
            throw exception;
        } catch (JsonParseException | IllegalStateException | NullPointerException exception) {
            throw new IllegalArgumentException("Corrupt Clock checkpoint snapshot", exception);
        }
    }

    static String configurationIdentity(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        return "butchercraft:simulation_clock/configuration/"
                + "tpm_" + configuration.ticksPerSimulationMinute()
                + "_mph_" + configuration.minutesPerHour()
                + "_hpd_" + configuration.hoursPerDay()
                + "_dpw_" + configuration.daysPerWeek()
                + "_wpm_" + configuration.weeksPerMonth()
                + "_mpy_" + configuration.monthsPerYear();
    }

    private static SimulationStateStorage storage(SimulationConfiguration configuration) {
        return new SimulationStateStorage(Path.of("checkpoint_clock_snapshot.json"), configuration);
    }

    record ParsedClockSnapshot(
            int snapshotSchemaVersion,
            String configurationIdentity,
            SimulationState state
    ) {
    }

    static final class UnsupportedSnapshotSchemaException extends RuntimeException {
        private final int schemaVersion;

        private UnsupportedSnapshotSchemaException(int schemaVersion) {
            super("Unsupported Clock checkpoint snapshot schema: " + schemaVersion);
            this.schemaVersion = schemaVersion;
        }

        int schemaVersion() {
            return schemaVersion;
        }
    }
}
