package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import com.butchercraft.registration.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

final class ButcherCraftLanguageProvider extends LanguageProvider {
    ButcherCraftLanguageProvider(PackOutput output) {
        super(output, ButcherCraft.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.butchercraft", "ButcherCraft");
        add(ModItems.DEVELOPMENT_TEST_ITEM.get(), "Development Test Item");
        add(ModItems.BEEF_TRIM_TEST.get(), "Beef Trim Test Product");
        add(ModItems.GROUND_BEEF_TEST.get(), "Ground Beef Test Product");
        add("commands.butchercraft.diagnostic", "ButcherCraft Diagnostic");
        add("tooltip.butchercraft.product_data.product", "Product: %s");
        add("tooltip.butchercraft.product_data.source", "Source: %s");
        add("tooltip.butchercraft.product_data.state", "State: %s");
        add("tooltip.butchercraft.product_data.quantity", "Quantity: %s %s");
        add("tooltip.butchercraft.product_data.quality", "Quality: %s");
        add("tooltip.butchercraft.product_data.quality_score", "Quality score: %s");
        add("tooltip.butchercraft.product_data.missing", "Missing ButcherCraft product data");
        add("tooltip.butchercraft.product_data.invalid", "Invalid ButcherCraft product data");
    }
}
