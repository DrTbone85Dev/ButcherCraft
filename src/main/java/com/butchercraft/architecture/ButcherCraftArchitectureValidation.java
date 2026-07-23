package com.butchercraft.architecture;

import com.butchercraft.architecture.validation.ArchitectureRules;
import com.butchercraft.architecture.validation.ArchitectureValidator;
import com.butchercraft.architecture.validation.ValidationReport;
import com.butchercraft.architecture.validation.ValidationRuleRegistry;

public final class ButcherCraftArchitectureValidation {
    private static final ValidationRuleRegistry RULES = ArchitectureRules.standardRegistry();

    private ButcherCraftArchitectureValidation() {
    }

    public static ValidationReport validateCurrentArchitecture() {
        return new ArchitectureValidator(RULES).validate(ButcherCraftArchitectureManifest.current());
    }

    public static ValidationRuleRegistry rules() {
        return RULES;
    }
}
