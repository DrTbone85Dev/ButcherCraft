package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

public record ResolvedProcessingOperationDefinition(
        ResourceLocation id,
        ProcessingOperationDefinition operation,
        ProductDefinition inputProduct,
        List<ProductDefinition> outputProducts,
        SpeciesDefinition inputSpecies,
        List<SpeciesDefinition> outputSpeciesDefinitions,
        ProcessingProfileDefinition inputProcessingProfile
) {
    public ResolvedProcessingOperationDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(inputProduct, "inputProduct");
        outputProducts = List.copyOf(Objects.requireNonNull(outputProducts, "outputProducts"));
        if (outputProducts.isEmpty()) {
            throw new IllegalArgumentException("Resolved operation must have at least one output product");
        }
        Objects.requireNonNull(inputSpecies, "inputSpecies");
        outputSpeciesDefinitions = List.copyOf(Objects.requireNonNull(outputSpeciesDefinitions, "outputSpeciesDefinitions"));
        if (outputSpeciesDefinitions.size() != outputProducts.size()) {
            throw new IllegalArgumentException("Output species count must match output product count");
        }
        Objects.requireNonNull(inputProcessingProfile, "inputProcessingProfile");
    }

    public ProductDefinition outputProduct() {
        return outputProducts.getFirst();
    }

    public SpeciesDefinition outputSpecies() {
        return outputSpeciesDefinitions.getFirst();
    }
}
