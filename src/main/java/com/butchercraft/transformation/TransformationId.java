package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;

import java.util.Objects;

/**
 * Stable identifier for a material transformation.
 */
public record TransformationId(EngineId id) {
    public TransformationId {
        Objects.requireNonNull(id, "id");
    }

    public static TransformationId of(String value) {
        return new TransformationId(EngineId.of(value));
    }

    public String value() {
        return id.value();
    }

    @Override
    public String toString() {
        return value();
    }
}
