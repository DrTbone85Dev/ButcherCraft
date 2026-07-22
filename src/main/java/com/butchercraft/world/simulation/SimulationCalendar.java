package com.butchercraft.world.simulation;

import java.util.Objects;

public record SimulationCalendar(
        int day,
        int week,
        int month,
        int year,
        int weekday,
        Season season
) {
    public SimulationCalendar {
        requirePositive(day, "day");
        requirePositive(week, "week");
        requirePositive(month, "month");
        requirePositive(year, "year");
        requirePositive(weekday, "weekday");
        season = Objects.requireNonNull(season, "season");
    }

    public static SimulationCalendar fromTick(long simulationTick, SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (simulationTick < 0L) {
            throw new IllegalArgumentException("Simulation tick must not be negative: " + simulationTick);
        }
        long elapsedMinutes = simulationTick / configuration.ticksPerSimulationMinute();
        long minutesPerDay = Math.multiplyExact((long) configuration.minutesPerHour(), configuration.hoursPerDay());
        long elapsedDays = elapsedMinutes / minutesPerDay;
        long yearIndex = elapsedDays / configuration.daysPerYear();
        long dayWithinYear = elapsedDays % configuration.daysPerYear();
        int monthIndex = (int) (dayWithinYear / configuration.daysPerMonth());
        int dayWithinMonth = (int) (dayWithinYear % configuration.daysPerMonth());
        int weekIndex = dayWithinMonth / configuration.daysPerWeek();
        int weekdayIndex = dayWithinMonth % configuration.daysPerWeek();
        Season season = Season.values()[(int) (((long) monthIndex * Season.values().length) / configuration.monthsPerYear())];
        return new SimulationCalendar(
                dayWithinMonth + 1,
                weekIndex + 1,
                monthIndex + 1,
                Math.toIntExact(yearIndex + 1L),
                weekdayIndex + 1,
                season
        );
    }

    public void validate(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        if (day > configuration.daysPerMonth()) {
            throw new IllegalArgumentException("Simulation calendar day exceeds configured month length: " + day);
        }
        if (week > configuration.weeksPerMonth()) {
            throw new IllegalArgumentException("Simulation calendar week exceeds configured month length: " + week);
        }
        if (month > configuration.monthsPerYear()) {
            throw new IllegalArgumentException("Simulation calendar month exceeds configured year length: " + month);
        }
        if (weekday > configuration.daysPerWeek()) {
            throw new IllegalArgumentException("Simulation calendar weekday exceeds configured week length: " + weekday);
        }
    }

    private static int requirePositive(int value, String fieldName) {
        if (value <= 0) {
            throw new IllegalArgumentException("Simulation calendar " + fieldName + " must be positive: " + value);
        }
        return value;
    }
}
