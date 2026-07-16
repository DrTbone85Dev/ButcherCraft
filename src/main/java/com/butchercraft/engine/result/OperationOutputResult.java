package com.butchercraft.engine.result;

import com.butchercraft.engine.product.Product;

import java.util.Objects;

/**
 * One immutable output in a prepared or committed operation result.
 */
public record OperationOutputResult(int outputIndex, Product product) {
    public OperationOutputResult {
        if (outputIndex < 0) {
            throw new IllegalArgumentException("Output index cannot be negative");
        }
        Objects.requireNonNull(product, "product");
    }
}
