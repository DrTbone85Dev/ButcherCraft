package com.butchercraft.world.workforce.materialhandling;

public enum EmployeeMaterialHandlingFailureCode {
    EMPLOYEE_NOT_FOUND("employee_not_found"),
    EMPLOYEE_UNAVAILABLE("employee_unavailable"),
    EMPLOYEE_OFF_SHIFT("employee_off_shift"),
    PLANT_CLOSED("plant_closed"),
    ASSIGNMENT_CONFLICT("assignment_conflict"),
    SOURCE_NOT_FOUND("source_not_found"),
    SOURCE_WRONG_TYPE("source_wrong_type"),
    SOURCE_ENDPOINT_REPLACED("source_endpoint_replaced"),
    SOURCE_RESERVATION_FAILED("source_reservation_failed"),
    SOURCE_UNREACHABLE("source_unreachable"),
    WITHDRAWAL_REJECTED("withdrawal_rejected"),
    CUSTODY_NOT_PROVEN("custody_not_proven"),
    DESTINATION_NOT_FOUND("destination_not_found"),
    DESTINATION_WRONG_TYPE("destination_wrong_type"),
    DESTINATION_ENDPOINT_REPLACED("destination_endpoint_replaced"),
    DESTINATION_RESERVATION_FAILED("destination_reservation_failed"),
    DESTINATION_UNREACHABLE("destination_unreachable"),
    DEPOSIT_REJECTED("deposit_rejected"),
    RESERVATION_LOST("reservation_lost"),
    TRANSFER_FAILED("transfer_failed"),
    CANCELLATION_FAILED("cancellation_failed"),
    RECOVERY_REQUIRED("recovery_required"),
    UNKNOWN_OUTCOME("unknown_outcome");

    private final String serializedName;

    EmployeeMaterialHandlingFailureCode(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static EmployeeMaterialHandlingFailureCode fromSerializedName(String value) {
        for (EmployeeMaterialHandlingFailureCode code : values()) {
            if (code.serializedName.equals(value)) {
                return code;
            }
        }
        throw new IllegalArgumentException("Unknown employee Material Handling failure code: " + value);
    }
}
