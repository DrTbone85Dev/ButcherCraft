package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Objects;

public record ProcessingGraphEdge(
        ResourceLocation operationId,
        ResourceLocation inputProduct,
        ResourceLocation outputProduct
) implements Comparable<ProcessingGraphEdge> {
    private static final Comparator<ProcessingGraphEdge> ORDERING = Comparator
            .comparing((ProcessingGraphEdge edge) -> edge.operationId().toString())
            .thenComparing(edge -> edge.inputProduct().toString())
            .thenComparing(edge -> edge.outputProduct().toString());

    public ProcessingGraphEdge {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(inputProduct, "inputProduct");
        Objects.requireNonNull(outputProduct, "outputProduct");
    }

    @Override
    public int compareTo(ProcessingGraphEdge other) {
        return ORDERING.compare(this, other);
    }
}
