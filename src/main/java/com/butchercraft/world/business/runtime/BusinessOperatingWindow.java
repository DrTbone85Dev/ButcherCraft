package com.butchercraft.world.business.runtime;

import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;

import java.util.Objects;

public record BusinessOperatingWindow(
        BusinessDayOfWeek dayOfWeek,
        BusinessTimeOfDay start,
        BusinessTimeOfDay end
) {
    public BusinessOperatingWindow {
        dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek");
        start = Objects.requireNonNull(start, "start");
        end = Objects.requireNonNull(end, "end");
    }

    public static BusinessOperatingWindow of(BusinessDayOfWeek day, String value) {
        String normalized = BusinessRuntimeValidation.requireText(value, day.serializedName() + " operating window")
                .toUpperCase(java.util.Locale.ROOT);
        if (normalized.equals("CLOSED")) {
            throw new IllegalArgumentException("Closed days are represented by omitting the operating window");
        }
        int split = normalized.indexOf('-');
        if (split <= 0 || split != normalized.lastIndexOf('-') || split == normalized.length() - 1) {
            throw new IllegalArgumentException("Operating window must use HH:MM-HH:MM");
        }
        return new BusinessOperatingWindow(
                day,
                parseTime(normalized.substring(0, split)),
                parseTime(normalized.substring(split + 1))
        );
    }

    static BusinessTimeOfDay parseTime(String value) {
        String normalized = BusinessRuntimeValidation.requireText(value, "Business time");
        if (normalized.equals("24:00")) {
            throw new IllegalArgumentException("Business time 24:00 is ambiguous; use 00:00 with an overnight window");
        }
        String[] parts = normalized.split(":", -1);
        if (parts.length != 2 || parts[0].length() != 2 || parts[1].length() != 2) {
            throw new IllegalArgumentException("Business time must use HH:MM: " + value);
        }
        try {
            return new BusinessTimeOfDay(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Business time must use numeric HH:MM: " + value, exception);
        }
    }

    public boolean overnight() {
        return endMinuteOfDay() <= startMinuteOfDay();
    }

    public int startMinuteOfDay() {
        return start.hour() * 60 + start.minute();
    }

    public int endMinuteOfDay() {
        return end.hour() * 60 + end.minute();
    }

    long durationMinutes() {
        int startMinute = startMinuteOfDay();
        int endMinute = endMinuteOfDay();
        return endMinute > startMinute
                ? endMinute - startMinute
                : BusinessOperatingSchedule.MINUTES_PER_DAY - startMinute + endMinute;
    }

    String canonicalLine() {
        return dayOfWeek.serializedName() + "=" + start.displayText() + "-" + end.displayText();
    }
}
