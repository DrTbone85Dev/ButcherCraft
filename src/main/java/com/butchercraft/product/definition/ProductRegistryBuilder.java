package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable construction boundary for an immutable product registry.
 */
public final class ProductRegistryBuilder {
    private final Map<EngineId, ProductDefinition> definitions = new LinkedHashMap<>();

    public ProductRegistryBuilder register(ProductDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate product id: " + definition.id().value());
        }
        definitions.put(definition.id(), definition);
        return this;
    }

    public ProductRegistry build() {
        return new ProductRegistry(definitions);
    }
}
