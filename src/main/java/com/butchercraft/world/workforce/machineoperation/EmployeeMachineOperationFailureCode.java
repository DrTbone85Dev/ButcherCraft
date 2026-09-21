package com.butchercraft.world.workforce.machineoperation;

import java.util.Locale;

public enum EmployeeMachineOperationFailureCode {
    EMPLOYEE_UNAVAILABLE,
    NAVIGATION_FAILED,
    RESERVATION_CONFLICT,
    RESERVATION_MISSING,
    WORKSTATION_UNAVAILABLE,
    WORKSTATION_REPLACED,
    UNSUPPORTED_MACHINE,
    INPUT_UNAVAILABLE,
    RUN_CONFLICT,
    RUN_MISSING,
    RUN_RESULT_CONFLICT,
    PLAYER_INTERRUPTED,
    RECOVERY_REQUIRED;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static EmployeeMachineOperationFailureCode fromSerializedName(String value) {
        for (EmployeeMachineOperationFailureCode code : values()) {
            if (code.serializedName().equals(value)) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unknown employee machine-operation failure code: " + value);
    }
}
