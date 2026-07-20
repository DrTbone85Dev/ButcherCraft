package com.butchercraft.transformation.serialization;

import com.butchercraft.transformation.TransformationDefinition;

/**
 * Serializes transformation definitions into an external representation.
 */
public interface TransformationDefinitionSerializer<T> {
    T serialize(TransformationDefinition definition);
}
