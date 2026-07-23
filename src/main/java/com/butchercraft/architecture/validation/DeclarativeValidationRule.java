package com.butchercraft.architecture.validation;

import java.util.Objects;
import java.util.List;
import java.util.function.Function;

final class DeclarativeValidationRule implements ValidationRule {
    private final ArchitectureId id;
    private final String description;
    private final ValidationCategory category;
    private final ValidationSeverity severity;
    private final Function<ValidationContext, List<String>> evaluator;

    DeclarativeValidationRule(
            String id,
            String description,
            ValidationCategory category,
            ValidationSeverity severity,
            Function<ValidationContext, List<String>> evaluator
    ) {
        this.id = ArchitectureId.of(id);
        this.description = Objects.requireNonNull(description, "description").strip();
        if (this.description.isEmpty()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        this.category = Objects.requireNonNull(category, "category");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public ArchitectureId id() {
        return id;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ValidationCategory category() {
        return category;
    }

    @Override
    public ValidationSeverity severity() {
        return severity;
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        List<String> details = Objects.requireNonNull(
                evaluator.apply(Objects.requireNonNull(context, "context")),
                "rule details"
        );
        return details.isEmpty()
                ? ValidationResult.passed(this, description)
                : ValidationResult.issue(this, description, details);
    }
}
