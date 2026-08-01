package com.butchercraft.world.simulation.time;

import java.util.Objects;

public record BusinessCalendarSnapshot(
        int schemaVersion,
        long businessDayIndex,
        BusinessDayOfWeek dayOfWeek,
        BusinessTimeOfDay timeOfDay,
        long minecraftDayTimeOfDay,
        long normalizedDayNumerator,
        long normalizedDayDenominator,
        String worldDayIdentity,
        WorldTimeConfigurationIdentity configurationIdentity,
        String sourceDimensionIdentity,
        long observationGameTime,
        long observedDayTime
) {
    public static final long MINECRAFT_DAY_UNITS = 24_000L;
    public static final long MINECRAFT_VISIBLE_MIDNIGHT_OFFSET = 6_000L;
    public static final long BUSINESS_MINUTES_PER_DAY = 1_440L;

    public BusinessCalendarSnapshot {
        if (schemaVersion != WorldTimeSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported business calendar schema version: " + schemaVersion);
        }
        dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        timeOfDay = Objects.requireNonNull(timeOfDay, "timeOfDay");
        if (minecraftDayTimeOfDay < 0L || minecraftDayTimeOfDay >= MINECRAFT_DAY_UNITS) {
            throw new IllegalArgumentException("Minecraft day-time-of-day is out of range: " + minecraftDayTimeOfDay);
        }
        if (normalizedDayNumerator < 0L || normalizedDayNumerator >= normalizedDayDenominator) {
            throw new IllegalArgumentException("Normalized business day fraction is out of range");
        }
        if (normalizedDayDenominator != MINECRAFT_DAY_UNITS) {
            throw new IllegalArgumentException("Business day fraction denominator must be 24000");
        }
        worldDayIdentity = requireText(worldDayIdentity, "worldDayIdentity");
        configurationIdentity = Objects.requireNonNull(configurationIdentity, "configurationIdentity");
        sourceDimensionIdentity = requireText(sourceDimensionIdentity, "sourceDimensionIdentity");
        if (observationGameTime < 0L) {
            throw new IllegalArgumentException("Observation gameTime must not be negative: " + observationGameTime);
        }
    }

    public static BusinessCalendarSnapshot fromDayTime(
            long observedDayTime,
            WorldTimeConfigurationIdentity configurationIdentity,
            String sourceDimensionIdentity,
            long observationGameTime
    ) {
        long shiftedDayTime = Math.addExact(observedDayTime, MINECRAFT_VISIBLE_MIDNIGHT_OFFSET);
        long businessDayIndex = Math.floorDiv(shiftedDayTime, MINECRAFT_DAY_UNITS);
        long dayTimeOfDay = Math.floorMod(shiftedDayTime, MINECRAFT_DAY_UNITS);
        long minuteOfDay = (dayTimeOfDay * BUSINESS_MINUTES_PER_DAY) / MINECRAFT_DAY_UNITS;
        BusinessTimeOfDay timeOfDay = new BusinessTimeOfDay((int) (minuteOfDay / 60L), (int) (minuteOfDay % 60L));
        String identity = "butchercraft:world_day/v1/" + sourceDimensionIdentity + "/" + businessDayIndex;
        return new BusinessCalendarSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                businessDayIndex,
                BusinessDayOfWeek.fromDayIndex(businessDayIndex),
                timeOfDay,
                dayTimeOfDay,
                dayTimeOfDay,
                MINECRAFT_DAY_UNITS,
                identity,
                configurationIdentity,
                sourceDimensionIdentity,
                observationGameTime,
                observedDayTime
        );
    }

    private static String requireText(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Business calendar field must not be blank: " + fieldName);
        }
        return normalized;
    }
}
