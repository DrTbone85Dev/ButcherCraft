package com.butchercraft.world.production;

import java.util.Objects;

public record ProductionRunId(String value) implements Comparable<ProductionRunId> {
    public ProductionRunId {
        value = ProductionValidation.requireId(value, "Production run id");
    }

    public static ProductionRunId of(String value) {
        return new ProductionRunId(value);
    }

    public static ProductionRunId forPlan(ProductionPlanId planId) {
        return of(Objects.requireNonNull(planId, "planId").value() + "/run");
    }

    @Override
    public int compareTo(ProductionRunId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
