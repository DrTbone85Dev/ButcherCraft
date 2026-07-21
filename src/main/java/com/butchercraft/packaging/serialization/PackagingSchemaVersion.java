package com.butchercraft.packaging.serialization;

import com.butchercraft.packaging.definition.PackagingDefinition;

/**
 * Stable packaging-definition schema version used by serialized content.
 */
public record PackagingSchemaVersion(int value) {
    public static final PackagingSchemaVersion CURRENT = new PackagingSchemaVersion(PackagingDefinition.CURRENT_SCHEMA_VERSION);

    public PackagingSchemaVersion {
        if (value <= 0) {
            throw new IllegalArgumentException("Packaging schema version must be positive");
        }
    }
}
