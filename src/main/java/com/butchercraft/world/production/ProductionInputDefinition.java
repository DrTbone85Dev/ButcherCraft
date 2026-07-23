package com.butchercraft.world.production;

import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.UnitOfMeasure;

import java.util.Objects;
import java.util.Optional;

public record ProductionInputDefinition(
        ProductionLineId id,
        GoodId goodId,
        GoodQuantity quantityPerBatch,
        UnitOfMeasure unit,
        ProductionInputRole role,
        ConsumptionPolicy consumptionPolicy,
        Optional<ProductionTransformationReference> transformationReference,
        ProductionInventoryConstraint sourceConstraint,
        ProductionLineMetadata metadata
) {
    public ProductionInputDefinition {
        id = Objects.requireNonNull(id, "id");
        goodId = Objects.requireNonNull(goodId, "goodId");
        quantityPerBatch = Objects.requireNonNull(quantityPerBatch, "quantityPerBatch")
                .requirePositive("Production input quantity");
        unit = Objects.requireNonNull(unit, "unit");
        role = Objects.requireNonNull(role, "role");
        consumptionPolicy = Objects.requireNonNull(consumptionPolicy, "consumptionPolicy");
        transformationReference = Objects.requireNonNull(transformationReference, "transformationReference");
        sourceConstraint = Objects.requireNonNull(sourceConstraint, "sourceConstraint");
        metadata = Objects.requireNonNull(metadata, "metadata");
    }
}
