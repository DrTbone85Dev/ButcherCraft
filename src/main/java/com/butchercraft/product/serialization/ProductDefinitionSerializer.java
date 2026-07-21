package com.butchercraft.product.serialization;

import com.butchercraft.product.definition.ProductDefinition;

/**
 * Serialization boundary for product definitions.
 *
 * @param <T> serialized representation
 */
public interface ProductDefinitionSerializer<T> {
    T serialize(ProductDefinition definition);
}
