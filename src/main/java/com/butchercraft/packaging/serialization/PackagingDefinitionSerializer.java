package com.butchercraft.packaging.serialization;

import com.butchercraft.packaging.definition.PackagingDefinition;

/**
 * Serialization contract for packaging definitions.
 */
public interface PackagingDefinitionSerializer<T> {
    T serialize(PackagingDefinition definition);
}
