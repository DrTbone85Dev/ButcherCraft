package com.butchercraft.network;

import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeController;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldTimeClientSnapshotPayloadTest {
    @Test
    void payloadContainsOnlyDisplaySnapshotAndNoAccumulatorAuthority() {
        WorldTimeConfiguration configuration = WorldTimeConfiguration.enabled(60);
        var state = WorldTimeController.initialState(configuration, 0L, 0L, "minecraft:overworld");
        var snapshot = new WorldTimeController().snapshot(state, configuration, 10L, 6_000L);

        WorldTimeClientSnapshotPayload payload = WorldTimeClientSnapshotPayload.from(snapshot);
        WorldTimeClientSnapshot client = payload.snapshot();

        assertEquals(WorldTimeSchema.CURRENT_VERSION, client.schemaVersion());
        assertTrue(client.scalingEnabled());
        assertEquals(60, client.configuredDayLengthMinutes());
        assertEquals(6_000L, client.dayTime());
        assertEquals(new BusinessTimeOfDay(12, 0), client.timeOfDay());
        assertEquals(WorldTimeMovementClassification.INITIALIZED, client.movementClassification());
        assertEquals(BusinessCalendarSnapshot.fromDayTime(6_000L, configuration.identity(),
                "minecraft:overworld", 10L).worldDayIdentity(), client.worldDayIdentity());
    }
}
