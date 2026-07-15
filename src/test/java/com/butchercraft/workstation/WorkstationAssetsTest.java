package com.butchercraft.workstation;

import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkstationAssetsTest {
    @Test
    void developmentWorkstationAssetsExist() {
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/blockstates/development_processing_workstation.json")));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/block/development_processing_workstation.json")));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/item/development_processing_workstation.json")));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath("src/main/resources/data/butchercraft/loot_table/blocks/development_processing_workstation.json")));
    }

    @Test
    void developmentWorkstationUsesSharedPlaceholderTexture() throws IOException {
        var model = JsonParser.parseString(Files.readString(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/models/block/development_processing_workstation.json")
        )).getAsJsonObject();

        assertEquals("butchercraft:item/development_test_item", model.getAsJsonObject("textures").get("all").getAsString());
    }

    @Test
    void languageEntriesExist() throws IOException {
        var language = JsonParser.parseString(Files.readString(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/lang/en_us.json")
        )).getAsJsonObject();

        assertEquals("Development Processing Workstation", language.get("block.butchercraft.development_processing_workstation").getAsString());
        assertEquals("Development Processing Workstation", language.get("container.butchercraft.development_processing_workstation").getAsString());
        for (WorkstationFailureCode code : WorkstationFailureCode.values()) {
            assertTrue(language.has(code.messageKey()), "Missing translation for " + code.messageKey());
        }
    }
}
