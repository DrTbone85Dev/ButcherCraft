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
    private static final ResourceLocation BEEF_TRIM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/beef_trim");
    private static final ResourceLocation GROUND_BEEF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/ground_beef");
    private static final ResourceLocation BEEF_PATTIES_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/beef_patties");
    private static final ResourceLocation PORK_TRIM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/pork_trim");
    private static final ResourceLocation GROUND_PORK_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/ground_pork");
    private static final ResourceLocation CHICKEN_TRIM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/chicken_trim");
    private static final ResourceLocation GROUND_CHICKEN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/ground_chicken");
    private static final ResourceLocation BISON_TRIM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/bison_trim");
    private static final ResourceLocation GROUND_BISON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/ground_bison");
    private static final ResourceLocation LAMB_TRIM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/lamb_trim");
    private static final ResourceLocation GROUND_LAMB_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/ground_lamb");
    private static final ResourceLocation VENISON_TRIM_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/venison_trim");
    private static final ResourceLocation GROUND_VENISON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/product/ground_venison");
    private static final ResourceLocation RETAIL_GROUND_BEEF_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/packaging/retail_ground_beef");

    ButcherCraftItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ButcherCraft.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        basicItem(ModItems.DEVELOPMENT_TEST_ITEM.get());
        generatedItem(ModItems.BEEF_TRIM.get(), BEEF_TRIM_TEXTURE);
        generatedItem(ModItems.GROUND_BEEF.get(), GROUND_BEEF_TEXTURE);
        generatedItem(ModItems.BEEF_PATTIES.get(), BEEF_PATTIES_TEXTURE);
        generatedItem(ModItems.RETAIL_GROUND_BEEF_TEST.get(), RETAIL_GROUND_BEEF_TEXTURE);
        generatedItem(ModItems.PORK_TRIM.get(), PORK_TRIM_TEXTURE);
        generatedItem(ModItems.GROUND_PORK.get(), GROUND_PORK_TEXTURE);
        generatedItem(ModItems.CHICKEN_TRIM.get(), CHICKEN_TRIM_TEXTURE);
        generatedItem(ModItems.GROUND_CHICKEN.get(), GROUND_CHICKEN_TEXTURE);
        generatedItem(ModItems.BUFFALO_TRIM.get(), BISON_TRIM_TEXTURE);
        generatedItem(ModItems.GROUND_BUFFALO.get(), GROUND_BISON_TEXTURE);
        generatedItem(ModItems.LAMB_TRIM.get(), LAMB_TRIM_TEXTURE);
        generatedItem(ModItems.GROUND_LAMB.get(), GROUND_LAMB_TEXTURE);
        generatedItem(ModItems.VENISON_TRIM.get(), VENISON_TRIM_TEXTURE);
        generatedItem(ModItems.GROUND_VENISON.get(), GROUND_VENISON_TEXTURE);
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
