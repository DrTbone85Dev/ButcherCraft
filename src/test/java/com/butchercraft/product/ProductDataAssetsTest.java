package com.butchercraft.product;

import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductDataAssetsTest {
    @Test
    void productLanguageEntriesExist() throws IOException {
        String language = Files.readString(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/lang/en_us.json"));
        var languageJson = JsonParser.parseString(language).getAsJsonObject();

        assertTrue(language.contains("\"item.butchercraft.beef_trim_test\""));
        assertTrue(language.contains("\"item.butchercraft.ground_beef_test\""));
        assertEquals("Beef Trim", languageJson.get("item.butchercraft.beef_trim_test").getAsString());
        assertEquals("Ground Beef", languageJson.get("item.butchercraft.ground_beef_test").getAsString());
        assertTrue(language.contains("\"item.butchercraft.retail_ground_beef_test\""));
        assertTrue(language.contains("\"item.butchercraft.pork_trim_test\""));
        assertTrue(language.contains("\"item.butchercraft.ground_pork_test\""));
        assertEquals("Pork Trim", languageJson.get("item.butchercraft.pork_trim_test").getAsString());
        assertEquals("Ground Pork", languageJson.get("item.butchercraft.ground_pork_test").getAsString());
        assertTrue(language.contains("\"item.butchercraft.chicken_trim\""));
        assertTrue(language.contains("\"item.butchercraft.ground_chicken\""));
        assertEquals("Chicken Trim", languageJson.get("item.butchercraft.chicken_trim").getAsString());
        assertEquals("Ground Chicken", languageJson.get("item.butchercraft.ground_chicken").getAsString());
        assertTrue(language.contains("\"item.butchercraft.bison_trim_test\""));
        assertTrue(language.contains("\"item.butchercraft.ground_bison_test\""));
        assertEquals("Buffalo Trim", languageJson.get("item.butchercraft.bison_trim_test").getAsString());
        assertEquals("Ground Buffalo", languageJson.get("item.butchercraft.ground_bison_test").getAsString());
        assertTrue(language.contains("\"item.butchercraft.lamb_trim\""));
        assertTrue(language.contains("\"item.butchercraft.ground_lamb\""));
        assertEquals("Lamb Trim", languageJson.get("item.butchercraft.lamb_trim").getAsString());
        assertEquals("Ground Lamb", languageJson.get("item.butchercraft.ground_lamb").getAsString());
        assertTrue(language.contains("\"item.butchercraft.venison_trim\""));
        assertTrue(language.contains("\"item.butchercraft.ground_venison\""));
        assertEquals("Venison Trim", languageJson.get("item.butchercraft.venison_trim").getAsString());
        assertEquals("Ground Venison", languageJson.get("item.butchercraft.ground_venison").getAsString());
        assertTrue(language.contains("\"item.butchercraft.beef_forequarter_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_chuck_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_rib_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_packer_brisket_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_plate_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_shank_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_fat_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_bone_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_hindquarter_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_round_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_sirloin_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_short_loin_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_flank_test\""));
        assertTrue(language.contains("\"item.butchercraft.t_bone_steak_test\""));
        assertTrue(language.contains("\"item.butchercraft.porterhouse_steak_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_strip_loin_test\""));
        assertTrue(language.contains("\"item.butchercraft.beef_tenderloin_test\""));
        assertTrue(language.contains("\"item.butchercraft.top_round_test\""));
        assertTrue(language.contains("\"item.butchercraft.bottom_round_test\""));
        assertTrue(language.contains("\"item.butchercraft.eye_of_round_test\""));
        assertTrue(language.contains("\"item.butchercraft.sirloin_tip_test\""));
        assertTrue(language.contains("\"item.butchercraft.top_sirloin_test\""));
        assertTrue(language.contains("\"item.butchercraft.sirloin_steak_test\""));
        assertTrue(language.contains("\"item.butchercraft.tri_tip_test\""));
        assertTrue(language.contains("\"tooltip.butchercraft.product_data.product\""));
        assertTrue(language.contains("\"tooltip.butchercraft.product_data.packaging\""));
        assertTrue(language.contains("\"tooltip.butchercraft.product_data.quality_score\""));
    }

    @Test
    void productItemModelsUseExpectedTextures() throws IOException {
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/beef_trim.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/ground_beef.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/pork_trim.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/ground_pork.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/chicken_trim.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/ground_chicken.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/bison_trim.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/ground_bison.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/lamb_trim.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/ground_lamb.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/venison_trim.png"
        )));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/main/resources/assets/butchercraft/textures/item/product/ground_venison.png"
        )));
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/beef_trim_test.json"),
                "butchercraft:item/product/beef_trim"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/ground_beef_test.json"),
                "butchercraft:item/product/ground_beef"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/pork_trim_test.json"),
                "butchercraft:item/product/pork_trim"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/ground_pork_test.json"),
                "butchercraft:item/product/ground_pork"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/chicken_trim.json"),
                "butchercraft:item/product/chicken_trim"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/ground_chicken.json"),
                "butchercraft:item/product/ground_chicken"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/bison_trim_test.json"),
                "butchercraft:item/product/bison_trim"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/ground_bison_test.json"),
                "butchercraft:item/product/ground_bison"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/lamb_trim.json"),
                "butchercraft:item/product/lamb_trim"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/ground_lamb.json"),
                "butchercraft:item/product/ground_lamb"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/venison_trim.json"),
                "butchercraft:item/product/venison_trim"
        );
        assertGeneratedModel(
                TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/ground_venison.json"),
                "butchercraft:item/product/ground_venison"
        );
        assertRetailGroundBeefModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/retail_ground_beef_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_forequarter_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_chuck_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_rib_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_packer_brisket_test.json"));
        assertTrue(Files.notExists(TestProjectPaths.projectPath(retiredGenericBrisketItemModelPath())));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_plate_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_shank_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_fat_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_bone_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_hindquarter_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_round_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_sirloin_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_short_loin_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_flank_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/t_bone_steak_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/porterhouse_steak_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_strip_loin_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/beef_tenderloin_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/top_round_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/bottom_round_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/eye_of_round_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/sirloin_tip_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/top_sirloin_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/sirloin_steak_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/generated/resources/assets/butchercraft/models/item/tri_tip_test.json"));
    }

    private static void assertPlaceholderModel(Path path) throws IOException {
        assertGeneratedModel(path, "butchercraft:item/development_test_item");
    }

    private static void assertGeneratedModel(Path path, String texture) throws IOException {
        assertTrue(Files.isRegularFile(path), "Expected item model at " + path);
        String model = Files.readString(path);
        assertTrue(model.contains("\"minecraft:item/generated\""));
        assertTrue(model.contains("\"" + texture + "\""));
    }

    private static void assertRetailGroundBeefModel(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "Expected item model at " + path);
        String model = Files.readString(path);
        assertTrue(model.contains("\"minecraft:item/generated\""));
        assertTrue(model.contains("\"butchercraft:item/packaging/retail_ground_beef\""));
    }

    private static String retiredGenericBrisketItemModelPath() {
        return "src/generated/resources/assets/butchercraft/models/item/beef_" + "brisket_test.json";
    }
}
