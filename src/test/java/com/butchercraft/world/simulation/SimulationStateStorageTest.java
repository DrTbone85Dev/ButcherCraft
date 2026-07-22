package com.butchercraft.world.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
