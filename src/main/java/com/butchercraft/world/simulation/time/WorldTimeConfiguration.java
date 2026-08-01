package com.butchercraft.world.simulation.time;

public record WorldTimeConfiguration(
        boolean enabled,
        int dayLengthMinutes,
        WorldTimeDimensionPolicy dimensionPolicy
) {
    public static final int VANILLA_DAY_LENGTH_MINUTES = 20;
    public static final int DEFAULT_DAY_LENGTH_MINUTES = 60;
    public static final int MINIMUM_DAY_LENGTH_MINUTES = 20;
    public static final int MAXIMUM_DAY_LENGTH_MINUTES = 1_440;
    public static final int SERVER_TICKS_PER_SECOND = 20;
    public static final int SECONDS_PER_MINUTE = 60;

    public WorldTimeConfiguration {
        if (dayLengthMinutes < MINIMUM_DAY_LENGTH_MINUTES
                || dayLengthMinutes > MAXIMUM_DAY_LENGTH_MINUTES) {
            throw new IllegalArgumentException("World time day length must be within "
                    + MINIMUM_DAY_LENGTH_MINUTES + "-" + MAXIMUM_DAY_LENGTH_MINUTES
                    + " minutes: " + dayLengthMinutes);
        }
        dimensionPolicy = java.util.Objects.requireNonNull(dimensionPolicy, "dimensionPolicy");
        ticksPerConfiguredDay();
    }

    public static WorldTimeConfiguration defaults() {
        return enabled(DEFAULT_DAY_LENGTH_MINUTES);
    }

    public static WorldTimeConfiguration enabled(int dayLengthMinutes) {
        return new WorldTimeConfiguration(true, dayLengthMinutes, WorldTimeDimensionPolicy.OVERWORLD_BUSINESS_SOURCE);
    }

    public static WorldTimeConfiguration disabled(int dayLengthMinutes) {
        return new WorldTimeConfiguration(false, dayLengthMinutes, WorldTimeDimensionPolicy.OVERWORLD_BUSINESS_SOURCE);
    }

    public long ticksPerConfiguredDay() {
        return Math.multiplyExact((long) dayLengthMinutes, SECONDS_PER_MINUTE * SERVER_TICKS_PER_SECOND);
    }

    public WorldTimeScale scale() {
        return WorldTimeScale.forConfiguredDayLength(dayLengthMinutes);
    }

    public WorldTimeConfigurationIdentity identity() {
        return WorldTimeConfigurationIdentity.from(this);
    }
}
