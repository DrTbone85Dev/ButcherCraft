package com.butchercraft.world.simulation.time;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTimeStateStorageTest {
    private static final WorldTimeConfiguration CONFIGURATION = WorldTimeConfiguration.enabled(60);
    private static final String SOURCE = "minecraft:overworld";

    @TempDir
    Path tempDir;

    @Test
    void saveLoadRoundTripPreservesAccumulatorAndObservations() {
        WorldTimeState state = new WorldTimeState(
                WorldTimeSchema.CURRENT_VERSION,
                CONFIGURATION.identity().value(),
                2L,
                99L,
                99L,
                SOURCE,
                42L,
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT,
                0,
                false
        );
        WorldTimeStateStorage storage = new WorldTimeStateStorage(tempDir.resolve("world_time.json"), CONFIGURATION);

        storage.save(state);
        WorldTimeState loaded = storage.load().orElseThrow();

        assertEquals(state.accumulatorRemainderNumerator(), loaded.accumulatorRemainderNumerator());
        assertEquals(state.lastObservedRawDayTime(), loaded.lastObservedRawDayTime());
        assertEquals(state.lastExpectedScaledDayTime(), loaded.lastExpectedScaledDayTime());
        assertEquals(WorldTimeMovementClassification.PERSISTENCE_RESTORED, loaded.lastMovementClassification());
    }

    @Test
    void serializationIsDeterministic() {
        WorldTimeState state = WorldTimeController.initialState(CONFIGURATION, 0L, 0L, SOURCE);
        WorldTimeStateStorage storage = new WorldTimeStateStorage(tempDir.resolve("deterministic.json"), CONFIGURATION);

        assertEquals(storage.serialize(state), storage.serialize(state));
    }

    @Test
    void missingFileLoadsAsEmptyOptional() {
        WorldTimeStateStorage storage = new WorldTimeStateStorage(tempDir.resolve("missing.json"), CONFIGURATION);

        assertTrue(storage.load().isEmpty());
    }

    @Test
    void corruptJsonAndUnsupportedSchemaAreRejected() throws Exception {
        WorldTimeStateStorage storage = new WorldTimeStateStorage(tempDir.resolve("unused.json"), CONFIGURATION);

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("{not json"));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("""
                {
                  "schema_version": 2,
                  "configuration_identity": "future",
                  "accumulator_remainder_numerator": 0,
                  "last_observed_raw_day_time": 0,
                  "last_expected_scaled_day_time": 0,
                  "source_dimension_identity": "minecraft:overworld",
                  "last_observation_game_time": 0,
                  "last_movement_classification": "initialized",
                  "consecutive_unexpected_changes": 0,
                  "external_conflict_detected": false
                }
                """));
    }

    @Test
    void invalidAccumulatorStateIsRejectedOnLoad() throws Exception {
        Path file = tempDir.resolve("invalid.json");
        Files.writeString(file, """
                {
                  "schema_version": 1,
                  "configuration_identity": "butchercraft:world_time_config/v1/test",
                  "accumulator_remainder_numerator": 3,
                  "last_observed_raw_day_time": 0,
                  "last_expected_scaled_day_time": 0,
                  "source_dimension_identity": "minecraft:overworld",
                  "last_observation_game_time": 0,
                  "last_movement_classification": "initialized",
                  "consecutive_unexpected_changes": 0,
                  "external_conflict_detected": false
                }
                """, StandardCharsets.UTF_8);
        WorldTimeStateStorage storage = new WorldTimeStateStorage(file, CONFIGURATION);

        assertThrows(IllegalArgumentException.class, storage::load);
    }
}
