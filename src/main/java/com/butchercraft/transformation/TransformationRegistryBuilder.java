package com.butchercraft.transformation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable construction boundary for an immutable transformation registry.
 */
public final class TransformationRegistryBuilder {
    private final Map<TransformationId, TransformationDefinition> definitions = new LinkedHashMap<>();

    public TransformationRegistryBuilder register(TransformationDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (definitions.containsKey(definition.id())) {
            throw new IllegalArgumentException("Duplicate transformation id: " + definition.id().value());
        }
        definitions.put(definition.id(), definition);
        return this;
    }

    public TransformationRegistry build() {
        return new TransformationRegistry(definitions);
    }
}
