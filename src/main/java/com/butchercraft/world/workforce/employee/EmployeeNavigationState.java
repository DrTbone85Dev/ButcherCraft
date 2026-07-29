package com.butchercraft.world.workforce.employee;

import java.util.Arrays;

public enum EmployeeNavigationState {
    OFF_SHIFT("off_shift"),
    WALKING_TO_DEPARTMENT("walking_to_department"),
    PRESENT_IN_DEPARTMENT("present_in_department"),
    WALKING_TO_WORKSTATION("walking_to_workstation"),
    WAITING_AT_WORKSTATION("waiting_at_workstation"),
    RETURNING_TO_DEPARTMENT("returning_to_department"),
    IDLE("idle"),
    RETURNING_TO_ANCHOR("returning_to_anchor");

    private final String serializedName;

    EmployeeNavigationState(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static EmployeeNavigationState fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(state -> state.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown employee navigation state: " + serializedName));
    }
}
