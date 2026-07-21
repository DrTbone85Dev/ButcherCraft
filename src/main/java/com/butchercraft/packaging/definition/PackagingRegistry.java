package com.butchercraft.packaging.definition;

import com.butchercraft.engine.EngineId;
import com.butchercraft.product.definition.ProductDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Immutable ordered registry of retail packaging definitions.
 */
public final class PackagingRegistry {
    private final Map<EngineId, PackagingDefinition> definitionsById;

    PackagingRegistry(Map<EngineId, PackagingDefinition> definitionsById) {
        Objects.requireNonNull(definitionsById, "definitionsById");
        this.definitionsById = Collections.unmodifiableMap(new LinkedHashMap<>(definitionsById));
    }

    public static PackagingRegistryBuilder builder() {
        return new PackagingRegistryBuilder();
    }

    public boolean contains(EngineId id) {
        return definitionsById.containsKey(Objects.requireNonNull(id, "id"));
    }

    public Optional<PackagingDefinition> find(EngineId id) {
        return Optional.ofNullable(definitionsById.get(Objects.requireNonNull(id, "id")));
    }

    public int size() {
        return definitionsById.size();
    }

    public Stream<PackagingDefinition> stream() {
        return definitionsById.values().stream();
    }

    public Stream<PackagingDefinition> findByFormat(PackagingFormat format) {
        Objects.requireNonNull(format, "format");
        return stream().filter(definition -> definition.format() == format);
    }

    public Stream<PackagingDefinition> findCompatible(ProductDefinition product) {
        Objects.requireNonNull(product, "product");
        return stream().filter(definition -> definition.isCompatibleWith(product));
    }
}
