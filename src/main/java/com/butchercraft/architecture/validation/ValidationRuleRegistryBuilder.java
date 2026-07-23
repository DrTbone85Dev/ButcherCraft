package com.butchercraft.architecture.validation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ValidationRuleRegistryBuilder {
    private final List<ValidationRule> rules = new ArrayList<>();
    private final Set<ArchitectureId> ids = new LinkedHashSet<>();

    public ValidationRuleRegistryBuilder register(ValidationRule rule) {
        ValidationRule candidate = Objects.requireNonNull(rule, "rule");
        ArchitectureId id = Objects.requireNonNull(candidate.id(), "rule id");
        if (!ids.add(id)) {
            throw new IllegalArgumentException("Duplicate validation rule id: " + id.value());
        }
        rules.add(candidate);
        return this;
    }

    public ValidationRuleRegistryBuilder registerAll(Iterable<? extends ValidationRule> additions) {
        Objects.requireNonNull(additions, "additions");
        for (ValidationRule rule : additions) {
            register(rule);
        }
        return this;
    }

    public ValidationRuleRegistry build() {
        return ValidationRuleRegistry.of(rules);
    }
}
