package com.butchercraft.world.workforce.employee;

import java.util.Arrays;

public enum EmployeeStatus {
    PENDING("pending"),
    ACTIVE("active"),
    INACTIVE("inactive"),
    TERMINATED("terminated");

    private final String serializedName;

    EmployeeStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public boolean permitsPresence() {
        return this == ACTIVE;
    }

    public static EmployeeStatus fromSerializedName(String serializedName) {
        return Arrays.stream(values())
                .filter(status -> status.serializedName.equals(serializedName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown employee status: " + serializedName));
    }
}
