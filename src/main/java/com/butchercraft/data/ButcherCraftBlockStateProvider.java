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

        ModelFile bandsawModel = bandsawModel();
        horizontalBlock(ModBlocks.BANDSAW.get(), bandsawModel);
        simpleBlockItem(ModBlocks.BANDSAW.get(), bandsawModel);
        horizontalBlock(ModBlocks.BANDSAW_UPPER.get(), bandsawUpperModel());

        ModelFile packagingTableModel = packagingTableModel();
        horizontalBlock(ModBlocks.PACKAGING_TABLE.get(), packagingTableModel);
        simpleBlockItem(ModBlocks.PACKAGING_TABLE.get(), packagingTableModel);
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

    private ModelFile bandsawModel() {
        return models().getBuilder("bandsaw")
                .texture("all", DEVELOPMENT_PLACEHOLDER_TEXTURE)
                .element().from(0, 0, 0).to(16, 10, 16).cube("#all").end()
                .element().from(2, 10, 2).to(14, 16, 14).cube("#all").end()
                .element().from(6, 3, 0).to(10, 14, 2).cube("#all").end();
    }

    private ModelFile bandsawUpperModel() {
        return models().getBuilder("bandsaw_upper")
                .texture("all", DEVELOPMENT_PLACEHOLDER_TEXTURE)
                .element().from(2, 0, 2).to(14, 13, 14).cube("#all").end()
                .element().from(5, 1, 0).to(11, 12, 2).cube("#all").end()
                .element().from(3, 13, 3).to(13, 16, 13).cube("#all").end();
    }

    private ModelFile packagingTableModel() {
        return models().getBuilder("packaging_table")
                .texture("all", DEVELOPMENT_PLACEHOLDER_TEXTURE)
                .element().from(0, 0, 0).to(16, 10, 16).cube("#all").end()
                .element().from(2, 10, 2).to(14, 12, 14).cube("#all").end()
                .element().from(3, 12, 3).to(13, 13, 13).cube("#all").end()
                .element().from(1, 10, 0).to(15, 11, 2).cube("#all").end();
    }
}
