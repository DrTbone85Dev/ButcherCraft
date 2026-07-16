package com.butchercraft.machine.bandsaw;

import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BandsawDatagenTest {
    @Test
    void generatedForequarterDefinitionUsesBandsawCapabilityAndOrderedOutputs() throws IOException {
        Path definition = TestProjectPaths.projectPath(
                "src/generated/resources/data/butchercraft/butchercraft/processing_operation/break_beef_forequarter.json"
        );
        assertTrue(
                Files.isRegularFile(definition),
                "Missing generated break_beef_forequarter definition. Run .\\gradlew.bat runData and copy src/generated/resources."
        );

        var json = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();
        var outputs = json.getAsJsonArray("outputs");

        assertEquals("butchercraft:bandsaw", json.get("workstation_capability").getAsString());
        assertEquals(8, outputs.size());
        assertEquals("butchercraft:beef_chuck", outputs.get(0).getAsJsonObject().get("product").getAsString());
        assertEquals("butchercraft:beef_bone", outputs.get(7).getAsJsonObject().get("product").getAsString());
    }

    @Test
    void bandsawResourceProvidersAreRegistered() throws IOException {
        String blockStates = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/data/ButcherCraftBlockStateProvider.java"));
        String lootTables = Files.readString(TestProjectPaths.projectPath("src/main/java/com/butchercraft/data/ButcherCraftLootTableProvider.java"));

        assertTrue(blockStates.contains("horizontalBlock(ModBlocks.BANDSAW.get(), bandsawModel)"));
        assertTrue(blockStates.contains("horizontalBlock(ModBlocks.BANDSAW_UPPER.get(), bandsawUpperModel())"));
        assertTrue(lootTables.contains("dropSelf(ModBlocks.BANDSAW.get())"));
    }
}
