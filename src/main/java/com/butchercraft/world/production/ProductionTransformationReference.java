package com.butchercraft.world.production;

import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodTransformation;
import com.butchercraft.world.goods.IndustryId;

import java.util.Objects;

public record ProductionTransformationReference(
        GoodId inputGoodId,
        GoodId outputGoodId,
        IndustryId owningIndustryId
) implements Comparable<ProductionTransformationReference> {
    public ProductionTransformationReference {
        inputGoodId = Objects.requireNonNull(inputGoodId, "inputGoodId");
        outputGoodId = Objects.requireNonNull(outputGoodId, "outputGoodId");
        owningIndustryId = Objects.requireNonNull(owningIndustryId, "owningIndustryId");
    }

    public static ProductionTransformationReference from(GoodTransformation transformation) {
        Objects.requireNonNull(transformation, "transformation");
        return new ProductionTransformationReference(
                transformation.inputGoodId(),
                transformation.outputGoodId(),
                transformation.owningIndustryId()
        );
    }

    public boolean matches(GoodTransformation transformation) {
        return inputGoodId.equals(transformation.inputGoodId())
                && outputGoodId.equals(transformation.outputGoodId())
                && owningIndustryId.equals(transformation.owningIndustryId());
    }

    @Override
    public int compareTo(ProductionTransformationReference other) {
        int input = inputGoodId.compareTo(Objects.requireNonNull(other, "other").inputGoodId);
        if (input != 0) return input;
        int output = outputGoodId.compareTo(other.outputGoodId);
        return output != 0 ? output : owningIndustryId.compareTo(other.owningIndustryId);
    }
}
