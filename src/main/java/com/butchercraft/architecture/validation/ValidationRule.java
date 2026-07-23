package com.butchercraft.architecture.validation;

public interface ValidationRule {
    ArchitectureId id();

    String description();

    ValidationCategory category();

    ValidationSeverity severity();

    ValidationResult validate(ValidationContext context);
}
