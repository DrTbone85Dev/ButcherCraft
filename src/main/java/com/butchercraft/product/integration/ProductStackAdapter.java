package com.butchercraft.product.integration;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.item.ProductDataCarrier;
import com.butchercraft.registration.ModDataComponents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Focused adapter between engine products and ItemStack product data components.
 *
 * <p>The adapter is the Minecraft boundary: engine classes stay independent, while this class
 * reads and writes the registered component. Reading never mutates a stack. Writing and removing
 * affect only the product data component and reject non-product-carrier items.</p>
 */
public final class ProductStackAdapter {
    private ProductStackAdapter() {
    }

    public static ProductDataResult<ProductStackData> fromProduct(Product product) {
        Objects.requireNonNull(product, "product");
        try {
            return ProductDataResult.success(ProductStackData.fromEngineValues(
                    product.typeId(),
                    product.sourceCategory(),
                    product.processingState(),
                    product.quantity().amount(),
                    product.quantity().unit(),
                    product.quality().score()
            ));
        } catch (RuntimeException exception) {
            return ProductDataResult.failure("invalid_engine_product", exception.getMessage());
        }
    }

    public static ProductDataResult<Product> toProduct(ProductStackData data) {
        Objects.requireNonNull(data, "data");
        try {
            QuantityUnit unit = QuantityUnit.fromId(data.quantityUnitId());
            return ProductDataResult.success(new Product(
                    EngineId.of(data.productTypeId()),
                    ProductCategory.fromId(EngineId.of(data.sourceCategoryId())),
                    ProcessingState.fromId(EngineId.of(data.processingStateId())),
                    new ProductQuantity(data.quantityValue(), unit),
                    ProductQuality.ofScore(data.qualityScore())
            ));
        } catch (RuntimeException exception) {
            return ProductDataResult.failure("invalid_product_data", exception.getMessage());
        }
    }

    public static ProductDataResult<ProductStackData> readProductData(ItemStack stack) {
        return readProductData(stack, ModDataComponents.PRODUCT_DATA.get());
    }

    public static ProductDataResult<ProductStackData> readProductData(
            ItemStack stack,
            DataComponentType<ProductStackData> componentType
    ) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(componentType, "componentType");
        if (!(stack.getItem() instanceof ProductDataCarrier)) {
            return ProductDataResult.failure("not_product_item", "Stack item is not intended to carry ButcherCraft product data");
        }
        ProductStackData data = stack.get(componentType);
        if (data == null) {
            return ProductDataResult.failure("missing_product_data", "Stack does not contain ButcherCraft product data");
        }
        return ProductDataResult.success(data);
    }

    public static ProductDataResult<Product> readProduct(ItemStack stack) {
        ProductDataResult<ProductStackData> data = readProductData(stack);
        if (!data.succeeded()) {
            return ProductDataResult.failure(
                    data.failureReason().orElseThrow().code(),
                    data.failureReason().orElseThrow().message()
            );
        }
        return toProduct(data.orThrow());
    }

    public static ProductDataResult<ProductStackData> writeProduct(ItemStack stack, Product product) {
        ProductDataResult<ProductStackData> data = fromProduct(product);
        if (!data.succeeded()) {
            return data;
        }
        return writeProductData(stack, data.orThrow());
    }

    public static ProductDataResult<ProductStackData> writeProductData(ItemStack stack, ProductStackData data) {
        return writeProductData(stack, data, ModDataComponents.PRODUCT_DATA.get());
    }

    public static ProductDataResult<ProductStackData> writeProductData(
            ItemStack stack,
            ProductStackData data,
            DataComponentType<ProductStackData> componentType
    ) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(componentType, "componentType");
        if (!(stack.getItem() instanceof ProductDataCarrier)) {
            return ProductDataResult.failure("not_product_item", "Stack item is not intended to carry ButcherCraft product data");
        }
        stack.set(componentType, data);
        return ProductDataResult.success(data);
    }

    public static ProductDataResult<ProductStackData> removeProductData(ItemStack stack) {
        return removeProductData(stack, ModDataComponents.PRODUCT_DATA.get());
    }

    public static ProductDataResult<ProductStackData> removeProductData(
            ItemStack stack,
            DataComponentType<ProductStackData> componentType
    ) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(componentType, "componentType");
        if (!(stack.getItem() instanceof ProductDataCarrier)) {
            return ProductDataResult.failure("not_product_item", "Stack item is not intended to carry ButcherCraft product data");
        }
        ProductStackData removed = stack.remove(componentType);
        if (removed == null) {
            return ProductDataResult.failure("missing_product_data", "Stack did not contain ButcherCraft product data");
        }
        return ProductDataResult.success(removed);
    }
}
