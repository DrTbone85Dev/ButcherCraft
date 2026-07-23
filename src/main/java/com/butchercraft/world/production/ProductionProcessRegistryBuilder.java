package com.butchercraft.world.production;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ProductionProcessRegistryBuilder {
    private final Map<ProductionProcessId, ProductionProcessDefinition> definitions = new LinkedHashMap<>();

    public ProductionProcessRegistryBuilder register(ProductionProcessDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate production process id: " + definition.id().value());
        }
        return this;
    }

    public ProductionProcessRegistry build() {
        return ProductionProcessRegistry.of(definitions.values());
    }
}
