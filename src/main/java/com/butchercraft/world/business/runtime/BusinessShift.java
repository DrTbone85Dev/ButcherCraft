package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationTime;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record BusinessShift(
        String id,
        int startHour,
        int startMinute,
        int endHour,
        int endMinute,
        int expectedWorkforce,
        boolean active
) {
    private static final Pattern VALID_ID = Pattern.compile("^[a-z0-9_]+$");

    public BusinessShift {
        id = requireId(id);
        validateConventionalTime(startHour, startMinute, false, "start");
        validateConventionalTime(endHour, endMinute, true, "end");
        if (BusinessHours.minuteOfDay(startHour, startMinute, 60) >= BusinessHours.minuteOfDay(endHour, endMinute, 60)) {
            throw new IllegalArgumentException("Business shift start must be before shift end: " + id);
        }
        if (expectedWorkforce < 0) {
            throw new IllegalArgumentException("Business shift expected workforce must not be negative: " + expectedWorkforce);
        }
    }

    public void validate(SimulationConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        validateConfiguredTime(startHour, startMinute, false, configuration, "start");
        validateConfiguredTime(endHour, endMinute, true, configuration, "end");
        if (startMinuteOfDay(configuration) >= endMinuteOfDay(configuration)) {
            throw new IllegalArgumentException("Business shift start must be before shift end: " + id);
        }
    }

    public boolean contains(SimulationTime time, SimulationConfiguration configuration) {
        Objects.requireNonNull(time, "time").validate(configuration);
        validate(configuration);
        if (!active) {
            return false;
        }
        int minuteOfDay = BusinessHours.minuteOfDay(time.hour(), time.minute(), configuration.minutesPerHour());
        return minuteOfDay >= startMinuteOfDay(configuration)
                && minuteOfDay < endMinuteOfDay(configuration);
    }

    int startMinuteOfDay(SimulationConfiguration configuration) {
        return BusinessHours.minuteOfDay(startHour, startMinute, configuration.minutesPerHour());
    }

    int endMinuteOfDay(SimulationConfiguration configuration) {
        return BusinessHours.minuteOfDay(endHour, endMinute, configuration.minutesPerHour());
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "id");
        value = value.toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("Business shift id must use lowercase snake case: " + value);
        }
        return value;
    }

    private static void validateConventionalTime(int hour, int minute, boolean allowEndOfDay, String label) {
        int maxHour = allowEndOfDay ? 24 : 23;
        if (hour < 0 || hour > maxHour) {
            throw new IllegalArgumentException("Business shift " + label + " hour is outside the supported range: " + hour);
        }
        if (minute < 0 || minute >= 60) {
            throw new IllegalArgumentException("Business shift " + label + " minute is outside the supported range: " + minute);
        }
        if (hour == 24 && minute != 0) {
            throw new IllegalArgumentException("Business shift end-of-day time must use minute 0");
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
            throw new IllegalArgumentException("Business shift " + label + " hour exceeds configured day length: " + hour);
        }
        if (minute < 0 || minute >= configuration.minutesPerHour()) {
            throw new IllegalArgumentException("Business shift " + label + " minute exceeds configured hour length: " + minute);
        }
        if (hour == configuration.hoursPerDay() && minute != 0) {
            throw new IllegalArgumentException("Business shift configured end-of-day time must use minute 0");
        }
    }
}
