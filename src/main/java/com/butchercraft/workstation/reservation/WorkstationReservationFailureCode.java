package com.butchercraft.workstation.reservation;

public enum WorkstationReservationFailureCode {
    UNKNOWN_EMPLOYEE("unknown_employee"),
    EMPLOYEE_NOT_PRESENT("employee_not_present"),
    EMPLOYEE_MISSING_DEPARTMENT("employee_missing_department"),
    EMPLOYEE_ENTITY_MISSING("employee_entity_missing"),
    EMPLOYEE_DIFFERENT_WORLD("employee_different_world"),
    PLANT_CLOSED("plant_closed"),
    MISSING_BUSINESS_RUNTIME("missing_business_runtime"),
    UNSUPPORTED_WORKSTATION("unsupported_workstation"),
    WORKSTATION_ALREADY_RESERVED("workstation_already_reserved"),
    EMPLOYEE_ALREADY_RESERVED("employee_already_reserved"),
    UNKNOWN_RESERVATION("unknown_reservation"),
    INVALID_POSITION("invalid_position"),
    STALE_RESERVATION("stale_reservation"),
    NAVIGATION_UNREACHABLE("navigation_unreachable"),
    NO_PATH("no_path"),
    PROGRESS_STALLED("progress_stalled"),
    CANDIDATE_BLOCKED("candidate_blocked"),
    DESTINATION_INVALID("destination_invalid"),
    WORKSTATION_REMOVED("workstation_removed"),
    RESERVATION_INVALID("reservation_invalid"),
    ALL_CANDIDATES_EXHAUSTED("all_candidates_exhausted"),
    DEPARTMENT_UNREACHABLE("department_unreachable");

    private final String reasonCode;

    WorkstationReservationFailureCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
