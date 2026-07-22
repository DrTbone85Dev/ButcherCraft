package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.SimulationCalendar;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationTime;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public record BusinessHours(
        int openingHour,
        int openingMinute,
        int closingHour,
        int closingMinute,
        List<Integer> operatingWeekdays
) {
    public BusinessHours {
        validateConventionalTime(openingHour, openingMinute, false, "opening");
        validateConventionalTime(closingHour, closingMinute, true, "closing");
        operatingWeekdays = normalizeWeekdays(operatingWeekdays);
        if (minuteOfDay(openingHour, openingMinute, 60) >= minuteOfDay(closingHour, closingMinute, 60)) {
            throw new IllegalArgumentException("Business opening time must be before closing time");
        }
    }

    public static BusinessHours weekdays(
            int openingHour,
            int openingMinute,
            int closingHour,
            int closingMinute,
            SimulationConfiguration configuration
    ) {
        Objects.requireNonNull(configuration, "configuration");
        int lastWeekday = Math.min(5, configuration.daysPerWeek());
        List<Integer> weekdays = IntStream.rangeClosed(1, lastWeekday)
                .boxed()
                .toList();
        return new BusinessHours(openingHour, openingMinute, closingHour, closingMinute, weekdays);
    }

    public static BusinessHours allDays(
            int openingHour,
            int openingMinute,
            int closingHour,
            int closingMinute,
            SimulationConfiguration configuration
    ) {
        Objects.requireNonNull(configuration, "configuration");
        List<Integer> weekdays = IntStream.rangeClosed(1, configuration.daysPerWeek())
                .boxed()
                .toList();
        return new BusinessHours(openingHour, openingMinute, closingHour, closingMinute, weekdays);
    }

    public void validate(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        validateConfiguredTime(openingHour, openingMinute, false, configuration, "opening");
        validateConfiguredTime(closingHour, closingMinute, true, configuration, "closing");
        if (openingMinuteOfDay(configuration) >= closingMinuteOfDay(configuration)) {
            throw new IllegalArgumentException("Business opening time must be before closing time");
        }
        for (int weekday : operatingWeekdays) {
            if (weekday > configuration.daysPerWeek()) {
                throw new IllegalArgumentException("Business operating weekday exceeds configured week length: " + weekday);
            }
        }
    }

    public boolean operatesOn(SimulationCalendar calendar) {
        Objects.requireNonNull(calendar, "calendar");
        return operatingWeekdays.contains(calendar.weekday());
    }

    public boolean isOpenAt(
            SimulationCalendar calendar,
            SimulationTime time,
            SimulationConfiguration configuration
    ) {
        Objects.requireNonNull(calendar, "calendar").validate(configuration);
        Objects.requireNonNull(time, "time").validate(configuration);
        validate(configuration);
        if (!operatesOn(calendar)) {
            return false;
        }
        int minuteOfDay = minuteOfDay(time.hour(), time.minute(), configuration.minutesPerHour());
        return minuteOfDay >= openingMinuteOfDay(configuration)
                && minuteOfDay < closingMinuteOfDay(configuration);
    }

    int openingMinuteOfDay(SimulationConfiguration configuration) {
        return minuteOfDay(openingHour, openingMinute, configuration.minutesPerHour());
    }

    int closingMinuteOfDay(SimulationConfiguration configuration) {
        return minuteOfDay(closingHour, closingMinute, configuration.minutesPerHour());
    }

    private static List<Integer> normalizeWeekdays(List<Integer> operatingWeekdays) {
        Objects.requireNonNull(operatingWeekdays, "operatingWeekdays");
        if (operatingWeekdays.isEmpty()) {
            throw new IllegalArgumentException("Business hours must include at least one operating weekday");
        }
        Set<Integer> copied = new LinkedHashSet<>();
        for (Integer weekday : operatingWeekdays.stream().sorted().toList()) {
            Objects.requireNonNull(weekday, "operatingWeekday");
            if (weekday <= 0) {
                throw new IllegalArgumentException("Business operating weekday must be positive: " + weekday);
            }
            copied.add(weekday);
        }
        if (copied.size() != operatingWeekdays.size()) {
            throw new IllegalArgumentException("Business operating weekdays must not contain duplicates");
        }
        return List.copyOf(copied);
    }

    private static void validateConventionalTime(int hour, int minute, boolean allowEndOfDay, String label) {
        int maxHour = allowEndOfDay ? 24 : 23;
        if (hour < 0 || hour > maxHour) {
            throw new IllegalArgumentException("Business " + label + " hour is outside the supported range: " + hour);
        }
        if (minute < 0 || minute >= 60) {
            throw new IllegalArgumentException("Business " + label + " minute is outside the supported range: " + minute);
        }
        if (hour == 24 && minute != 0) {
            throw new IllegalArgumentException("Business end-of-day closing time must use minute 0");
        }
    }

    private static void validateConfiguredTime(
            int hour,
            int minute,
            boolean allowEndOfDay,
            SimulationConfiguration configuration,
            String label
    ) {
        int maxHour = allowEndOfDay ? configuration.hoursPerDay() : configuration.hoursPerDay() - 1;
        if (hour < 0 || hour > maxHour) {
            throw new IllegalArgumentException("Business " + label + " hour exceeds configured day length: " + hour);
        }
        if (minute < 0 || minute >= configuration.minutesPerHour()) {
            throw new IllegalArgumentException("Business " + label + " minute exceeds configured hour length: " + minute);
        }
        if (hour == configuration.hoursPerDay() && minute != 0) {
            throw new IllegalArgumentException("Business configured end-of-day closing time must use minute 0");
        }
    }

    static int minuteOfDay(int hour, int minute, int minutesPerHour) {
        return Math.addExact(Math.multiplyExact(hour, minutesPerHour), minute);
    }
}
