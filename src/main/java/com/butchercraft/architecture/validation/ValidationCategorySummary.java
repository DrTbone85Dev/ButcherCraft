package com.butchercraft.architecture.validation;

public record ValidationCategorySummary(
        int passed,
        int failed,
        int warnings,
        int informational
) {
    public ValidationCategorySummary {
        if (passed < 0 || failed < 0 || warnings < 0 || informational < 0) {
            throw new IllegalArgumentException("Validation category counts cannot be negative");
        }
    }

    public int total() {
        return passed + failed + warnings + informational;
    }
}
