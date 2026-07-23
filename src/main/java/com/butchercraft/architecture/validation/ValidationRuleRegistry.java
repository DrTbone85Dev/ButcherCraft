package com.butchercraft.architecture.validation;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ValidationRuleRegistry {
    private static final Comparator<ValidationRule> RULE_ORDER = Comparator
            .comparing(ValidationRule::category)
            .thenComparing(ValidationRule::id);

    private final List<ValidationRule> rules;
    private final Map<ArchitectureId, ValidationRule> byId;

    private ValidationRuleRegistry(Collection<ValidationRule> source) {
        List<ValidationRule> ordered = Objects.requireNonNull(source, "source").stream()
                .map(rule -> Objects.requireNonNull(rule, "rule"))
                .sorted(RULE_ORDER)
                .toList();
        java.util.LinkedHashSet<ArchitectureId> seen = new java.util.LinkedHashSet<>();
        for (ValidationRule rule : ordered) {
            Objects.requireNonNull(rule.id(), "rule id");
            Objects.requireNonNull(rule.description(), "rule description");
            Objects.requireNonNull(rule.category(), "rule category");
            Objects.requireNonNull(rule.severity(), "rule severity");
            if (rule.description().isBlank()) {
                throw new IllegalArgumentException("Validation rule description must not be blank: " + rule.id());
            }
            if (!seen.add(rule.id())) {
                throw new IllegalArgumentException("Duplicate validation rule id: " + rule.id().value());
            }
        }
        rules = ordered;
        byId = rules.stream().collect(Collectors.toUnmodifiableMap(ValidationRule::id, Function.identity()));
    }

    public static ValidationRuleRegistry of(Collection<ValidationRule> rules) {
        return new ValidationRuleRegistry(rules);
    }

    public static ValidationRuleRegistryBuilder builder() {
        return new ValidationRuleRegistryBuilder();
    }

    public boolean contains(ArchitectureId id) {
        return byId.containsKey(Objects.requireNonNull(id, "id"));
    }

    public Optional<ValidationRule> find(ArchitectureId id) {
        return Optional.ofNullable(byId.get(Objects.requireNonNull(id, "id")));
    }

    public int size() {
        return rules.size();
    }

    public Stream<ValidationRule> stream() {
        return rules.stream();
    }

    public List<ValidationRule> rules() {
        return rules;
    }

    public List<ValidationRule> findByCategory(ValidationCategory category) {
        Objects.requireNonNull(category, "category");
        return rules.stream().filter(rule -> rule.category() == category).toList();
    }
}
