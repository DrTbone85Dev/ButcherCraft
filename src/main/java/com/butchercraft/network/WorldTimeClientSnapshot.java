package com.butchercraft.network;

import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeSchema;

import java.util.Objects;

public record WorldTimeClientSnapshot(
        int schemaVersion,
        boolean scalingEnabled,
        int configuredDayLengthMinutes,
        String configurationIdentity,
        String sourceDimensionIdentity,
        long gameTime,
        long dayTime,
        long businessDayIndex,
        BusinessDayOfWeek dayOfWeek,
        BusinessTimeOfDay timeOfDay,
        String worldDayIdentity,
        WorldTimeMovementClassification movementClassification,
        boolean externalConflictDetected
) {
    public WorldTimeClientSnapshot {
        if (schemaVersion != WorldTimeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported client world time snapshot schema: " + schemaVersion);
        }
        if (configuredDayLengthMinutes < WorldTimeConfiguration.MINIMUM_DAY_LENGTH_MINUTES
                || configuredDayLengthMinutes > WorldTimeConfiguration.MAXIMUM_DAY_LENGTH_MINUTES) {
            throw new IllegalArgumentException("Configured day length out of range: " + configuredDayLengthMinutes);
        }
        configurationIdentity = requireText(configurationIdentity, "configurationIdentity");
        sourceDimensionIdentity = requireText(sourceDimensionIdentity, "sourceDimensionIdentity");
        if (gameTime < 0L) {
            throw new IllegalArgumentException("gameTime must not be negative: " + gameTime);
        }
        dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        timeOfDay = Objects.requireNonNull(timeOfDay, "timeOfDay");
        worldDayIdentity = requireText(worldDayIdentity, "worldDayIdentity");
        movementClassification = Objects.requireNonNull(movementClassification, "movementClassification");
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Client world time field must not be blank: " + fieldName);
        }
        return normalized;
    }
}
