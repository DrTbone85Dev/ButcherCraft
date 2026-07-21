package com.butchercraft.product.serialization;

import com.butchercraft.product.definition.ProductDefinition;

/**
 * Deserialization boundary for product definitions.
 *
 * @param <T> serialized representation
 */
public interface ProductDefinitionDeserializer<T> {
    ProductDefinition deserialize(T serialized);
}
