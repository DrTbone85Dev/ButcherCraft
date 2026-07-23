package com.butchercraft.world.production;

import java.util.Objects;

public record ProductionLineId(String value) implements Comparable<ProductionLineId> {
    public ProductionLineId {
        value = ProductionValidation.requireId(value, "Production line id");
    }

    public static ProductionLineId of(String value) {
        return new ProductionLineId(value);
    }

    @Override
    public int compareTo(ProductionLineId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
