package com.butchercraft.product.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProductCategory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Immutable ordered registry of canonical product definitions.
 */
public final class ProductRegistry {
    private final Map<EngineId, ProductDefinition> definitionsById;

    ProductRegistry(Map<EngineId, ProductDefinition> definitionsById) {
        Objects.requireNonNull(definitionsById, "definitionsById");
        this.definitionsById = Collections.unmodifiableMap(new LinkedHashMap<>(definitionsById));
    }

    public static ProductRegistryBuilder builder() {
        return new ProductRegistryBuilder();
    }

    public boolean contains(EngineId id) {
        return definitionsById.containsKey(Objects.requireNonNull(id, "id"));
    }

    public Optional<ProductDefinition> find(EngineId id) {
        return Optional.ofNullable(definitionsById.get(Objects.requireNonNull(id, "id")));
    }

    public int size() {
        return definitionsById.size();
    }

    public Stream<ProductDefinition> stream() {
        return definitionsById.values().stream();
    }

    public Stream<ProductDefinition> findByCategory(ProductCategory category) {
        Objects.requireNonNull(category, "category");
        return stream().filter(definition -> definition.category().equals(category));
    }

    public Stream<ProductDefinition> findByTag(EngineId tag) {
        Objects.requireNonNull(tag, "tag");
        return stream().filter(definition -> definition.hasTag(tag));
    }
}
