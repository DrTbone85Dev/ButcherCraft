package com.butchercraft.transformation.serialization;

import com.butchercraft.transformation.TransformationDefinition;

/**
 * Deserializes transformation definitions from an external representation.
 */
public interface TransformationDefinitionDeserializer<T> {
    TransformationDefinition deserialize(T serialized);
}
