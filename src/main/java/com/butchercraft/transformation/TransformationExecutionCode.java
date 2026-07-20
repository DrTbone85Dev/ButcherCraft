package com.butchercraft.transformation;

/**
 * Stable machine-readable result code for transformation execution.
 */
public enum TransformationExecutionCode {
    EXECUTED("executed"),
    EVALUATION_NOT_ACCEPTED("evaluation_not_accepted"),
    EVALUATION_MISMATCH("evaluation_mismatch"),
    INVALID_REQUEST("invalid_request");

    private final String code;

    TransformationExecutionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
