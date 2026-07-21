package com.butchercraft.packaging.serialization;

import com.butchercraft.packaging.definition.PackagingDefinition;

/**
 * Deserialization contract for packaging definitions.
 */
public interface PackagingDefinitionDeserializer<T> {
    PackagingDefinition deserialize(T serialized);
}
