package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusinessRuntimeCalendarStorageTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void diagnosticStateRoundTripsDeterministically() {
        BusinessRuntimeCalendarStorage storage = storage();
        BusinessRuntimeObservationSnapshot observed = observe(0L, 6, 0);
        BusinessRuntimeCalendarState state = BusinessRuntimeCalendarState.from(observed);

        String serialized = storage.serialize(state);
        BusinessRuntimeCalendarState restored = storage.deserialize(serialized);

        assertEquals(state, restored);
        assertEquals(serialized, storage.serialize(restored));
    }

    @Test
    void saveAndLoadUseTheConfiguredPath() {
        BusinessRuntimeCalendarStorage storage = storage();
        BusinessRuntimeCalendarState state = BusinessRuntimeCalendarState.from(observe(0L, 14, 30));

        storage.save(state);

        assertTrue(Files.exists(temporaryDirectory.resolve("business_calendar_runtime.json")));
        assertEquals(state, storage.load().orElseThrow());
    }

    @Test
    void unsupportedSchemaFailsVisibly() {
        BusinessRuntimeCalendarStorage storage = storage();

        assertThrows(IllegalArgumentException.class, () ->
                storage.deserialize("""
                        {
                          "schema_version": 2,
                          "operating_schedule_identity": "butchercraft:business_operating_schedule/v1/test",
                          "shift_set_identity": "butchercraft:business_shift_set/v1/test",
                          "configuration_identity": "butchercraft:business_runtime_config/v1/test",
                          "last_observed_world_day_identity": "butchercraft:world_day/v1/minecraft:overworld/0",
                          "last_observed_open": true,
                          "last_active_shift_identity": null,
                          "last_evaluated_boundary": null,
                          "last_movement_classification": "initialized"
                        }
                        """));
    }

    private BusinessRuntimeCalendarStorage storage() {
        return new BusinessRuntimeCalendarStorage(temporaryDirectory.resolve("business_calendar_runtime.json"));
    }

    private static BusinessRuntimeObservationSnapshot observe(long dayIndex, int hour, int minute) {
        return BusinessRuntimeObservationSnapshot.observe(
                BusinessOperatingScheduleTest.snapshot(dayIndex, hour, minute),
                BusinessRuntimeCalendarConfiguration.defaults(WorldTimeConfiguration.enabled(60).identity()),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT
        );
    }
}
