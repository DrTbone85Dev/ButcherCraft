package com.butchercraft.machine.bandsaw;

import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandsawAssetsTest {
    @Test
    void generatedBandsawAssetsExist() throws IOException {
        assertResource("src/generated/resources/assets/butchercraft/blockstates/bandsaw.json");
        assertResource("src/generated/resources/assets/butchercraft/blockstates/bandsaw_upper.json");
        assertResource("src/generated/resources/assets/butchercraft/models/block/bandsaw.json");
        assertResource("src/generated/resources/assets/butchercraft/models/block/bandsaw_upper.json");
        assertResource("src/generated/resources/assets/butchercraft/models/item/bandsaw.json");
        assertResource("src/generated/resources/data/butchercraft/loot_table/blocks/bandsaw.json");
    }

    @Test
    void bandsawLanguageEntriesExist() throws IOException {
        Path languagePath = TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/lang/en_us.json");
        var language = JsonParser.parseString(Files.readString(languagePath)).getAsJsonObject();

        assertEquals("Industrial Bandsaw", language.get("block.butchercraft.bandsaw").getAsString());
        assertEquals("Industrial Bandsaw", language.get("container.butchercraft.bandsaw").getAsString());
    }

    private static void assertResource(String relativePath) {
        Path path = TestProjectPaths.projectPath(relativePath);
        assertTrue(Files.isRegularFile(path), "Missing Bandsaw resource: " + relativePath);
    }
}
