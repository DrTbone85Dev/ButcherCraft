package com.butchercraft.transformation;

/**
 * Stable machine-readable result code for transformation evaluation.
 */
public enum TransformationEvaluationCode {
    ACCEPTED("accepted"),
    MISSING_INPUT("missing_input"),
    INSUFFICIENT_INPUT("insufficient_input"),
    UNSUPPORTED_CAPABILITY("unsupported_capability"),
    INVALID_CONTEXT("invalid_context");

    private final String code;

    TransformationEvaluationCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
