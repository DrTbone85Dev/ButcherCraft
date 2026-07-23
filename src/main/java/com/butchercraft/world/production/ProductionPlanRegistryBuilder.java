package com.butchercraft.world.production;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ProductionPlanRegistryBuilder {
    private final Map<ProductionPlanId, ProductionPlanDefinition> definitions = new LinkedHashMap<>();

    public ProductionPlanRegistryBuilder register(ProductionPlanDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate production plan id: " + definition.id().value());
        }
        return this;
    }

    public ProductionPlanRegistry build() {
        return ProductionPlanRegistry.of(definitions.values());
    }
}
