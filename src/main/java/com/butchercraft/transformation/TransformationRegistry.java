package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Immutable ordered registry of transformation definitions.
 */
public final class TransformationRegistry {
    private final Map<TransformationId, TransformationDefinition> definitionsById;

    TransformationRegistry(Map<TransformationId, TransformationDefinition> definitionsById) {
        Objects.requireNonNull(definitionsById, "definitionsById");
        this.definitionsById = Collections.unmodifiableMap(new LinkedHashMap<>(definitionsById));
    }

    public static TransformationRegistryBuilder builder() {
        return new TransformationRegistryBuilder();
    }

    public boolean contains(TransformationId id) {
        return definitionsById.containsKey(Objects.requireNonNull(id, "id"));
    }

    public Optional<TransformationDefinition> find(TransformationId id) {
        return Optional.ofNullable(definitionsById.get(Objects.requireNonNull(id, "id")));
    }

    public int size() {
        return definitionsById.size();
    }

    public Stream<TransformationDefinition> stream() {
        return definitionsById.values().stream();
    }

    public Stream<TransformationDefinition> findByCapability(EngineId workstationCapability) {
        Objects.requireNonNull(workstationCapability, "workstationCapability");
        return stream()
                .filter(definition -> definition.workstationCapability()
                        .filter(workstationCapability::equals)
                        .isPresent());
    }
}
