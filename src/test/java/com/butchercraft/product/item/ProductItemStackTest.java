package com.butchercraft.product.item;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModDataComponents;
import com.butchercraft.registration.ModItems;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductItemStackTest {
    private static final ProductStackData DEFAULT_DATA = ProductStackData.fromEngineValues(
            EngineId.of("butchercraft:beef_trim"),
            ProductCategory.BEEF,
            ProcessingState.RAW,
            1_000,
            QuantityUnit.GRAM,
            700
    );
    private static final ProductStackData DIFFERENT_DATA = ProductStackData.fromEngineValues(
            EngineId.of("butchercraft:beef_trim"),
            ProductCategory.BEEF,
            ProcessingState.RAW,
            500,
            QuantityUnit.GRAM,
            700
    );
    @Test
    void defaultProductTestItemStackHasProductComponent() {
        ItemStack stack = productStack();

        assertEquals(DEFAULT_DATA, stack.get(ModDataComponents.PRODUCT_DATA.get()));
    }

    @Test
    void productBearingStacksUseSingleStackLimit() {
        assertEquals(1, productStack().getMaxStackSize());
    }

    @Test
    void itemStackCopyRetainsProductData() {
        ItemStack original = productStack();
        ItemStack copy = original.copy();

        assertEquals(DEFAULT_DATA, copy.get(ModDataComponents.PRODUCT_DATA.get()));
        assertTrue(ItemStack.isSameItemSameComponents(original, copy));
    }

    @Test
    void separateStacksDoNotShareMutableState() {
        ItemStack first = productStack();
        ItemStack second = productStack();

        ProductStackAdapter.writeProductData(first, DIFFERENT_DATA);

        assertEquals(DIFFERENT_DATA, first.get(ModDataComponents.PRODUCT_DATA.get()));
        assertEquals(DEFAULT_DATA, second.get(ModDataComponents.PRODUCT_DATA.get()));
    }

    @Test
    void differentProductDataDoesNotMergeAsSameComponents() {
        ItemStack first = productStack();
        ItemStack second = productStack();
        ProductStackAdapter.writeProductData(second, DIFFERENT_DATA);

        assertFalse(ItemStack.isSameItemSameComponents(first, second));
    }

    private static ItemStack productStack() {
        return ModItems.BEEF_TRIM_TEST.get().getDefaultInstance();
    }
}
