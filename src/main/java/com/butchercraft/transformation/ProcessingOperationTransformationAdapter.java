package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.quantity.ProductQuantity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compatibility adapter from existing processing operations into transformation definitions.
 */
public final class ProcessingOperationTransformationAdapter {
    private ProcessingOperationTransformationAdapter() {
    }

    public static TransformationDefinition fromProcessingOperation(
            ProcessingOperation operation,
            ProductQuantity inputQuantity
    ) {
        return fromProcessingOperation(operation, inputQuantity, Optional.empty());
    }

    public static TransformationDefinition fromProcessingOperation(
            ProcessingOperation operation,
            ProductQuantity inputQuantity,
            Optional<EngineId> workstationCapability
    ) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(inputQuantity, "inputQuantity");
        Objects.requireNonNull(workstationCapability, "workstationCapability");
        workstationCapability.ifPresent(capability -> Objects.requireNonNull(capability, "workstationCapability value"));

        MaterialAmount inputAmount = new MaterialAmount(operation.requiredProductType(), inputQuantity);
        List<ProductQuantity> outputQuantities = operation.outputQuantities(inputQuantity, 0);
        List<TransformationOutput> outputs = new ArrayList<>();
        for (int index = 0; index < operation.outputs().size(); index++) {
            MaterialAmount outputAmount = new MaterialAmount(
                    operation.outputs().get(index).productType(),
                    outputQuantities.get(index)
            );
            outputs.add(new TransformationOutput(outputAmount, TransformationOutputClassification.PRIMARY));
        }

        return new TransformationDefinition(
                new TransformationId(operation.id()),
                operation.name(),
                List.of(new TransformationInput(inputAmount)),
                outputs,
                operation.baseDuration(),
                workstationCapability
        );
    }
}
