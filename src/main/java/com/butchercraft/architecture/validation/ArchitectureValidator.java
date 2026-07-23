package com.butchercraft.architecture.validation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ArchitectureValidator {
    private final ValidationRuleRegistry rules;

    public ArchitectureValidator(ValidationRuleRegistry rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    public ValidationReport validate(ValidationContext context) {
        return validate(context, Duration.ZERO);
    }

    public ValidationReport validate(ValidationContext context, Duration executionTime) {
        ValidationContext candidate = Objects.requireNonNull(context, "context");
        Duration suppliedExecutionTime = Objects.requireNonNull(executionTime, "executionTime");
        if (suppliedExecutionTime.isNegative()) {
            throw new IllegalArgumentException("executionTime cannot be negative");
        }
        List<ValidationResult> results = new ArrayList<>(rules.size());
        for (ValidationRule rule : rules.rules()) {
            results.add(execute(rule, candidate));
        }
        return ValidationReport.of(candidate.id(), results, suppliedExecutionTime);
    }

    public ValidationRuleRegistry rules() {
        return rules;
    }

    private static ValidationResult execute(ValidationRule rule, ValidationContext context) {
        try {
            ValidationResult result = Objects.requireNonNull(rule.validate(context), "rule result");
            if (!result.ruleId().equals(rule.id())
                    || result.category() != rule.category()
                    || result.severity() != rule.severity()) {
                throw new IllegalStateException("Rule result metadata does not match the registered rule");
            }
            return result;
        } catch (RuntimeException failure) {
            return ValidationResult.malformedRule(rule, failure);
        }
    }
}
