package com.butchercraft.workstation;

import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.product.Product;
import com.butchercraft.processing.definition.ProductDefinition;
import com.butchercraft.processing.definition.ResolvedProcessingOperationDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ResolvedWorkstationOperation(
        ResourceLocation operationId,
        ResolvedProcessingOperationDefinition definition,
        ProcessingOperation engineOperation,
        Product inputProduct,
        ProductDefinition outputProductDefinition,
        int totalTicks
) {
    public ResolvedWorkstationOperation {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(engineOperation, "engineOperation");
        Objects.requireNonNull(inputProduct, "inputProduct");
        Objects.requireNonNull(outputProductDefinition, "outputProductDefinition");
        if (totalTicks <= 0) {
            throw new IllegalArgumentException("Total ticks must be positive");
        }
    }
}
