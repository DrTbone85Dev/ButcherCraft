package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record OperationDefinitionEntry(ResourceLocation id, ProcessingOperationDefinition definition) {
    public OperationDefinitionEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(definition, "definition");
    }
}
