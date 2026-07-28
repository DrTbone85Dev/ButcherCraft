package com.butchercraft.world.workforce.employee;

public enum EmployeeFailureCode {
    CAPACITY_EXCEEDED("capacity_exceeded"),
    UNKNOWN_EMPLOYEE("unknown_employee"),
    DUPLICATE_EMPLOYEE_ID("duplicate_employee_id"),
    MISSING_BUSINESS("missing_business"),
    MISSING_BUSINESS_RUNTIME("missing_business_runtime"),
    MISSING_SHIFT("missing_shift"),
    INVALID_SHIFT("invalid_shift"),
    INVALID_POSITION("invalid_position"),
    INVALID_STATUS_TRANSITION("invalid_status_transition"),
    INVALID_PRESENCE_STATE("invalid_presence_state"),
    TERMINATED_EMPLOYEE("terminated_employee"),
    ENTITY_ALREADY_BOUND("entity_already_bound"),
    ENTITY_LINK_CONFLICT("entity_link_conflict");

    private final String reasonCode;

    EmployeeFailureCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
