package com.butchercraft.world.production;

import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodYieldRatio;
import com.butchercraft.world.goods.UnitOfMeasure;

import java.util.Objects;
import java.util.Optional;

public record ProductionOutputDefinition(
        ProductionLineId id,
        GoodId goodId,
        GoodQuantity baseQuantityPerBatch,
        UnitOfMeasure unit,
        ProductionOutputRole role,
        GoodYieldRatio yieldRatio,
        Optional<ProductionTransformationReference> transformationReference,
        ProductionInventoryConstraint destinationConstraint,
        ProductionLineMetadata metadata
) {
    public ProductionOutputDefinition {
        id = Objects.requireNonNull(id, "id");
        goodId = Objects.requireNonNull(goodId, "goodId");
        baseQuantityPerBatch = Objects.requireNonNull(baseQuantityPerBatch, "baseQuantityPerBatch")
                .requirePositive("Production output quantity");
        unit = Objects.requireNonNull(unit, "unit");
        role = Objects.requireNonNull(role, "role");
        yieldRatio = Objects.requireNonNull(yieldRatio, "yieldRatio");
        transformationReference = Objects.requireNonNull(transformationReference, "transformationReference");
        destinationConstraint = Objects.requireNonNull(destinationConstraint, "destinationConstraint");
        metadata = Objects.requireNonNull(metadata, "metadata");
    }
}
