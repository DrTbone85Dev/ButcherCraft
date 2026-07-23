package com.butchercraft.architecture.validation;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ValidationSummary(
        int ruleCount,
        int passedRules,
        int failedRules,
        int warnings,
        int informationalMessages,
        Duration executionTime,
        Map<ValidationCategory, ValidationCategorySummary> categories
) {
    public ValidationSummary {
        if (ruleCount < 0 || passedRules < 0 || failedRules < 0 || warnings < 0
                || informationalMessages < 0) {
            throw new IllegalArgumentException("Validation summary counts cannot be negative");
        }
        if (ruleCount != passedRules + failedRules + warnings + informationalMessages) {
            throw new IllegalArgumentException("Validation summary counts must equal ruleCount");
        }
        executionTime = Objects.requireNonNull(executionTime, "executionTime");
        if (executionTime.isNegative()) {
            throw new IllegalArgumentException("executionTime cannot be negative");
        }
        EnumMap<ValidationCategory, ValidationCategorySummary> copy = new EnumMap<>(ValidationCategory.class);
        copy.putAll(Objects.requireNonNull(categories, "categories"));
        categories = Collections.unmodifiableMap(copy);
    }

    public static ValidationSummary from(List<ValidationResult> results, Duration executionTime) {
        Objects.requireNonNull(results, "results");
        EnumMap<ValidationCategory, int[]> counts = new EnumMap<>(ValidationCategory.class);
        int passed = 0;
        int failed = 0;
        int warnings = 0;
        int information = 0;
        for (ValidationResult result : results) {
            Objects.requireNonNull(result, "result");
            int[] categoryCounts = counts.computeIfAbsent(result.category(), ignored -> new int[4]);
            switch (result.status()) {
                case PASSED -> {
                    passed++;
                    categoryCounts[0]++;
                }
                case FAILED -> {
                    failed++;
                    categoryCounts[1]++;
                }
                case WARNING -> {
                    warnings++;
                    categoryCounts[2]++;
                }
                case INFORMATIONAL -> {
                    information++;
                    categoryCounts[3]++;
                }
            }
        }
        EnumMap<ValidationCategory, ValidationCategorySummary> categorySummaries =
                new EnumMap<>(ValidationCategory.class);
        counts.forEach((category, values) -> categorySummaries.put(
                category,
                new ValidationCategorySummary(values[0], values[1], values[2], values[3])
        ));
        return new ValidationSummary(
                results.size(),
                passed,
                failed,
                warnings,
                information,
                executionTime,
                categorySummaries
        );
    }

    public boolean successful() {
        return failedRules == 0;
    }
}
