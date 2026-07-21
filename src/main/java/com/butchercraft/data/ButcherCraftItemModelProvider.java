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
    private static final ResourceLocation RETAIL_GROUND_BEEF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/packaging/retail_ground_beef");

    ButcherCraftItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ButcherCraft.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.DEVELOPMENT_TEST_ITEM.get());
        placeholderProductItem(ModItems.BEEF_TRIM_TEST.get());
        placeholderProductItem(ModItems.GROUND_BEEF_TEST.get());
        generatedItem(ModItems.RETAIL_GROUND_BEEF_TEST.get(), RETAIL_GROUND_BEEF_TEXTURE);
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
        placeholderProductItem(ModItems.BEEF_HINDQUARTER_TEST.get());
        placeholderProductItem(ModItems.BEEF_ROUND_TEST.get());
        placeholderProductItem(ModItems.BEEF_SIRLOIN_TEST.get());
        placeholderProductItem(ModItems.BEEF_SHORT_LOIN_TEST.get());
        placeholderProductItem(ModItems.BEEF_FLANK_TEST.get());
        placeholderProductItem(ModItems.T_BONE_STEAK_TEST.get());
        placeholderProductItem(ModItems.PORTERHOUSE_STEAK_TEST.get());
        placeholderProductItem(ModItems.BEEF_STRIP_LOIN_TEST.get());
        placeholderProductItem(ModItems.BEEF_TENDERLOIN_TEST.get());
        placeholderProductItem(ModItems.TOP_ROUND_TEST.get());
        placeholderProductItem(ModItems.BOTTOM_ROUND_TEST.get());
        placeholderProductItem(ModItems.EYE_OF_ROUND_TEST.get());
        placeholderProductItem(ModItems.SIRLOIN_TIP_TEST.get());
        placeholderProductItem(ModItems.TOP_SIRLOIN_TEST.get());
        placeholderProductItem(ModItems.SIRLOIN_STEAK_TEST.get());
        placeholderProductItem(ModItems.TRI_TIP_TEST.get());
        generatedItem(ModItems.FOAM_TRAY.get(), packagingTexture("foam_tray"));
        generatedItem(ModItems.PLASTIC_WRAP_ROLL.get(), packagingTexture("plastic_wrap_roll"));
        generatedItem(ModItems.VACUUM_BAG.get(), packagingTexture("vacuum_bag"));
        generatedItem(ModItems.BUTCHER_PAPER_ROLL.get(), packagingTexture("butcher_paper_roll"));
        generatedItem(ModItems.FREEZER_PAPER_ROLL.get(), packagingTexture("freezer_paper_roll"));
        generatedItem(ModItems.RETAIL_LABEL_ROLL.get(), packagingTexture("retail_label_roll"));
    }

    private void placeholderProductItem(Item item) {
        generatedItem(item, DEVELOPMENT_PLACEHOLDER_TEXTURE);
    }

    private void generatedItem(Item item, ResourceLocation texture) {
        ResourceLocation itemId = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(item));
        getBuilder(itemId.toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", texture);
    }

    private static ResourceLocation packagingTexture(String path) {
        return ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/packaging/" + path);
    }
}
