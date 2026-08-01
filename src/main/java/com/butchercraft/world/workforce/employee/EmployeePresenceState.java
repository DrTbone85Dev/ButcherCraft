package com.butchercraft.world.workforce.employee;

import java.util.Arrays;

public enum EmployeePresenceState {
    OFF_SHIFT("off_shift"),
    SCHEDULED("scheduled"),
    PRESENT("present"),
    ABSENT("absent"),
    UNAVAILABLE("unavailable");

    private final String serializedName;

    EmployeePresenceState(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean isExplicitCommandState() {
        return this != SCHEDULED;
    }

    public static EmployeePresenceState fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(state -> state.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown employee presence state: " + serializedName));
    }
}
