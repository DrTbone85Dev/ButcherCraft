package com.butchercraft.packaging.definition;

import com.butchercraft.engine.EngineId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable construction boundary for an immutable packaging registry.
 */
public final class PackagingRegistryBuilder {
    private final Map<EngineId, PackagingDefinition> definitions = new LinkedHashMap<>();

    public PackagingRegistryBuilder register(PackagingDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate packaging id: " + definition.id().value());
        }
        definitions.put(definition.id(), definition);
        return this;
    }

    public PackagingRegistry build() {
        return new PackagingRegistry(definitions);
    }
}
