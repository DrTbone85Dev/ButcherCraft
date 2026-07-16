package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import com.butchercraft.registration.ModBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

final class ButcherCraftBlockStateProvider extends BlockStateProvider {
    private static final ResourceLocation DEVELOPMENT_PLACEHOLDER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/development_test_item");

    ButcherCraftBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, ButcherCraft.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        ModelFile grinderModel = grinderModel();
        horizontalBlock(ModBlocks.GRINDER.get(), grinderModel);
        simpleBlockItem(ModBlocks.GRINDER.get(), grinderModel);
    }

    @Override
    public String getName() {
        return "ButcherCraft Block States";
    }

    private ModelFile grinderModel() {
        return models().getBuilder("grinder")
                .texture("all", DEVELOPMENT_PLACEHOLDER_TEXTURE)
                .element().from(0, 0, 0).to(16, 9, 16).cube("#all").end()
                .element().from(3, 9, 3).to(13, 13, 13).cube("#all").end()
                .element().from(4, 3, 0).to(12, 8, 2).cube("#all").end();
    }
}
