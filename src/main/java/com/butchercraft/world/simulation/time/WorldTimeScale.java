package com.butchercraft.world.simulation.time;

public record WorldTimeScale(long numerator, long denominator, long configuredDayServerTicks) {
    public WorldTimeScale {
        if (numerator <= 0L) {
            throw new IllegalArgumentException("World time scale numerator must be positive: " + numerator);
        }
        if (denominator <= 0L) {
            throw new IllegalArgumentException("World time scale denominator must be positive: " + denominator);
        }
        if (configuredDayServerTicks <= 0L) {
            throw new IllegalArgumentException("Configured day server ticks must be positive: "
                    + configuredDayServerTicks);
        }
    }

    public static WorldTimeScale forConfiguredDayLength(int dayLengthMinutes) {
        WorldTimeConfiguration configuration = new WorldTimeConfiguration(
                true,
                dayLengthMinutes,
                WorldTimeDimensionPolicy.OVERWORLD_BUSINESS_SOURCE
        );
        long numerator = BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS;
        long denominator = configuration.ticksPerConfiguredDay();
        long divisor = greatestCommonDivisor(numerator, denominator);
        return new WorldTimeScale(numerator / divisor, denominator / divisor, denominator);
    }

    private static long greatestCommonDivisor(long first, long second) {
        long a = Math.abs(first);
        long b = Math.abs(second);
        while (b != 0L) {
            long next = a % b;
            a = b;
            b = next;
        }
        return a;
    }
}
