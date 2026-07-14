package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import com.butchercraft.registration.ModItems;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

final class ButcherCraftItemModelProvider extends ItemModelProvider {
    ButcherCraftItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ButcherCraft.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.DEVELOPMENT_TEST_ITEM.get());
    }
}
