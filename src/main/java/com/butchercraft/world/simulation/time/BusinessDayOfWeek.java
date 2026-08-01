package com.butchercraft.world.simulation.time;

import java.util.Locale;

public enum BusinessDayOfWeek {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday"),
    SATURDAY("Saturday"),
    SUNDAY("Sunday");

    private final String displayName;

    BusinessDayOfWeek(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static BusinessDayOfWeek fromDayIndex(long dayIndex) {
        return values()[Math.floorMod(dayIndex, values().length)];
    }

    public static BusinessDayOfWeek fromSerializedName(String value) {
        for (BusinessDayOfWeek day : values()) {
            if (day.serializedName().equals(value)) {
                return day;
            }
        }
        throw new IllegalArgumentException("Unknown business day-of-week: " + value);
    }
}
