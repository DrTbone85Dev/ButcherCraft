package com.butchercraft.world.simulation.time;

import java.util.Objects;

public record WorldTimeStatusSnapshot(
        int schemaVersion,
        boolean scalingEnabled,
        int configuredDayLengthMinutes,
        long scaleNumerator,
        long scaleDenominator,
        WorldTimeConfigurationIdentity configurationIdentity,
        String sourceDimensionIdentity,
        long gameTime,
        long dayTime,
        BusinessCalendarSnapshot businessCalendar,
        long accumulatorRemainderNumerator,
        WorldTimeMovementClassification movementClassification,
        boolean externalConflictDetected
) {
    public WorldTimeStatusSnapshot {
        if (schemaVersion != WorldTimeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported world time status schema version: " + schemaVersion);
        }
        if (configuredDayLengthMinutes < WorldTimeConfiguration.MINIMUM_DAY_LENGTH_MINUTES
                || configuredDayLengthMinutes > WorldTimeConfiguration.MAXIMUM_DAY_LENGTH_MINUTES) {
            throw new IllegalArgumentException("Configured day length out of range: " + configuredDayLengthMinutes);
        }
        if (scaleNumerator <= 0L || scaleDenominator <= 0L) {
            throw new IllegalArgumentException("World time scale must be positive");
        }
        configurationIdentity = Objects.requireNonNull(configurationIdentity, "configurationIdentity");
        sourceDimensionIdentity = Objects.requireNonNull(sourceDimensionIdentity, "sourceDimensionIdentity").strip();
        if (sourceDimensionIdentity.isEmpty()) {
            throw new IllegalArgumentException("Source dimension identity must not be blank");
        }
        if (gameTime < 0L) {
            throw new IllegalArgumentException("gameTime must not be negative: " + gameTime);
        }
        businessCalendar = Objects.requireNonNull(businessCalendar, "businessCalendar");
        if (accumulatorRemainderNumerator < 0L) {
            throw new IllegalArgumentException("Accumulator remainder must not be negative: "
                    + accumulatorRemainderNumerator);
        }
        movementClassification = Objects.requireNonNull(movementClassification, "movementClassification");
    }

    public String businessTimeDisplay() {
        return businessCalendar.dayOfWeek().displayName() + " "
                + businessCalendar.timeOfDay().displayText();
    }
}
