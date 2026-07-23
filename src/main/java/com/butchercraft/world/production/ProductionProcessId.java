package com.butchercraft.world.production;

import java.util.Objects;

public record ProductionProcessId(String value) implements Comparable<ProductionProcessId> {
    public ProductionProcessId {
        value = ProductionValidation.requireId(value, "Production process id");
    }

    public static ProductionProcessId of(String value) {
        return new ProductionProcessId(value);
    }

    @Override
    public int compareTo(ProductionProcessId other) {
        return value.compareTo(Objects.requireNonNull(other, "other").value);
    }
}
