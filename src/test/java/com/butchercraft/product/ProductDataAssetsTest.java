package com.butchercraft.product;

import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductDataAssetsTest {
    @Test
    void productLanguageEntriesExist() throws IOException {
        String language = Files.readString(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/lang/en_us.json"));

        assertTrue(language.contains("\"item.butchercraft.beef_trim_test\""));
        assertTrue(language.contains("\"item.butchercraft.ground_beef_test\""));
        assertTrue(language.contains("\"item.butchercraft.pork_trim_test\""));
        assertTrue(language.contains("\"item.butchercraft.ground_pork_test\""));
        assertTrue(language.contains("\"item.butchercraft.bison_trim_test\""));
        assertTrue(language.contains("\"item.butchercraft.ground_bison_test\""));
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
        assertTrue(language.contains("\"tooltip.butchercraft.product_data.quality_score\""));
    }

    @Test
    void productItemModelsExistAndUsePlaceholderTexture() throws IOException {
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/beef_trim_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/ground_beef_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/pork_trim_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/ground_pork_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/bison_trim_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/ground_bison_test.json"));
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
        assertTrue(Files.isRegularFile(path), "Expected item model at " + path);
        String model = Files.readString(path);
        assertTrue(model.contains("\"minecraft:item/generated\""));
        assertTrue(model.contains("\"butchercraft:item/development_test_item\""));
    }

    private static String retiredGenericBrisketItemModelPath() {
        return "src/generated/resources/assets/butchercraft/models/item/beef_" + "brisket_test.json";
    }
}
