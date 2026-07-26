package com.butchercraft.registration;

import com.butchercraft.ButcherCraft;
import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.QuantityUnit;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.component.ProductStackPackagingData;
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

    public static final DeferredItem<Item> FOAM_TRAY = ITEMS.registerSimpleItem(
            "foam_tray",
            new Item.Properties()
    );

    public static final DeferredItem<Item> PLASTIC_WRAP_ROLL = ITEMS.registerSimpleItem(
            "plastic_wrap_roll",
            new Item.Properties()
    );

    public static final DeferredItem<Item> VACUUM_BAG = ITEMS.registerSimpleItem(
            "vacuum_bag",
            new Item.Properties()
    );

    public static final DeferredItem<Item> BUTCHER_PAPER_ROLL = ITEMS.registerSimpleItem(
            "butcher_paper_roll",
            new Item.Properties()
    );

    public static final DeferredItem<Item> FREEZER_PAPER_ROLL = ITEMS.registerSimpleItem(
            "freezer_paper_roll",
            new Item.Properties()
    );

    public static final DeferredItem<Item> RETAIL_LABEL_ROLL = ITEMS.registerSimpleItem(
            "retail_label_roll",
            new Item.Properties()
    );

    /**
     * Promoted Beef Trim gameplay item. The original registry id is retained for save compatibility.
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

    public static final DeferredItem<ProductTestItem> BEEF_TRIM = BEEF_TRIM_TEST;

    /**
     * Promoted Ground Beef gameplay item. The original registry id is retained for save compatibility.
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

    public static final DeferredItem<ProductTestItem> GROUND_BEEF = GROUND_BEEF_TEST;

    public static final DeferredItem<ProductTestItem> BEEF_PATTIES = ITEMS.register(
            "beef_patties",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:beef_patties"),
                    ProductCategory.BEEF,
                    ProcessingState.fromId(EngineId.of("butchercraft:patties")),
                    900,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Development-only product fixture used to verify Packaging Table output.
     */
    public static final DeferredItem<ProductTestItem> RETAIL_GROUND_BEEF_TEST = ITEMS.register(
            "retail_ground_beef_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:retail_ground_beef"),
                    ProductCategory.BEEF,
                    ProcessingState.fromId(EngineId.of("butchercraft:retail_packaged")),
                    900,
                    QuantityUnit.GRAM,
                    700
            ).withPackaging(new ProductStackPackagingData(
                    "butchercraft:retail_package",
                    "tray_wrap",
                    "butchercraft:ground_beef"
            )))
    );

    /**
     * Promoted Pork Trim gameplay item. The original registry id is retained for save compatibility.
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

    public static final DeferredItem<ProductTestItem> PORK_TRIM = PORK_TRIM_TEST;

    /**
     * Promoted Ground Pork gameplay item. The original registry id is retained for save compatibility.
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

    public static final DeferredItem<ProductTestItem> GROUND_PORK = GROUND_PORK_TEST;

    /**
     * Promoted Chicken Trim gameplay item.
     */
    public static final DeferredItem<ProductTestItem> CHICKEN_TRIM = ITEMS.register(
            "chicken_trim",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:chicken_trim"),
                    ProductCategory.fromId(EngineId.of("butchercraft:chicken")),
                    ProcessingState.RAW,
                    1_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Promoted Ground Chicken gameplay item.
     */
    public static final DeferredItem<ProductTestItem> GROUND_CHICKEN = ITEMS.register(
            "ground_chicken",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:ground_chicken"),
                    ProductCategory.fromId(EngineId.of("butchercraft:chicken")),
                    ProcessingState.PREPARED,
                    900,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Promoted Buffalo Trim gameplay item. The original bison registry id is retained for save compatibility.
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
     * Promoted Ground Buffalo gameplay item. The original bison registry id is retained for save compatibility.
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

    public static final DeferredItem<ProductTestItem> BUFFALO_TRIM = BISON_TRIM_TEST;
    public static final DeferredItem<ProductTestItem> GROUND_BUFFALO = GROUND_BISON_TEST;

    /**
     * Promoted Lamb Trim gameplay item.
     */
    public static final DeferredItem<ProductTestItem> LAMB_TRIM = ITEMS.register(
            "lamb_trim",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:lamb_trim"),
                    ProductCategory.fromId(EngineId.of("butchercraft:lamb")),
                    ProcessingState.RAW,
                    1_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Promoted Ground Lamb gameplay item.
     */
    public static final DeferredItem<ProductTestItem> GROUND_LAMB = ITEMS.register(
            "ground_lamb",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:ground_lamb"),
                    ProductCategory.fromId(EngineId.of("butchercraft:lamb")),
                    ProcessingState.PREPARED,
                    900,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Promoted Venison Trim gameplay item.
     */
    public static final DeferredItem<ProductTestItem> VENISON_TRIM = ITEMS.register(
            "venison_trim",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:venison_trim"),
                    ProductCategory.fromId(EngineId.of("butchercraft:venison")),
                    ProcessingState.RAW,
                    1_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    /**
     * Promoted Ground Venison gameplay item.
     */
    public static final DeferredItem<ProductTestItem> GROUND_VENISON = ITEMS.register(
            "ground_venison",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:ground_venison"),
                    ProductCategory.fromId(EngineId.of("butchercraft:venison")),
                    ProcessingState.PREPARED,
                    900,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    public static final DeferredItem<ProductTestItem> BEEF_FOREQUARTER_TEST = ITEMS.register(
            "beef_forequarter_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:beef_forequarter"),
                    ProductCategory.BEEF,
                    ProcessingState.fromId(EngineId.of("butchercraft:forequarter")),
                    100_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    public static final DeferredItem<ProductTestItem> BEEF_CHUCK_TEST = ITEMS.register(
            "beef_chuck_test",
            () -> beefOutputFixture("butchercraft:beef_chuck", "butchercraft:primal", 30_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_RIB_TEST = ITEMS.register(
            "beef_rib_test",
            () -> beefOutputFixture("butchercraft:beef_rib", "butchercraft:primal", 10_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_PACKER_BRISKET_TEST = ITEMS.register(
            "beef_packer_brisket_test",
            () -> beefOutputFixture("butchercraft:beef_packer_brisket", "butchercraft:primal", 10_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_PLATE_TEST = ITEMS.register(
            "beef_plate_test",
            () -> beefOutputFixture("butchercraft:beef_plate", "butchercraft:primal", 10_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_SHANK_TEST = ITEMS.register(
            "beef_shank_test",
            () -> beefOutputFixture("butchercraft:beef_shank", "butchercraft:primal", 5_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_FAT_TEST = ITEMS.register(
            "beef_fat_test",
            () -> beefOutputFixture("butchercraft:beef_fat", "butchercraft:fat", 5_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_BONE_TEST = ITEMS.register(
            "beef_bone_test",
            () -> beefOutputFixture("butchercraft:beef_bone", "butchercraft:bone", 10_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_HINDQUARTER_TEST = ITEMS.register(
            "beef_hindquarter_test",
            () -> new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                    EngineId.of("butchercraft:beef_hindquarter"),
                    ProductCategory.BEEF,
                    ProcessingState.fromId(EngineId.of("butchercraft:hindquarter")),
                    100_000,
                    QuantityUnit.GRAM,
                    700
            ))
    );

    public static final DeferredItem<ProductTestItem> BEEF_ROUND_TEST = ITEMS.register(
            "beef_round_test",
            () -> beefOutputFixture("butchercraft:beef_round", "butchercraft:primal", 30_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_SIRLOIN_TEST = ITEMS.register(
            "beef_sirloin_test",
            () -> beefOutputFixture("butchercraft:beef_sirloin", "butchercraft:primal", 15_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_SHORT_LOIN_TEST = ITEMS.register(
            "beef_short_loin_test",
            () -> beefOutputFixture("butchercraft:beef_short_loin", "butchercraft:primal", 15_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_FLANK_TEST = ITEMS.register(
            "beef_flank_test",
            () -> beefOutputFixture("butchercraft:beef_flank", "butchercraft:primal", 7_500)
    );

    public static final DeferredItem<ProductTestItem> T_BONE_STEAK_TEST = ITEMS.register(
            "t_bone_steak_test",
            () -> beefOutputFixture("butchercraft:t_bone_steak", "butchercraft:steak", 4_000)
    );

    public static final DeferredItem<ProductTestItem> PORTERHOUSE_STEAK_TEST = ITEMS.register(
            "porterhouse_steak_test",
            () -> beefOutputFixture("butchercraft:porterhouse_steak", "butchercraft:steak", 3_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_STRIP_LOIN_TEST = ITEMS.register(
            "beef_strip_loin_test",
            () -> beefOutputFixture("butchercraft:beef_strip_loin", "butchercraft:subprimal", 3_000)
    );

    public static final DeferredItem<ProductTestItem> BEEF_TENDERLOIN_TEST = ITEMS.register(
            "beef_tenderloin_test",
            () -> beefOutputFixture("butchercraft:beef_tenderloin", "butchercraft:subprimal", 2_000)
    );

    public static final DeferredItem<ProductTestItem> TOP_ROUND_TEST = ITEMS.register(
            "top_round_test",
            () -> beefOutputFixture("butchercraft:top_round", "butchercraft:subprimal", 7_500)
    );

    public static final DeferredItem<ProductTestItem> BOTTOM_ROUND_TEST = ITEMS.register(
            "bottom_round_test",
            () -> beefOutputFixture("butchercraft:bottom_round", "butchercraft:subprimal", 6_500)
    );

    public static final DeferredItem<ProductTestItem> EYE_OF_ROUND_TEST = ITEMS.register(
            "eye_of_round_test",
            () -> beefOutputFixture("butchercraft:eye_of_round", "butchercraft:subprimal", 3_500)
    );

    public static final DeferredItem<ProductTestItem> SIRLOIN_TIP_TEST = ITEMS.register(
            "sirloin_tip_test",
            () -> beefOutputFixture("butchercraft:sirloin_tip", "butchercraft:subprimal", 5_000)
    );

    public static final DeferredItem<ProductTestItem> TOP_SIRLOIN_TEST = ITEMS.register(
            "top_sirloin_test",
            () -> beefOutputFixture("butchercraft:top_sirloin", "butchercraft:subprimal", 5_000)
    );

    public static final DeferredItem<ProductTestItem> SIRLOIN_STEAK_TEST = ITEMS.register(
            "sirloin_steak_test",
            () -> beefOutputFixture("butchercraft:sirloin_steak", "butchercraft:steak", 3_500)
    );

    public static final DeferredItem<ProductTestItem> TRI_TIP_TEST = ITEMS.register(
            "tri_tip_test",
            () -> beefOutputFixture("butchercraft:tri_tip", "butchercraft:subprimal", 2_000)
    );

    /**
     * Development-only workstation block item used to prove the reusable processing workstation framework.
     */
    public static final DeferredItem<BlockItem> DEVELOPMENT_PROCESSING_WORKSTATION =
            ITEMS.registerSimpleBlockItem(ModBlocks.DEVELOPMENT_PROCESSING_WORKSTATION, new Item.Properties());

    public static final DeferredItem<BlockItem> GRINDER =
            ITEMS.registerSimpleBlockItem(ModBlocks.GRINDER, new Item.Properties());

    public static final DeferredItem<BlockItem> PATTY_FORMER =
            ITEMS.registerSimpleBlockItem(ModBlocks.PATTY_FORMER, new Item.Properties());

    public static final DeferredItem<BlockItem> BANDSAW =
            ITEMS.registerSimpleBlockItem(ModBlocks.BANDSAW, new Item.Properties());

    public static final DeferredItem<BlockItem> PACKAGING_TABLE =
            ITEMS.registerSimpleBlockItem(ModBlocks.PACKAGING_TABLE, new Item.Properties());

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }

    private static ProductTestItem beefOutputFixture(String productId, String stateId, long grams) {
        return new ProductTestItem(new Item.Properties(), ProductStackData.fromEngineValues(
                EngineId.of(productId),
                ProductCategory.BEEF,
                ProcessingState.fromId(EngineId.of(stateId)),
                grams,
                QuantityUnit.GRAM,
                695
        ));
    }
}
