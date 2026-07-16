package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import com.butchercraft.registration.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.util.Objects;

final class ButcherCraftItemModelProvider extends ItemModelProvider {
    private static final ResourceLocation DEVELOPMENT_PLACEHOLDER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/development_test_item");

    ButcherCraftItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ButcherCraft.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.DEVELOPMENT_TEST_ITEM.get());
        placeholderProductItem(ModItems.BEEF_TRIM_TEST.get());
        placeholderProductItem(ModItems.GROUND_BEEF_TEST.get());
        placeholderProductItem(ModItems.PORK_TRIM_TEST.get());
        placeholderProductItem(ModItems.GROUND_PORK_TEST.get());
        placeholderProductItem(ModItems.BISON_TRIM_TEST.get());
        placeholderProductItem(ModItems.GROUND_BISON_TEST.get());
        placeholderProductItem(ModItems.BEEF_FOREQUARTER_TEST.get());
        placeholderProductItem(ModItems.BEEF_CHUCK_TEST.get());
        placeholderProductItem(ModItems.BEEF_RIB_TEST.get());
        placeholderProductItem(ModItems.BEEF_PACKER_BRISKET_TEST.get());
        placeholderProductItem(ModItems.BEEF_PLATE_TEST.get());
        placeholderProductItem(ModItems.BEEF_SHANK_TEST.get());
        placeholderProductItem(ModItems.BEEF_FAT_TEST.get());
        placeholderProductItem(ModItems.BEEF_BONE_TEST.get());
    }

    private void placeholderProductItem(Item item) {
        ResourceLocation itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item));
        getBuilder(itemId.toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", DEVELOPMENT_PLACEHOLDER_TEXTURE);
    }
}
