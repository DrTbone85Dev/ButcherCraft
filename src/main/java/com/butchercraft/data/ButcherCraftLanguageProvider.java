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
        add("commands.butchercraft.diagnostic", "ButcherCraft Diagnostic");
    }
}
