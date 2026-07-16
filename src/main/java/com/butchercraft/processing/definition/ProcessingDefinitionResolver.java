package com.butchercraft.processing.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.context.ProcessingFactor;
import com.butchercraft.engine.modifier.ProcessingModifier;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.validation.ValidationRule;
import com.butchercraft.engine.validation.ValidationRules;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ProcessingDefinitionResolver {
    private final DefinitionRegistryView definitions;

    public ProcessingDefinitionResolver(DefinitionRegistryView definitions) {
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public DefinitionRegistryView definitions() {
        return definitions;
    }

    public DefinitionResolution<SpeciesDefinition> resolveSpecies(ResourceLocation speciesId) {
        Objects.requireNonNull(speciesId, "speciesId");
        SpeciesDefinition species = definitions.species().get(speciesId);
        if (species == null) {
            return DefinitionResolution.failure(DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_species",
                    speciesId,
                    "Species definition is not loaded"
            )));
        }
        if (!definitions.processingProfiles().containsKey(species.processingProfile())) {
            return DefinitionResolution.failure(DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_processing_profile",
                    speciesId,
                    "Species references missing processing profile " + species.processingProfile()
            )));
        }
        return DefinitionResolution.success(species);
    }

    public DefinitionResolution<ProcessingProfileDefinition> resolveProcessingProfile(ResourceLocation profileId) {
        Objects.requireNonNull(profileId, "profileId");
        ProcessingProfileDefinition profile = definitions.processingProfiles().get(profileId);
        if (profile == null) {
            return DefinitionResolution.failure(DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_processing_profile",
                    profileId,
                    "Processing profile definition is not loaded"
            )));
        }
        return DefinitionResolution.success(profile);
    }

    public DefinitionResolution<ProductDefinition> resolveProduct(ResourceLocation productId) {
        Objects.requireNonNull(productId, "productId");
        ProductDefinition product = definitions.products().get(productId);
        if (product == null) {
            return DefinitionResolution.failure(DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_product",
                    productId,
                    "Product definition is not loaded"
            )));
        }
        if (!definitions.species().containsKey(product.species())) {
            return DefinitionResolution.failure(DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_species",
                    productId,
                    "Product references missing species " + product.species()
            )));
        }
        return DefinitionResolution.success(product);
    }

    public DefinitionResolution<ResolvedProcessingOperationDefinition> resolveOperation(ResourceLocation operationId) {
        Objects.requireNonNull(operationId, "operationId");
        ProcessingOperationDefinition operation = definitions.operations().get(operationId);
        if (operation == null) {
            return DefinitionResolution.failure(DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_processing_operation",
                    operationId,
                    "Processing operation definition is not loaded"
            )));
        }

        DefinitionValidationReport report = validateOperation(operationId, operation);
        if (report.hasErrors()) {
            return DefinitionResolution.failure(report);
        }

        ProductDefinition inputProduct = definitions.products().get(operation.inputProduct());
        List<ProductDefinition> outputProducts = operation.outputs().stream()
                .map(output -> definitions.products().get(output.product()))
                .toList();
        SpeciesDefinition inputSpecies = definitions.species().get(inputProduct.species());
        List<SpeciesDefinition> outputSpecies = outputProducts.stream()
                .map(output -> definitions.species().get(output.species()))
                .toList();
        ProcessingProfileDefinition inputProfile = definitions.processingProfiles().get(inputSpecies.processingProfile());
        return DefinitionResolution.success(new ResolvedProcessingOperationDefinition(
                operationId,
                operation,
                inputProduct,
                outputProducts,
                inputSpecies,
                outputSpecies,
                inputProfile
        ), report);
    }

    public DefinitionResolution<ProcessingOperation> toEngineOperation(ResourceLocation operationId) {
        DefinitionResolution<ResolvedProcessingOperationDefinition> resolved = resolveOperation(operationId);
        if (!resolved.succeeded()) {
            return DefinitionResolution.failure(resolved.report());
        }

        ResolvedProcessingOperationDefinition definition = resolved.orThrow();
        try {
            ProcessingOperationDefinition operation = definition.operation();
            ProductCategory category = ProductCategory.fromId(EngineId.of(definition.inputProduct().productCategory().toString()));
            ProcessingState requiredState = ProcessingState.fromId(EngineId.of(operation.requiredInputProcessingState().toString()));
            List<ValidationRule> rules = new ArrayList<>();
            rules.add(ValidationRules.requiredProductType());
            rules.add(ValidationRules.requiredSourceCategory());
            rules.add(ValidationRules.requiredProcessingState());
            rules.add(ValidationRules.minimumQuantity(operation.minimumInputQuantity().toEngineQuantity()));
            rules.add(ValidationRules.minimumCleanliness(ProcessingFactor.of(operation.minimumCleanlinessFactor())));
            rules.add(ValidationRules.minimumEquipmentCondition(ProcessingFactor.of(operation.minimumEquipmentConditionFactor())));
            if (!operation.zeroOutputPolicy().permitsZeroOutput()) {
                rules.add(ValidationRules.zeroOutputNotPermitted());
            }

            List<ProcessingModifier> modifiers = operation.staticModifiers().stream()
                    .map(StaticModifierDefinition::toEngineModifier)
                    .toList();
            List<com.butchercraft.engine.operation.ProcessingOutputDefinition> outputs = operation.outputs().stream()
                    .map(ProcessingOutputDefinition::toEngineOutput)
                    .toList();

            return DefinitionResolution.success(new ProcessingOperation(
                    EngineId.of(operationId.toString()),
                    operation.displayNameKey(),
                    EngineId.of(operation.inputProduct().toString()),
                    Optional.of(category),
                    requiredState,
                    ProcessingDuration.milliseconds(operation.baseDurationMilliseconds()),
                    outputs,
                    rules,
                    modifiers,
                    operation.zeroOutputPolicy().permitsZeroOutput()
            ), resolved.report());
        } catch (RuntimeException exception) {
            return DefinitionResolution.failure(resolved.report().plus(DefinitionValidationIssue.error(
                    "engine_conversion_failed",
                    operationId,
                    "Operation cannot be converted to the current engine operation model: " + exception.getMessage()
            )));
        }
    }

    public DefinitionValidationReport validateAll() {
        List<DefinitionValidationReport> reports = new ArrayList<>();
        for (var entry : definitions.species().entrySet()) {
            reports.add(validateSpecies(entry.getKey(), entry.getValue()));
        }
        for (var entry : definitions.products().entrySet()) {
            reports.add(validateProduct(entry.getKey(), entry.getValue()));
        }
        for (var entry : definitions.operations().entrySet()) {
            reports.add(validateOperation(entry.getKey(), entry.getValue()));
        }
        return DefinitionValidationReport.combine(reports);
    }

    private DefinitionValidationReport validateSpecies(ResourceLocation speciesId, SpeciesDefinition species) {
        if (!definitions.processingProfiles().containsKey(species.processingProfile())) {
            return DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_processing_profile",
                    speciesId,
                    "Species references missing processing profile " + species.processingProfile()
            ));
        }
        return DefinitionValidationReport.EMPTY;
    }

    private DefinitionValidationReport validateProduct(ResourceLocation productId, ProductDefinition product) {
        if (!definitions.species().containsKey(product.species())) {
            return DefinitionValidationReport.of(DefinitionValidationIssue.error(
                    "missing_species",
                    productId,
                    "Product references missing species " + product.species()
            ));
        }
        return DefinitionValidationReport.EMPTY;
    }

    public DefinitionValidationReport validateOperation(ResourceLocation operationId, ProcessingOperationDefinition operation) {
        DefinitionValidationReport report = DefinitionValidationReport.EMPTY;

        ProductDefinition input = definitions.products().get(operation.inputProduct());
        if (input == null) {
            report = report.plus(DefinitionValidationIssue.error(
                    "missing_input_product",
                    operationId,
                    "Operation references missing input product " + operation.inputProduct()
            ));
        }
        List<ProductDefinition> outputs = new ArrayList<>();
        Set<ResourceLocation> seenOutputProducts = new HashSet<>();
        for (ProcessingOutputDefinition output : operation.outputs()) {
            if (!seenOutputProducts.add(output.product())) {
                report = report.plus(DefinitionValidationIssue.error(
                        "duplicate_output_product",
                        operationId,
                        "Operation defines duplicate output product " + output.product()
                ));
            }
            ProductDefinition outputProduct = definitions.products().get(output.product());
            if (outputProduct == null) {
                report = report.plus(DefinitionValidationIssue.error(
                        "missing_output_product",
                        operationId,
                        "Operation references missing output product " + output.product()
                ));
            } else {
                outputs.add(outputProduct);
            }
        }
        for (ResourceLocation requiredProfile : operation.requiredProcessingProfiles()) {
            if (!definitions.processingProfiles().containsKey(requiredProfile)) {
                report = report.plus(DefinitionValidationIssue.error(
                        "missing_required_processing_profile",
                        operationId,
                        "Operation requires missing processing profile " + requiredProfile
                ));
            }
        }
        if (input == null || outputs.size() != operation.outputs().size()) {
            return report;
        }

        if (!operation.requiredInputProcessingState().equals(input.processingState())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "input_state_mismatch",
                    operationId,
                    "Operation required input state does not match the input product definition state"
            ));
        }
        if (!operation.minimumInputQuantity().unit().equals(input.quantityUnit())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "minimum_quantity_unit_mismatch",
                    operationId,
                    "Operation minimum quantity unit does not match the input product quantity unit"
            ));
        }
        if (totalYieldExceedsIdentity(operation.outputs())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "total_yield_too_high",
                    operationId,
                    "Operation output yield ratios must sum to 100% or less"
            ));
        }
        if (!operation.zeroOutputPolicy().permitsZeroOutput() && totalYieldIsZero(operation.outputs())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "zero_output_not_permitted",
                    operationId,
                    "Operation forbids zero output but all output yields are zero"
            ));
        }

        for (int index = 0; index < operation.outputs().size(); index++) {
            ProcessingOutputDefinition output = operation.outputs().get(index);
            ProductDefinition outputProduct = outputs.get(index);
            if (operation.inputProduct().equals(output.product()) && !operation.selfLoopPermitted()) {
                report = report.plus(DefinitionValidationIssue.error(
                        "self_loop_not_permitted",
                        operationId,
                        "Operation transforms a product into itself but self loops are not permitted"
                ));
            }
            if (!operation.crossSpeciesPermitted() && !input.species().equals(outputProduct.species())) {
                report = report.plus(DefinitionValidationIssue.error(
                        "species_mismatch",
                        operationId,
                        "Operation input species " + input.species() + " does not match output species " + outputProduct.species()
                ));
            }
            if (!definitions.species().containsKey(outputProduct.species())) {
                report = report.plus(DefinitionValidationIssue.error(
                        "missing_output_species",
                        operationId,
                        "Operation output product references missing species " + outputProduct.species()
                ));
            }
            if (!output.state().equals(outputProduct.processingState())) {
                report = report.plus(DefinitionValidationIssue.error(
                        "output_state_mismatch",
                        operationId,
                        "Operation output state does not match output product definition state for " + output.product()
                ));
            }
            if (!output.quantityUnit().equals(outputProduct.quantityUnit())) {
                report = report.plus(DefinitionValidationIssue.error(
                        "output_quantity_unit_mismatch",
                        operationId,
                        "Operation output quantity unit does not match output product quantity unit for " + output.product()
                ));
            }
            if (!output.allowZero() && output.yield().numerator() == 0) {
                report = report.plus(DefinitionValidationIssue.error(
                        "zero_output_not_permitted",
                        operationId,
                        "Operation output forbids zero output but has a zero yield for " + output.product()
                ));
            }
        }

        SpeciesDefinition inputSpecies = definitions.species().get(input.species());
        if (inputSpecies == null) {
            report = report.plus(DefinitionValidationIssue.error(
                    "missing_input_species",
                    operationId,
                    "Operation input product references missing species " + input.species()
            ));
            return report;
        }

        ProcessingProfileDefinition inputProfile = definitions.processingProfiles().get(inputSpecies.processingProfile());
        if (inputProfile == null) {
            report = report.plus(DefinitionValidationIssue.error(
                    "missing_input_processing_profile",
                    operationId,
                    "Operation input species references missing processing profile " + inputSpecies.processingProfile()
            ));
            return report;
        }

        if (!operation.requiredProcessingProfiles().contains(inputSpecies.processingProfile())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "profile_mismatch",
                    operationId,
                    "Operation does not allow the input species processing profile " + inputSpecies.processingProfile()
            ));
        }
        if (!inputProfile.allowsOperationCategory(operation.operationCategory())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "operation_category_not_allowed",
                    operationId,
                    "Input species processing profile does not allow operation category " + operation.operationCategory()
            ));
        }
        return report;
    }

    private static boolean totalYieldExceedsIdentity(List<ProcessingOutputDefinition> outputs) {
        Fraction total = totalYield(outputs);
        return total.numerator().compareTo(total.denominator()) > 0;
    }

    private static boolean totalYieldIsZero(List<ProcessingOutputDefinition> outputs) {
        return totalYield(outputs).numerator().signum() == 0;
    }

    private static Fraction totalYield(List<ProcessingOutputDefinition> outputs) {
        BigInteger denominator = BigInteger.ONE;
        for (ProcessingOutputDefinition output : outputs) {
            denominator = lcm(denominator, BigInteger.valueOf(output.yield().denominator()));
        }
        BigInteger numerator = BigInteger.ZERO;
        for (ProcessingOutputDefinition output : outputs) {
            BigInteger outputDenominator = BigInteger.valueOf(output.yield().denominator());
            numerator = numerator.add(BigInteger.valueOf(output.yield().numerator())
                    .multiply(denominator.divide(outputDenominator)));
        }
        return new Fraction(numerator, denominator);
    }

    private static BigInteger lcm(BigInteger first, BigInteger second) {
        return first.divide(first.gcd(second)).multiply(second);
    }

    private record Fraction(BigInteger numerator, BigInteger denominator) {
    }
}
