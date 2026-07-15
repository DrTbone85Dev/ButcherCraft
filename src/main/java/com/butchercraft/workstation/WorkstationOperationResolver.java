package com.butchercraft.workstation;

import com.butchercraft.engine.product.Product;
import com.butchercraft.processing.definition.DefinitionRegistryLoadResult;
import com.butchercraft.processing.definition.DefinitionRegistryView;
import com.butchercraft.processing.definition.DefinitionResolution;
import com.butchercraft.processing.definition.DefinitionValidationIssue;
import com.butchercraft.processing.definition.ProcessingDefinitionResolver;
import com.butchercraft.processing.definition.ProcessingGraph;
import com.butchercraft.processing.definition.ProductStackDefinitionValidator;
import com.butchercraft.processing.definition.ResolvedProcessingOperationDefinition;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductDataResult;
import com.butchercraft.product.integration.ProductStackAdapter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class WorkstationOperationResolver implements WorkstationOperationLookup {
    @Override
    public WorkstationOperationResolution resolve(
            RegistryAccess registryAccess,
            WorkstationCapability capability,
            ItemStack inputStack
    ) {
        Objects.requireNonNull(registryAccess, "registryAccess");
        DefinitionRegistryLoadResult loadResult = DefinitionRegistryView.fromRegistryAccess(registryAccess);
        if (!loadResult.allRegistriesAvailable()) {
            return WorkstationOperationResolution.failure(WorkstationFailure.of(
                    WorkstationFailureCode.REGISTRY_NOT_AVAILABLE,
                    "One or more ButcherCraft definition registries are unavailable"
            ));
        }
        return resolve(loadResult.view(), capability, inputStack);
    }

    public WorkstationOperationResolution resolve(
            DefinitionRegistryView definitions,
            WorkstationCapability capability,
            ItemStack inputStack
    ) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(inputStack, "inputStack");
        if (inputStack.isEmpty()) {
            return WorkstationOperationResolution.failure(WorkstationFailure.of(
                    WorkstationFailureCode.NO_INPUT,
                    "Input slot is empty"
            ));
        }

        ProductDataResult<ProductStackData> dataResult = ProductStackAdapter.readProductData(inputStack);
        if (!dataResult.succeeded()) {
            String code = dataResult.failureReason().orElseThrow().code();
            WorkstationFailureCode failureCode = "missing_product_data".equals(code)
                    ? WorkstationFailureCode.MISSING_PRODUCT_DATA
                    : WorkstationFailureCode.INPUT_NOT_PRODUCT;
            return WorkstationOperationResolution.failure(WorkstationFailure.of(
                    failureCode,
                    dataResult.failureReason().orElseThrow().message()
            ));
        }

        ProductStackData data = dataResult.orThrow();
        ResourceLocation productId = ResourceLocation.tryParse(data.productTypeId());
        if (productId == null) {
            return WorkstationOperationResolution.failure(WorkstationFailure.of(
                    WorkstationFailureCode.PRODUCT_DATA_MISMATCH,
                    "Product stack contains an invalid product id"
            ));
        }

        var stackValidation = ProductStackDefinitionValidator.validate(data, definitions);
        if (stackValidation.hasErrors()) {
            DefinitionValidationIssue issue = stackValidation.issues().getFirst();
            WorkstationFailureCode failureCode = "unknown_product".equals(issue.reasonCode())
                    ? WorkstationFailureCode.UNKNOWN_PRODUCT_DEFINITION
                    : WorkstationFailureCode.PRODUCT_DATA_MISMATCH;
            return WorkstationOperationResolution.failure(new WorkstationFailure(
                    failureCode,
                    issue.definitionId().map(List::of).orElse(List.of()),
                    issue.explanation()
            ));
        }

        ProductDataResult<Product> productResult = ProductStackAdapter.toProduct(data);
        if (!productResult.succeeded()) {
            return WorkstationOperationResolution.failure(WorkstationFailure.of(
                    WorkstationFailureCode.PRODUCT_DATA_MISMATCH,
                    productResult.failureReason().orElseThrow().message()
            ));
        }

        Product inputProduct = productResult.orThrow();
        ProcessingGraph graph = ProcessingGraph.fromDefinitions(definitions);
        if (graph.validationReport().hasErrors()) {
            return WorkstationOperationResolution.failure(WorkstationFailure.of(
                    WorkstationFailureCode.PROCESSING_VALIDATION_REJECTED,
                    "Processing graph contains validation errors"
            ));
        }

        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(definitions);
        List<ResolvedWorkstationOperation> compatible = new ArrayList<>();
        WorkstationFailure lastRejection = null;
        var edges = graph.operationsAvailableFor(productId).stream()
                .sorted(Comparator.comparing(edge -> edge.operationId().toString()))
                .toList();
        for (var edge : edges) {
            DefinitionResolution<ResolvedProcessingOperationDefinition> resolved = resolver.resolveOperation(edge.operationId());
            if (!resolved.succeeded()) {
                lastRejection = WorkstationFailure.of(
                        WorkstationFailureCode.PROCESSING_VALIDATION_REJECTED,
                        edge.operationId(),
                        resolved.report().issues().toString()
                );
                continue;
            }

            ResolvedProcessingOperationDefinition operationDefinition = resolved.orThrow();
            ResourceLocation profileId = operationDefinition.inputSpecies().processingProfile();
            if (!capability.allowsProcessingProfile(profileId)) {
                lastRejection = WorkstationFailure.of(
                        WorkstationFailureCode.OPERATION_PROFILE_MISMATCH,
                        edge.operationId(),
                        "Workstation does not allow processing profile " + profileId
                );
                continue;
            }
            if (!supportsOperation(capability, operationDefinition)) {
                lastRejection = WorkstationFailure.of(
                        WorkstationFailureCode.OPERATION_CAPABILITY_MISMATCH,
                        edge.operationId(),
                        "Workstation does not support operation category or workstation capability"
                );
                continue;
            }
            if (data.quantityValue() < operationDefinition.operation().minimumInputQuantity().amount()) {
                lastRejection = WorkstationFailure.of(
                        WorkstationFailureCode.INPUT_QUANTITY_TOO_LOW,
                        edge.operationId(),
                        "Input quantity is below operation minimum"
                );
                continue;
            }
            if (data.quantityValue() > capability.maxInputQuantity()) {
                lastRejection = WorkstationFailure.of(
                        WorkstationFailureCode.INPUT_QUANTITY_TOO_HIGH,
                        edge.operationId(),
                        "Input quantity exceeds workstation maximum"
                );
                continue;
            }

            var engineOperation = resolver.toEngineOperation(edge.operationId());
            if (!engineOperation.succeeded()) {
                lastRejection = WorkstationFailure.of(
                        WorkstationFailureCode.PROCESSING_VALIDATION_REJECTED,
                        edge.operationId(),
                        engineOperation.report().issues().toString()
                );
                continue;
            }

            compatible.add(new ResolvedWorkstationOperation(
                    edge.operationId(),
                    operationDefinition,
                    engineOperation.orThrow(),
                    inputProduct,
                    operationDefinition.outputProduct(),
                    WorkstationDuration.millisecondsToTicks(operationDefinition.operation().baseDurationMilliseconds())
            ));
        }

        if (compatible.isEmpty()) {
            return WorkstationOperationResolution.failure(lastRejection == null
                    ? WorkstationFailure.of(WorkstationFailureCode.NO_COMPATIBLE_OPERATION, productId, "No operation starts from this product")
                    : lastRejection);
        }
        if (compatible.size() > 1) {
            return WorkstationOperationResolution.multiple(compatible.stream()
                    .map(ResolvedWorkstationOperation::operationId)
                    .sorted(Comparator.comparing(ResourceLocation::toString))
                    .toList());
        }
        return WorkstationOperationResolution.success(compatible.getFirst());
    }

    private static boolean supportsOperation(
            WorkstationCapability capability,
            ResolvedProcessingOperationDefinition definition
    ) {
        if (capability.supportsOperationCategory(definition.operation().operationCategory())) {
            return true;
        }
        return definition.operation().workstationCapability()
                .map(capability::supportsWorkstationCapability)
                .orElse(false);
    }
}
