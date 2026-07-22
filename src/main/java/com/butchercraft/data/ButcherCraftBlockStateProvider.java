package com.butchercraft.data;

import com.butchercraft.ButcherCraft;
import com.butchercraft.registration.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

final class ButcherCraftBlockStateProvider extends BlockStateProvider {
    private static final ResourceLocation DEVELOPMENT_PLACEHOLDER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "item/development_test_item");
    private static final ResourceLocation PACKAGING_TABLE_SURFACE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "block/workstation/packaging_table_surface");
    private static final ResourceLocation PACKAGING_TABLE_FRAME_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "block/workstation/packaging_table_frame");
    private static final ResourceLocation PACKAGING_TABLE_ROLL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ButcherCraft.MOD_ID, "block/workstation/packaging_table_roll");

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
        BlockModelBuilder model = models().getBuilder("packaging_table")
                .texture("particle", PACKAGING_TABLE_SURFACE_TEXTURE)
                .texture("surface", PACKAGING_TABLE_SURFACE_TEXTURE)
                .texture("frame", PACKAGING_TABLE_FRAME_TEXTURE)
                .texture("roll", PACKAGING_TABLE_ROLL_TEXTURE);

        cuboid(model, 0, 12, 0, 16, 15, 16, "#surface");
        cuboid(model, 2, 4, 2, 14, 6, 14, "#surface");
        cuboid(model, 1, 9, 1, 15, 12, 3, "#frame");
        cuboid(model, 1, 9, 13, 15, 12, 15, "#frame");
        cuboid(model, 1, 9, 3, 3, 12, 13, "#frame");
        cuboid(model, 13, 9, 3, 15, 12, 13, "#frame");
        cuboid(model, 1, 0, 1, 3, 12, 3, "#frame");
        cuboid(model, 13, 0, 1, 15, 12, 3, "#frame");
        cuboid(model, 1, 0, 13, 3, 12, 15, "#frame");
        cuboid(model, 13, 0, 13, 15, 12, 15, "#frame");
        cuboid(model, 2, 15, 1, 14, 16, 3, "#roll");
        return model;
    }

    private static void cuboid(
            BlockModelBuilder model,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            String texture
    ) {
        model.element().from(x1, y1, z1).to(x2, y2, z2)
                .face(Direction.DOWN).texture(texture).end()
                .face(Direction.UP).texture(texture).end()
                .face(Direction.NORTH).texture(texture).end()
                .face(Direction.SOUTH).texture(texture).end()
                .face(Direction.WEST).texture(texture).end()
                .face(Direction.EAST).texture(texture).end()
                .end();
    }
}
