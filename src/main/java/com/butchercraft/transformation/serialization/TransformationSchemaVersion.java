package com.butchercraft.transformation.serialization;

import com.butchercraft.transformation.TransformationDefinition;

/**
 * Stable external schema version for serialized transformation definitions.
 */
public record TransformationSchemaVersion(int value) {
    public static final TransformationSchemaVersion CURRENT =
            new TransformationSchemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION);

    public TransformationSchemaVersion {
        if (value <= 0) {
            throw new IllegalArgumentException("Transformation schema version must be positive");
        }
    }
}
