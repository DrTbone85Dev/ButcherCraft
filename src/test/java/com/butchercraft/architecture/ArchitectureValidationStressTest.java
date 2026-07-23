package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ArchitectureRules;
import com.butchercraft.architecture.validation.ArchitectureValidator;
import com.butchercraft.architecture.validation.OrderingPolicy;
import com.butchercraft.architecture.validation.RegistryDescriptor;
import com.butchercraft.architecture.validation.RegistryEntryDescriptor;
import com.butchercraft.architecture.validation.ValidationCategory;
import com.butchercraft.architecture.validation.ValidationContext;
import com.butchercraft.architecture.validation.ValidationReport;
import com.butchercraft.architecture.validation.ValidationResult;
import com.butchercraft.architecture.validation.ValidationRule;
import com.butchercraft.architecture.validation.ValidationRuleRegistry;
import com.butchercraft.architecture.validation.ValidationSeverity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureValidationStressTest {
    @Test
    void validatesOneHundredThousandRegistryEntriesDeterministically() {
        List<RegistryEntryDescriptor> entries = new ArrayList<>(100_000);
        for (int index = 0; index < 100_000; index++) {
            entries.add(RegistryEntryDescriptor.of(
                    "butchercraft:entry/item_" + String.format("%06d", index)
            ));
        }
        RegistryDescriptor registry = new RegistryDescriptor(
                "butchercraft:stress_registry",
                OrderingPolicy.CANONICAL_ID,
                entries
        );
        ValidationContext context = ArchitectureValidationTestFixtures.withRegistries(
                ArchitectureValidationTestFixtures.validContext(),
                List.of(registry)
        );
        ValidationRuleRegistry rules = ValidationRuleRegistry.of(List.of(
                ArchitectureRules.registryIdentity(),
                ArchitectureRules.registryOrdering(),
                ArchitectureRules.registryReferences()
        ));
        ArchitectureValidator validator = new ArchitectureValidator(rules);

        ValidationReport first = validator.validate(context);
        ValidationReport second = validator.validate(context);

        assertTrue(first.successful(), () -> "Registry stress failures: " + first.failedRules());
        assertEquals(first, second);
        assertEquals(100_000, registry.entries().size());
    }

    @Test
    void validatesTwentyThousandExtensionRulesInCanonicalOrder() {
        List<ValidationRule> rules = new ArrayList<>(20_000);
        for (int index = 19_999; index >= 0; index--) {
            rules.add(new PassingRule("butchercraft:stress/rule_" + String.format("%05d", index)));
        }
        ArchitectureValidator validator = new ArchitectureValidator(ValidationRuleRegistry.of(rules));
        ValidationContext context = ValidationContext.builder(ArchitectureId.of("butchercraft:stress")).build();

        ValidationReport report = validator.validate(context);

        assertTrue(report.successful());
        assertEquals(20_000, report.summary().ruleCount());
        assertEquals(
                "butchercraft:stress/rule_00000",
                report.results().getFirst().ruleId().value()
        );
        assertEquals(
                "butchercraft:stress/rule_19999",
                report.results().getLast().ruleId().value()
        );
    }

    private record PassingRule(ArchitectureId id) implements ValidationRule {
        private PassingRule(String id) {
            this(ArchitectureId.of(id));
        }

        @Override
        public String description() {
            return "Stress extension rule";
        }

        @Override
        public ValidationCategory category() {
            return ValidationCategory.GENERAL;
        }

        @Override
        public ValidationSeverity severity() {
            return ValidationSeverity.ERROR;
        }

        @Override
        public ValidationResult validate(ValidationContext context) {
            return ValidationResult.passed(this, description());
        }
    }
}
