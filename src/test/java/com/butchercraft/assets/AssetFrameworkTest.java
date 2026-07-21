package com.butchercraft.assets;

import com.butchercraft.client.screen.PackagingTableGuiLayout;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetFrameworkTest {
    private static final Map<String, String> PACKAGING_ITEM_TEXTURES = Map.of(
            "foam_tray", "butchercraft:item/packaging/foam_tray",
            "plastic_wrap_roll", "butchercraft:item/packaging/plastic_wrap_roll",
            "vacuum_bag", "butchercraft:item/packaging/vacuum_bag",
            "butcher_paper_roll", "butchercraft:item/packaging/butcher_paper_roll",
            "freezer_paper_roll", "butchercraft:item/packaging/freezer_paper_roll",
            "retail_label_roll", "butchercraft:item/packaging/retail_label_roll",
            "retail_ground_beef_test", "butchercraft:item/packaging/retail_ground_beef"
    );

    @Test
    void packagingItemModelsParseAndReferenceExistingTextures() throws IOException {
        for (Map.Entry<String, String> entry : PACKAGING_ITEM_TEXTURES.entrySet()) {
            JsonObject model = readJson("src/generated/resources/assets/butchercraft/models/item/" + entry.getKey() + ".json");

            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals(entry.getValue(), model.getAsJsonObject("textures").get("layer0").getAsString());
            assertTextureExists(entry.getValue(), 16, 16);
        }
    }

    @Test
    void packagingTableBlockstateAndModelsParse() throws IOException {
        JsonObject blockstate = readJson("src/generated/resources/assets/butchercraft/blockstates/packaging_table.json");
        JsonObject variants = blockstate.getAsJsonObject("variants");

        for (String facing : List.of("facing=north", "facing=east", "facing=south", "facing=west")) {
            assertTrue(variants.has(facing), "Missing Packaging Table blockstate variant " + facing);
            assertEquals("butchercraft:block/packaging_table", variants.getAsJsonObject(facing).get("model").getAsString());
        }

        JsonObject itemModel = readJson("src/generated/resources/assets/butchercraft/models/item/packaging_table.json");
        assertEquals("butchercraft:block/packaging_table", itemModel.get("parent").getAsString());

        JsonObject blockModel = readJson("src/generated/resources/assets/butchercraft/models/block/packaging_table.json");
        assertEquals("butchercraft:block/workstation/packaging_table_surface",
                blockModel.getAsJsonObject("textures").get("particle").getAsString());
        assertTextureExists("butchercraft:block/workstation/packaging_table_surface", 16, 16);
        assertTextureExists("butchercraft:block/workstation/packaging_table_frame", 16, 16);
        assertTextureExists("butchercraft:block/workstation/packaging_table_roll", 16, 16);
    }

    @Test
    void packagingTableModelGeometryStaysWithinBlockBounds() throws IOException {
        JsonObject blockModel = readJson("src/generated/resources/assets/butchercraft/models/block/packaging_table.json");

        blockModel.getAsJsonArray("elements").forEach(element -> {
            JsonObject cuboid = element.getAsJsonObject();
            assertCoordinateRange(cuboid.getAsJsonArray("from").asList().stream().mapToDouble(value -> value.getAsDouble()).toArray());
            assertCoordinateRange(cuboid.getAsJsonArray("to").asList().stream().mapToDouble(value -> value.getAsDouble()).toArray());

            for (String direction : List.of("down", "up", "north", "south", "west", "east")) {
                JsonObject face = cuboid.getAsJsonObject("faces").getAsJsonObject(direction);
                assertNotNull(face, "Missing " + direction + " face");
                assertTrue(face.has("texture"), "Missing texture for " + direction + " face");
                assertFalse(face.has("cullface"), "Interior Packaging Table cuboids should not declare broad cullfaces");
            }
        });
    }

    @Test
    void packagingTableGuiTextureAndConstantsStayWithinBounds() throws IOException {
        assertEquals("butchercraft", PackagingTableGuiLayout.BACKGROUND_TEXTURE.getNamespace());
        assertEquals("textures/gui/packaging_table.png", PackagingTableGuiLayout.BACKGROUND_TEXTURE.getPath());

        Path guiTexture = TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/textures/gui/packaging_table.png");
        BufferedImage image = ImageIO.read(guiTexture.toFile());
        assertNotNull(image, "Expected readable GUI PNG");
        assertEquals(PackagingTableGuiLayout.TEXTURE_WIDTH, image.getWidth());
        assertEquals(PackagingTableGuiLayout.TEXTURE_HEIGHT, image.getHeight());

        assertTrue(PackagingTableGuiLayout.IMAGE_WIDTH <= PackagingTableGuiLayout.TEXTURE_WIDTH);
        assertTrue(PackagingTableGuiLayout.IMAGE_HEIGHT <= PackagingTableGuiLayout.TEXTURE_HEIGHT);
        assertTrue(PackagingTableGuiLayout.PROGRESS_X + PackagingTableGuiLayout.PROGRESS_WIDTH <= PackagingTableGuiLayout.IMAGE_WIDTH);
        assertTrue(PackagingTableGuiLayout.PROGRESS_Y + PackagingTableGuiLayout.PROGRESS_HEIGHT <= PackagingTableGuiLayout.IMAGE_HEIGHT);
        assertTrue(PackagingTableGuiLayout.STATUS_X + PackagingTableGuiLayout.STATUS_WIDTH <= PackagingTableGuiLayout.IMAGE_WIDTH);
    }

    @Test
    void assetManifestCoversPackagingAssetsAndKeepsPlaceholdersUnapproved() throws IOException {
        String manifest = Files.readString(TestProjectPaths.projectPath("docs/ASSET_MANIFEST.md"));

        for (String required : List.of(
                "Packaging Table block model",
                "Foam Tray",
                "Plastic Wrap Roll",
                "Vacuum Bag",
                "Butcher Paper Roll",
                "Freezer Paper Roll",
                "Retail Label Roll",
                "Retail Ground Beef Test Product",
                "butchercraft:item/packaging/retail_ground_beef"
        )) {
            assertTrue(manifest.contains(required), "Manifest missing " + required);
        }

        for (String line : manifest.lines().toList()) {
            if (line.contains("|") && line.contains("Placeholder")) {
                assertFalse(line.contains("Approved"), "Placeholder asset must not be marked approved: " + line);
            }
        }
    }

    @Test
    void retiredSharedPackagingPlaceholderIsNotRetained() {
        assertFalse(Files.exists(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/packaging_supply_placeholder.png"
        )));
    }

    private static JsonObject readJson(String path) throws IOException {
        return JsonParser.parseString(Files.readString(TestProjectPaths.projectPath(path))).getAsJsonObject();
    }

    private static void assertTextureExists(String resourceLocation, int expectedWidth, int expectedHeight) throws IOException {
        String[] parts = resourceLocation.split(":", 2);
        assertEquals(2, parts.length, "Expected namespaced texture location");
        Path texture = TestProjectPaths.projectPath(
                "src/main/resources/assets/" + parts[0] + "/textures/" + parts[1] + ".png"
        );
        assertTrue(Files.isRegularFile(texture), "Missing texture " + texture);
        BufferedImage image = ImageIO.read(texture.toFile());
        assertNotNull(image, "Expected readable PNG " + texture);
        assertEquals(expectedWidth, image.getWidth(), "Unexpected texture width for " + texture);
        assertEquals(expectedHeight, image.getHeight(), "Unexpected texture height for " + texture);
    }

    private static void assertCoordinateRange(double[] coordinates) {
        assertEquals(3, coordinates.length);
        for (double coordinate : coordinates) {
            assertTrue(coordinate >= 0.0D, "Coordinate must not be below the block");
            assertTrue(coordinate <= 16.0D, "Coordinate must not exceed block bounds");
        }
    }
}
