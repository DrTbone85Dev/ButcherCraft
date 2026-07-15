package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.item.ProductTestItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ButcherCraft.MOD_ID);

    /**
     * Harmless development-only item used to verify registration, assets, creative-tab wiring, and diagnostics.
     */
    public static final DeferredItem<Item> DEVELOPMENT_TEST_ITEM = ITEMS.registerSimpleItem(
            "development_test_item",
            new Item.Properties()
    );

    /**
     * Development-only product fixture used to verify ItemStack product data integration.
     */
    public static final DeferredItem<ProductTestItem> BEEF_TRIM_TEST = ITEMS.register(
            "beef_trim_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:beef_trim"),
                    ProductCategory.BEEF,
                    ProcessingState.RAW,
                    1_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Development-only product fixture used to verify ItemStack product data integration.
     */
    public static final DeferredItem<ProductTestItem> GROUND_BEEF_TEST = ITEMS.register(
            "ground_beef_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:ground_beef"),
                    ProductCategory.BEEF,
                    ProcessingState.PREPARED,
                    900,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
