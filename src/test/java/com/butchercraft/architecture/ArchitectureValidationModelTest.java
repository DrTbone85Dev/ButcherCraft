package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureComponent;
import com.butchercraft.architecture.validation.ArchitectureId;
import com.butchercraft.architecture.validation.ArchitectureValidator;
import com.butchercraft.architecture.validation.ValidationCategory;
import com.butchercraft.architecture.validation.ValidationCategorySummary;
import com.butchercraft.architecture.validation.ValidationContext;
import com.butchercraft.architecture.validation.ValidationReport;
import com.butchercraft.architecture.validation.ValidationResult;
import com.butchercraft.architecture.validation.ValidationRule;
import com.butchercraft.architecture.validation.ValidationRuleRegistry;
import com.butchercraft.architecture.validation.ValidationSeverity;
import com.butchercraft.architecture.validation.ValidationStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchitectureValidationModelTest {
    @Test
    void architectureIdsNormalizeAndSortDeterministically() {
        ArchitectureId normalized = ArchitectureId.of("  BUTCHERCRAFT:PLANNING  ");

        assertEquals("butchercraft:planning", normalized.value());
        assertTrue(ArchitectureId.of("butchercraft:a").compareTo(ArchitectureId.of("butchercraft:b")) < 0);
    }

    @Test
    void architectureIdsRejectMalformedValuesAndNulls() {
        assertThrows(NullPointerException.class, () -> ArchitectureId.of(null));
        assertThrows(IllegalArgumentException.class, () -> ArchitectureId.of(""));
        assertThrows(IllegalArgumentException.class, () -> ArchitectureId.of("bad id"));
        assertThrows(IllegalArgumentException.class, () -> ArchitectureId.of("9bad:id"));
    }

    @Test
    void validationContextDefensivelyCopiesEveryCollection() {
        List<ArchitectureComponent> components = new ArrayList<>();
        components.add(new ArchitectureComponent(
                ArchitectureId.of("butchercraft:test"),
                "Test",
                "com.butchercraft.test"
        ));
        ValidationContext context = ValidationContext.builder(ArchitectureId.of("butchercraft:context"))
                .component(components.getFirst())
                .build();

        components.clear();

        assertEquals(1, context.components().size());
        assertThrows(UnsupportedOperationException.class, () -> context.components().clear());
    }

    @Test
    void resultRejectsInconsistentStatusSeverityAndDetails() {
        ValidationRule errorRule = rule("butchercraft:test/error", ValidationSeverity.ERROR, false);

        assertThrows(IllegalArgumentException.class, () -> new ValidationResult(
                errorRule.id(),
                errorRule.category(),
                ValidationSeverity.WARNING,
                ValidationStatus.FAILED,
                "failed",
                List.of("detail")
        ));
        assertThrows(IllegalArgumentException.class, () -> new ValidationResult(
                errorRule.id(),
                errorRule.category(),
                ValidationSeverity.ERROR,
                ValidationStatus.PASSED,
                "passed",
                List.of("detail")
        ));
    }

    @Test
    void reportSummarizesPassesFailuresWarningsInformationAndDuration() {
        ValidationRule pass = rule("butchercraft:test/pass", ValidationSeverity.ERROR, true);
        ValidationRule failure = rule("butchercraft:test/failure", ValidationSeverity.ERROR, false);
        ValidationRule warning = rule("butchercraft:test/warning", ValidationSeverity.WARNING, false);
        ValidationRule information = rule("butchercraft:test/information", ValidationSeverity.INFORMATION, false);
        ValidationRuleRegistry registry = ValidationRuleRegistry.of(List.of(
                information, warning, failure, pass
        ));
        ValidationContext context = ValidationContext.builder(ArchitectureId.of("butchercraft:context"))
                .build();

        ValidationReport report = new ArchitectureValidator(registry).validate(context, Duration.ofMillis(17));

        assertFalse(report.successful());
        assertEquals(1, report.summary().passedRules());
        assertEquals(1, report.summary().failedRules());
        assertEquals(1, report.summary().warnings());
        assertEquals(1, report.summary().informationalMessages());
        assertEquals(Duration.ofMillis(17), report.summary().executionTime());
        assertEquals(1, report.passedRules().size());
        assertEquals(1, report.failedRules().size());
        assertEquals(1, report.warnings().size());
        assertEquals(1, report.informationalMessages().size());
        ValidationCategorySummary summary = report.summary().categories().get(ValidationCategory.GENERAL);
        assertEquals(4, summary.total());
    }

    @Test
    void validatorUsesDeterministicZeroDurationByDefault() {
        ValidationReport report = new ArchitectureValidator(
                ValidationRuleRegistry.of(List.of(rule(
                        "butchercraft:test/pass",
                        ValidationSeverity.ERROR,
                        true
                )))
        ).validate(ValidationContext.builder(ArchitectureId.of("butchercraft:context")).build());

        assertEquals(Duration.ZERO, report.summary().executionTime());
    }

    @Test
    void validatorRejectsNullContextAndNegativeSuppliedDuration() {
        ArchitectureValidator validator = new ArchitectureValidator(ValidationRuleRegistry.of(List.of()));
        ValidationContext context = ValidationContext.builder(ArchitectureId.of("butchercraft:context")).build();

        assertThrows(NullPointerException.class, () -> validator.validate(null));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(context, Duration.ofMillis(-1)));
    }

    private static ValidationRule rule(String id, ValidationSeverity severity, boolean passes) {
        return new ValidationRule() {
            @Override
            public ArchitectureId id() {
                return ArchitectureId.of(id);
            }

            @Override
            public String description() {
                return "Test rule";
            }

            @Override
            public ValidationCategory category() {
                return ValidationCategory.GENERAL;
            }

            @Override
            public ValidationSeverity severity() {
                return severity;
            }

            @Override
            public ValidationResult validate(ValidationContext context) {
                return passes
                        ? ValidationResult.passed(this, description())
                        : ValidationResult.issue(this, description(), List.of("test issue"));
            }
        };
    }
}
