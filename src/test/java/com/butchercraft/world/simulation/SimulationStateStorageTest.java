package com.butchercraft.world.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationStateStorageTest {
    private static final SimulationConfiguration CONFIGURATION =
            new SimulationConfiguration(2, 3, 2, 2, 2, 4);

    @TempDir
    Path tempDir;

    @Test
    void saveLoadRoundTripPreservesSimulationState() {
        SimulationClock clock = new SimulationClock(CONFIGURATION);
        clock.advance(5L);
        clock.schedule("daily_rollover_999", 999L, SimulationEventType.DAILY_ROLLOVER, "manual_test_payload");
        SimulationStateStorage storage = new SimulationStateStorage(tempDir.resolve("simulation_state.json"), CONFIGURATION);

        storage.save(clock.state());
        SimulationState restored = storage.load().orElseThrow();

        assertEquals(clock.state(), restored);
    }

    @Test
    void exactDuplicateStateIsNotRepublished() throws Exception {
        Path file = tempDir.resolve("duplicate_save.json");
        SimulationStateStorage storage = new SimulationStateStorage(file, CONFIGURATION);
        SimulationState state = new SimulationClock(CONFIGURATION).state();

        storage.save(state);
        String firstPublication = Files.readString(file, StandardCharsets.UTF_8);
        storage.save(state);

        assertEquals(1L, storage.successfulPublicationCount());
        assertEquals(firstPublication, Files.readString(file, StandardCharsets.UTF_8));

        SimulationStateStorage restarted = new SimulationStateStorage(file, CONFIGURATION);
        assertEquals(state, restarted.load().orElseThrow());
        restarted.save(state);
        assertEquals(0L, restarted.successfulPublicationCount());
    }

    @Test
    void sameTickWithDifferentPendingEventsIsRepublished() {
        SimulationStateStorage storage = new SimulationStateStorage(
                tempDir.resolve("material_change.json"),
                CONFIGURATION
        );
        SimulationState first = new SimulationClock(CONFIGURATION).state();
        SimulationState second = new SimulationState(
                first.schemaVersion(),
                first.simulationTick(),
                first.calendar(),
                List.of(event("daily_rollover_10", 10L, SimulationEventType.DAILY_ROLLOVER))
        );

        storage.save(first);
        storage.save(second);

        assertEquals(2L, storage.successfulPublicationCount());
        assertEquals(second, storage.load().orElseThrow());
    }

    @Test
    void boundedClockPublicationStressReloadsExactFinalRevisionWithoutAttemptFiles() throws Exception {
        Path file = tempDir.resolve("clock_stress.json");
        SimulationStateStorage storage = new SimulationStateStorage(file, CONFIGURATION);
        SimulationClock clock = new SimulationClock(CONFIGURATION);
        int publications = 1_000;

        for (int revision = 1; revision <= publications; revision++) {
            clock.advance(1L);
            storage.save(clock.state());
        }

        assertEquals(publications, storage.successfulPublicationCount());
        assertEquals(clock.state(), new SimulationStateStorage(file, CONFIGURATION).load().orElseThrow());
        try (var files = Files.list(tempDir)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith("clock_stress.json.tmp-")));
        }
    }

    @Test
    void serializationIsDeterministic() {
        SimulationStateStorage storage = new SimulationStateStorage(tempDir.resolve("deterministic.json"), CONFIGURATION);
        SimulationState first = new SimulationState(
                SimulationSchema.CURRENT_VERSION,
                0L,
                SimulationCalendar.fromTick(0L, CONFIGURATION),
                List.of(
                        event("weekly_rollover_10", 10L, SimulationEventType.WEEKLY_ROLLOVER),
                        event("daily_rollover_10", 10L, SimulationEventType.DAILY_ROLLOVER)
                )
        );
        SimulationState second = new SimulationState(
                SimulationSchema.CURRENT_VERSION,
                0L,
                SimulationCalendar.fromTick(0L, CONFIGURATION),
                first.pendingEvents().reversed()
        );

        assertEquals(storage.serialize(first), storage.serialize(second));
    }

    @Test
    void missingFileLoadsAsEmptyOptional() {
        SimulationStateStorage storage = new SimulationStateStorage(tempDir.resolve("missing.json"), CONFIGURATION);

        assertTrue(storage.load().isEmpty());
    }

    @Test
    void corruptPersistenceIsRejected() throws Exception {
        Path file = tempDir.resolve("corrupt.json");
        Files.writeString(file, "{not json", StandardCharsets.UTF_8);

        SimulationStateStorage storage = new SimulationStateStorage(file, CONFIGURATION);

        assertThrows(IllegalArgumentException.class, storage::load);
    }

    @Test
    void unsupportedSchemaIsRejectedForMigrationReadiness() {
        SimulationStateStorage storage = new SimulationStateStorage(tempDir.resolve("future.json"), CONFIGURATION);
        String json = """
                {
                  "schema_version": 2,
                  "simulation_tick": 0,
                  "calendar": {
                    "day": 1,
                    "week": 1,
                    "month": 1,
                    "year": 1,
                    "weekday": 1,
                    "season": "spring"
                  },
                  "pending_scheduled_events": []
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(json));
    }

    @Test
    void invalidCalendarAndPastEventsAreRejected() {
        SimulationStateStorage storage = new SimulationStateStorage(tempDir.resolve("invalid.json"), CONFIGURATION);
        assertThrows(IllegalArgumentException.class, () -> new SimulationState(
                SimulationSchema.CURRENT_VERSION,
                CONFIGURATION.ticksPerDay(),
                SimulationCalendar.fromTick(0L, CONFIGURATION),
                List.of()
        ).validate(CONFIGURATION));
        assertThrows(IllegalArgumentException.class, () -> new SimulationState(
                SimulationSchema.CURRENT_VERSION,
                10L,
                SimulationCalendar.fromTick(10L, CONFIGURATION),
                List.of(event("daily_rollover_5", 5L, SimulationEventType.DAILY_ROLLOVER))
        ).validate(CONFIGURATION));
        assertThrows(IllegalArgumentException.class, () -> storage.deserialize("""
                {
                  "schema_version": 1,
                  "simulation_tick": -1,
                  "calendar": {
                    "day": 1,
                    "week": 1,
                    "month": 1,
                    "year": 1,
                    "weekday": 1,
                    "season": "spring"
                  },
                  "pending_scheduled_events": []
                }
                """));
    }

    @Test
    void duplicateEventIdsAreRejectedOnLoad() {
        SimulationStateStorage storage = new SimulationStateStorage(tempDir.resolve("duplicate.json"), CONFIGURATION);
        String json = """
                {
                  "schema_version": 1,
                  "simulation_tick": 0,
                  "calendar": {
                    "day": 1,
                    "week": 1,
                    "month": 1,
                    "year": 1,
                    "weekday": 1,
                    "season": "spring"
                  },
                  "pending_scheduled_events": [
                    {
                      "id": "daily_rollover_10",
                      "scheduled_simulation_tick": 10,
                      "event_type": "daily_rollover",
                      "payload_reference": "simulation_calendar",
                      "execution_status": "pending"
                    },
                    {
                      "id": "daily_rollover_10",
                      "scheduled_simulation_tick": 20,
                      "event_type": "daily_rollover",
                      "payload_reference": "simulation_calendar",
                      "execution_status": "pending"
                    }
                  ]
                }
                """;

        assertThrows(IllegalArgumentException.class, () -> storage.deserialize(json));
    }

    private static ScheduledSimulationEvent event(String id, long tick, SimulationEventType type) {
        return new ScheduledSimulationEvent(id, tick, type, "simulation_calendar", SimulationEventStatus.PENDING);
    }
}
