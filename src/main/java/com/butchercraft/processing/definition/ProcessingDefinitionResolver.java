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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
        ProductDefinition outputProduct = definitions.products().get(operation.outputProduct());
        SpeciesDefinition inputSpecies = definitions.species().get(inputProduct.species());
        SpeciesDefinition outputSpecies = definitions.species().get(outputProduct.species());
        ProcessingProfileDefinition inputProfile = definitions.processingProfiles().get(inputSpecies.processingProfile());
        return DefinitionResolution.success(new ResolvedProcessingOperationDefinition(
                operationId,
                operation,
                inputProduct,
                outputProduct,
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
            ProcessingState outputState = ProcessingState.fromId(EngineId.of(operation.outputProcessingState().toString()));
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

            return DefinitionResolution.success(new ProcessingOperation(
                    EngineId.of(operationId.toString()),
                    operation.displayNameKey(),
                    EngineId.of(operation.inputProduct().toString()),
                    Optional.of(category),
                    requiredState,
                    EngineId.of(operation.outputProduct().toString()),
                    outputState,
                    ProcessingDuration.milliseconds(operation.baseDurationMilliseconds()),
                    operation.baseYield().toEngineRatio(),
                    operation.baseQualityDelta(),
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
        ProductDefinition output = definitions.products().get(operation.outputProduct());
        if (input == null) {
            report = report.plus(DefinitionValidationIssue.error(
                    "missing_input_product",
                    operationId,
                    "Operation references missing input product " + operation.inputProduct()
            ));
        }
        if (output == null) {
            report = report.plus(DefinitionValidationIssue.error(
                    "missing_output_product",
                    operationId,
                    "Operation references missing output product " + operation.outputProduct()
            ));
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
        if (input == null || output == null) {
            return report;
        }

        if (operation.inputProduct().equals(operation.outputProduct()) && !operation.selfLoopPermitted()) {
            report = report.plus(DefinitionValidationIssue.error(
                    "self_loop_not_permitted",
                    operationId,
                    "Operation transforms a product into itself but self loops are not permitted"
            ));
        }
        if (!operation.crossSpeciesPermitted() && !input.species().equals(output.species())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "species_mismatch",
                    operationId,
                    "Operation input species " + input.species() + " does not match output species " + output.species()
            ));
        }
        if (!operation.requiredInputProcessingState().equals(input.processingState())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "input_state_mismatch",
                    operationId,
                    "Operation required input state does not match the input product definition state"
            ));
        }
        if (!operation.outputProcessingState().equals(output.processingState())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "output_state_mismatch",
                    operationId,
                    "Operation output state does not match the output product definition state"
            ));
        }
        if (!operation.minimumInputQuantity().unit().equals(input.quantityUnit())) {
            report = report.plus(DefinitionValidationIssue.error(
                    "minimum_quantity_unit_mismatch",
                    operationId,
                    "Operation minimum quantity unit does not match the input product quantity unit"
            ));
        }
        if (!operation.zeroOutputPolicy().permitsZeroOutput() && operation.baseYield().numerator() == 0) {
            report = report.plus(DefinitionValidationIssue.error(
                    "zero_output_not_permitted",
                    operationId,
                    "Operation forbids zero output but has a zero base yield"
            ));
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
}
