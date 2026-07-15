package com.butchercraft.product.integration;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.Product;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quality.ProductQuality;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductStackAdapterTest {
    private static final Product PRODUCT = new Product(
            EngineId.of("butchercraft:beef_trim"),
            ProductCategory.BEEF,
            ProcessingState.RAW,
            ProductQuantity.grams(1_234),
            ProductQuality.ofScore(701)
    );
    private static final ProductStackData DATA = ProductStackData.fromEngineValues(
            EngineId.of("butchercraft:beef_trim"),
            ProductCategory.BEEF,
            ProcessingState.RAW,
            1_234,
            QuantityUnit.GRAM,
            701
    );
    @Test
    void convertsEngineProductToComponentData() {
        ProductDataResult<ProductStackData> result = ProductStackAdapter.fromProduct(PRODUCT);

        assertTrue(result.succeeded());
        assertEquals(DATA, result.orThrow());
    }

    @Test
    void convertsComponentDataToEngineProduct() {
        ProductDataResult<Product> result = ProductStackAdapter.toProduct(DATA);

        assertTrue(result.succeeded());
        assertEquals(PRODUCT, result.orThrow());
    }

    @Test
    void roundTripPreservesQuantityAndQualityExactly() {
        ProductStackData componentData = ProductStackAdapter.fromProduct(PRODUCT).orThrow();
        Product roundTripped = ProductStackAdapter.toProduct(componentData).orThrow();

        assertEquals(PRODUCT.quantity(), roundTripped.quantity());
        assertEquals(PRODUCT.quality(), roundTripped.quality());
        assertEquals(PRODUCT, roundTripped);
    }

    @Test
    void missingProductComponentReturnsExplicitFailure() {
        ItemStack stack = productStack();
        ProductStackAdapter.removeProductData(stack);

        ProductDataResult<ProductStackData> result = ProductStackAdapter.readProductData(stack);

        assertFalse(result.succeeded());
        assertEquals("missing_product_data", result.failureReason().orElseThrow().code());
    }

    @Test
    void nonProductItemReturnsExplicitFailure() {
        ItemStack stack = new ItemStack(Items.STICK);

        ProductDataResult<ProductStackData> result = ProductStackAdapter.readProductData(stack);

        assertFalse(result.succeeded());
        assertEquals("not_product_item", result.failureReason().orElseThrow().code());
    }

    @Test
    void readDoesNotMutateStack() {
        ItemStack stack = productStack();
        var before = stack.getComponentsPatch();

        ProductDataResult<ProductStackData> result = ProductStackAdapter.readProductData(stack);

        assertTrue(result.succeeded());
        assertEquals(before, stack.getComponentsPatch());
    }

    @Test
    void writeChangesOnlyProductComponent() {
        ItemStack stack = productStack();
        int countBefore = stack.getCount();

        ProductDataResult<ProductStackData> result = ProductStackAdapter.writeProductData(stack, DATA);

        assertTrue(result.succeeded());
        assertEquals(DATA, stack.get(ModDataComponents.PRODUCT_DATA.get()));
        assertEquals(countBefore, stack.getCount());
    }

    @Test
    void removeProductDataRemovesOnlyProductComponent() {
        ItemStack stack = productStack();
        int countBefore = stack.getCount();
        ProductStackData initialData = stack.get(ModDataComponents.PRODUCT_DATA.get());

        ProductDataResult<ProductStackData> result = ProductStackAdapter.removeProductData(stack);

        assertTrue(result.succeeded());
        assertEquals(initialData, result.orThrow());
        assertEquals(null, stack.get(ModDataComponents.PRODUCT_DATA.get()));
        assertEquals(countBefore, stack.getCount());
    }

    private static ItemStack productStack() {
        return ModItems.BEEF_TRIM_TEST.get().getDefaultInstance();
    }
}
