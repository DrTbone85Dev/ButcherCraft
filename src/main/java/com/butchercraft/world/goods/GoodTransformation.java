package com.butchercraft.world.goods;

import java.util.Objects;

public record GoodTransformation(
        GoodId inputGoodId,
        GoodId outputGoodId,
        GoodYieldRatio yieldRatio,
        IndustryId owningIndustryId,
        int schemaVersion
) {
    public GoodTransformation(
            GoodId inputGoodId,
            GoodId outputGoodId,
            GoodYieldRatio yieldRatio,
            IndustryId owningIndustryId
    ) {
        this(inputGoodId, outputGoodId, yieldRatio, owningIndustryId, GoodSchema.CURRENT_VERSION);
    }

    public GoodTransformation {
        inputGoodId = Objects.requireNonNull(inputGoodId, "inputGoodId");
        outputGoodId = Objects.requireNonNull(outputGoodId, "outputGoodId");
        yieldRatio = Objects.requireNonNull(yieldRatio, "yieldRatio");
        owningIndustryId = Objects.requireNonNull(owningIndustryId, "owningIndustryId");
        if (schemaVersion != GoodSchema.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported good transformation schema version: " + schemaVersion);
        }
    }
}
