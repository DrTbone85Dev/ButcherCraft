package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.item.ProductTestItem;
import net.minecraft.world.item.BlockItem;
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

    /**
     * Development-only product fixture used to prove data-driven grinding beyond beef.
     */
    public static final DeferredItem<ProductTestItem> PORK_TRIM_TEST = ITEMS.register(
            "pork_trim_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:pork_trim"),
                    ProductCategory.fromId(EngineId.of("butchercraft:pork")),
                    ProcessingState.RAW,
                    1_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Development-only product fixture used to prove data-driven grinding beyond beef.
     */
    public static final DeferredItem<ProductTestItem> GROUND_PORK_TEST = ITEMS.register(
            "ground_pork_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:ground_pork"),
                    ProductCategory.fromId(EngineId.of("butchercraft:pork")),
                    ProcessingState.PREPARED,
                    900,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Development-only product fixture used to prove data-driven grinding beyond fixed engine source categories.
     */
    public static final DeferredItem<ProductTestItem> BISON_TRIM_TEST = ITEMS.register(
            "bison_trim_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:bison_trim"),
                    ProductCategory.fromId(EngineId.of("butchercraft:bison")),
                    ProcessingState.RAW,
                    1_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Development-only product fixture used to prove data-driven grinding beyond fixed engine source categories.
     */
    public static final DeferredItem<ProductTestItem> GROUND_BISON_TEST = ITEMS.register(
            "ground_bison_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:ground_bison"),
                    ProductCategory.fromId(EngineId.of("butchercraft:bison")),
                    ProcessingState.PREPARED,
                    900,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Development-only workstation block item used to prove the reusable processing workstation framework.
     */
    public static final DeferredItem<BlockItem> DEVELOPMENT_PROCESSING_WORKSTATION =
            ITEMS.registerSimpleBlockItem(ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION, new Item.Properties());

    public static final DeferredItem<BlockItem> GRINDER =
            ITEMS.registerSimpleBlockItem(ModBlocks.GRINDER, new Item.Properties());

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
