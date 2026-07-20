package com.butchercraft.transformation;

/**
 * Stable machine-readable result code for transformation execution.
 */
public enum TransformationExecutionCode {
    EXECUTED("executed"),
    EVALUATION_NOT_ACCEPTED("evaluation_not_accepted"),
    EVALUATION_MISMATCH("evaluation_mismatch"),
    INPUT_UNAVAILABLE("input_unavailable"),
    OUTPUT_REJECTED("output_rejected"),
    TRANSACTION_ALREADY_COMMITTED("transaction_already_committed"),
    TRANSACTION_ROLLED_BACK("transaction_rolled_back"),
    INVALID_REQUEST("invalid_request");

    private final String code;

    TransformationExecutionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
