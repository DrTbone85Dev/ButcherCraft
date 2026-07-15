package com.butchercraft.product.item;

import com.butchercraft.product.component.ProductStackData;

/**
 * Marker contract for development items intended to carry ButcherCraft product data.
 *
 * <p>This keeps adapter writes scoped to product-bearing items and prevents accidental component
 * writes to unrelated stacks.</p>
 */
public interface ProductDataCarrier {
    ProductStackData defaultProductData();
}
