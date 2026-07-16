package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record ProcessingGraphEdge(
        ResourceLocation operationId,
        ResourceLocation inputProduct,
        List<ResourceLocation> outputProducts
) implements Comparable<ProcessingGraphEdge> {
    private static final Comparator<ProcessingGraphEdge> ORDERING = Comparator
            .comparing((ProcessingGraphEdge edge) -> edge.operationId().toString())
            .thenComparing(edge -> edge.inputProduct().toString())
            .thenComparing(edge -> edge.outputProducts().stream()
                    .map(ResourceLocation::toString)
                    .reduce("", (first, second) -> first + "\n" + second));

    public ProcessingGraphEdge {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(inputProduct, "inputProduct");
        outputProducts = List.copyOf(Objects.requireNonNull(outputProducts, "outputProducts"));
        if (outputProducts.isEmpty()) {
            throw new IllegalArgumentException("Graph edge must have at least one output product");
        }
    }

    public ResourceLocation outputProduct() {
        return outputProducts.getFirst();
    }

    @Override
    public int compareTo(ProcessingGraphEdge other) {
        return ORDERING.compare(this, other);
    }
}
