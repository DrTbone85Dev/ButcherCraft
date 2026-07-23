package com.butchercraft.world.production;

import java.util.Objects;

public record ProductionPlanId(String value) implements Comparable<ProductionPlanId> {
    public ProductionPlanId {
        value = ProductionValidation.requireId(value, "Production plan id");
    }

    public static ProductionPlanId of(String value) {
        return new ProductionPlanId(value);
    }

    @Override
    public int compareTo(ProductionPlanId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
