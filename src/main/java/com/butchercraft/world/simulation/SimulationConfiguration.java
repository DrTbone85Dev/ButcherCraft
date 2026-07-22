package com.butchercraft.world.simulation;

public record SimulationConfiguration(
        int ticksPerSimulationMinute,
        int minutesPerHour,
        int hoursPerDay,
        int daysPerWeek,
        int weeksPerMonth,
        int monthsPerYear
) {
    public static SimulationConfiguration standard() {
        return new SimulationConfiguration(20, 60, 24, 7, 4, 12);
    }

    public SimulationConfiguration {
        requirePositive(ticksPerSimulationMinute, "ticksPerSimulationMinute");
        requirePositive(minutesPerHour, "minutesPerHour");
        requirePositive(hoursPerDay, "hoursPerDay");
        requirePositive(daysPerWeek, "daysPerWeek");
        requirePositive(weeksPerMonth, "weeksPerMonth");
        if (monthsPerYear < Season.values().length) {
            throw new IllegalArgumentException("Simulation months per year must support every season: " + monthsPerYear);
        }
    }

    public long ticksPerHour() {
        return multiply(ticksPerSimulationMinute, minutesPerHour);
    }

    public long ticksPerDay() {
        return multiply(ticksPerHour(), hoursPerDay);
    }

    public long ticksPerWeek() {
        return multiply(ticksPerDay(), daysPerWeek);
    }

    public long ticksPerMonth() {
        return multiply(ticksPerWeek(), weeksPerMonth);
    }

    public long ticksPerYear() {
        return multiply(ticksPerMonth(), monthsPerYear);
    }

    public int daysPerMonth() {
        return Math.multiplyExact(daysPerWeek, weeksPerMonth);
    }

    public int daysPerYear() {
        return Math.multiplyExact(daysPerMonth(), monthsPerYear);
    }

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException("Simulation configuration " + fieldName + " must be positive: " + value);
        }
        return value;
    }

    private static long multiply(long left, long right) {
        return Math.multiplyExact(left, right);
    }
}
