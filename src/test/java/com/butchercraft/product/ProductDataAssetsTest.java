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
        assertTrue(language.contains("\"tooltip.butchercraft.product_data.product\""));
        assertTrue(language.contains("\"tooltip.butchercraft.product_data.quality_score\""));
    }

    @Test
    void productItemModelsExistAndUsePlaceholderTexture() throws IOException {
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/beef_trim_test.json"));
        assertPlaceholderModel(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/ground_beef_test.json"));
    }

    private static void assertPlaceholderModel(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "Expected item model at " + path);
        String model = Files.readString(path);
        assertTrue(model.contains("\"minecraft:item/generated\""));
        assertTrue(model.contains("\"butchercraft:item/development_test_item\""));
    }
}
