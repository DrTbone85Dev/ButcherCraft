package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ArchitectureValidator;
import com.butchercraft.architecture.validation.ValidationCategory;
import com.butchercraft.architecture.validation.ValidationContext;
import com.butchercraft.architecture.validation.ValidationResult;
import com.butchercraft.architecture.validation.ValidationRule;
import com.butchercraft.architecture.validation.ValidationRuleRegistry;
import com.butchercraft.architecture.validation.ValidationSeverity;
import com.butchercraft.architecture.validation.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationRuleRegistryTest {
    @Test
    void registryCanonicalizesRuleOrderIndependentOfRegistrationOrder() {
        ValidationRule zeta = passRule("butchercraft:test/zeta", ValidationCategory.SIMULATION);
        ValidationRule alpha = passRule("butchercraft:test/alpha", ValidationCategory.GENERAL);
        ValidationRule beta = passRule("butchercraft:test/beta", ValidationCategory.GENERAL);

        ValidationRuleRegistry registry = ValidationRuleRegistry.of(List.of(zeta, beta, alpha));

        assertEquals(List.of(zeta, alpha, beta), registry.rules());
        assertEquals(3, registry.size());
        assertTrue(registry.contains(alpha.id()));
        assertEquals(alpha, registry.find(alpha.id()).orElseThrow());
        assertEquals(List.of(alpha, beta), registry.findByCategory(ValidationCategory.GENERAL));
        assertEquals(3, registry.stream().count());
    }

    @Test
    void builderAndImmutableRegistryRejectDuplicateRuleIds() {
        ValidationRule first = passRule("butchercraft:test/duplicate", ValidationCategory.GENERAL);
        ValidationRule second = passRule("butchercraft:test/duplicate", ValidationCategory.SIMULATION);

        assertThrows(IllegalArgumentException.class, () -> ValidationRuleRegistry.builder()
                .register(first)
                .register(second));
        assertThrows(IllegalArgumentException.class, () -> ValidationRuleRegistry.of(List.of(first, second)));
    }

    @Test
    void registryRejectsNullAndMalformedRules() {
        assertThrows(NullPointerException.class, () -> ValidationRuleRegistry.of(List.of((ValidationRule) null)));
        ValidationRule blankDescription = new TestRule(
                "butchercraft:test/blank",
                "",
                ValidationCategory.GENERAL,
                ValidationSeverity.ERROR,
                false
        );
        assertThrows(IllegalArgumentException.class, () -> ValidationRuleRegistry.of(List.of(blankDescription)));
    }

    @Test
    void validatorConvertsRuleExceptionsIntoDeterministicFailures() {
        ValidationRule malformed = new TestRule(
                "butchercraft:test/malformed",
                "Malformed",
                ValidationCategory.GENERAL,
                ValidationSeverity.ERROR,
                true
        );
        ArchitectureValidator validator = new ArchitectureValidator(ValidationRuleRegistry.of(List.of(malformed)));
        ValidationContext context = ValidationContext.builder(ArchitectureId.of("butchercraft:context")).build();

        ValidationResult result = validator.validate(context).results().getFirst();

        assertEquals(ValidationStatus.FAILED, result.status());
        assertEquals(List.of("IllegalStateException: deterministic failure"), result.details());
    }

    @Test
    void customRulesExtendFrameworkWithoutChangingValidator() {
        ValidationRule custom = passRule("butchercraft:custom/extension", ValidationCategory.ALLOCATION);
        ArchitectureValidator validator = new ArchitectureValidator(ValidationRuleRegistry.of(List.of(custom)));

        ValidationResult result = validator.validate(
                ValidationContext.builder(ArchitectureId.of("butchercraft:context")).build()
        ).results().getFirst();

        assertEquals(custom.id(), result.ruleId());
        assertEquals(ValidationCategory.ALLOCATION, result.category());
        assertEquals(ValidationStatus.PASSED, result.status());
    }

    private static ValidationRule passRule(String id, ValidationCategory category) {
        return new TestRule(id, "Pass", category, ValidationSeverity.ERROR, false);
    }

    private record TestRule(
            String rawId,
            String description,
            ValidationCategory category,
            ValidationSeverity severity,
            boolean throwsFailure
    ) implements ValidationRule {
        @Override
        public ArchitectureId id() {
            return ArchitectureId.of(rawId);
        }

        @Override
        public ValidationResult validate(ValidationContext context) {
            if (throwsFailure) {
                throw new IllegalStateException("deterministic failure");
            }
            return ValidationResult.passed(this, description);
        }
    }
}
