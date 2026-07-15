package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record ResolvedProcessingOperationDefinition(
        ResourceLocation id,
        ProcessingOperationDefinition operation,
        ProductDefinition inputProduct,
        ProductDefinition outputProduct,
        SpeciesDefinition inputSpecies,
        SpeciesDefinition outputSpecies,
        ProcessingProfileDefinition inputProcessingProfile
) {
    public ResolvedProcessingOperationDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(inputProduct, "inputProduct");
        Objects.requireNonNull(outputProduct, "outputProduct");
        Objects.requireNonNull(inputSpecies, "inputSpecies");
        Objects.requireNonNull(outputSpecies, "outputSpecies");
        Objects.requireNonNull(inputProcessingProfile, "inputProcessingProfile");
    }
}
