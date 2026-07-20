package com.butchercraft.transformation.serialization;

/**
 * Future migration contract between serialized transformation schema versions.
 *
 * <p>No migrations are implemented in v0.6.5.</p>
 */
public interface TransformationDefinitionMigration {
    TransformationSchemaVersion sourceVersion();

    TransformationSchemaVersion targetVersion();

    SerializedTransformationDefinition migrate(SerializedTransformationDefinition definition);
}
