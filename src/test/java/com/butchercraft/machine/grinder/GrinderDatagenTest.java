package com.butchercraft.machine.grinder;

import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrinderDatagenTest {
    @Test
    void generatedGrindBeefDefinitionUsesGrinderCapability() throws IOException {
        assertGeneratedGrindingCapability("grind_beef");
        assertGeneratedGrindingCapability("grind_pork");
        assertGeneratedGrindingCapability("grind_bison");
        assertEquals(
                "butchercraft:grinding",
                BuiltInProcessingDefinitions.grindBeefOperation().workstationCapability().orElseThrow().toString()
        );
        assertEquals(
                "butchercraft:grinding",
                BuiltInProcessingDefinitions.grindPorkOperation().workstationCapability().orElseThrow().toString()
        );
        assertEquals(
                "butchercraft:grinding",
                BuiltInProcessingDefinitions.grindBisonOperation().workstationCapability().orElseThrow().toString()
        );
    }

    @Test
    void grinderResourceProvidersAreRegistered() throws IOException {
        String dataGenerators = source("src/main/java/com/butchercraft/data/ButcherCraftDataGenerators.java");
        String blockStates = source("src/main/java/com/butchercraft/data/ButcherCraftBlockStateProvider.java");
        String lootTables = source("src/main/java/com/butchercraft/data/ButcherCraftLootTableProvider.java");

        assertTrue(dataGenerators.contains("ButcherCraftBlockStateProvider"));
        assertTrue(dataGenerators.contains("ButcherCraftLootTableProvider"));
        assertTrue(dataGenerators.contains("event.includeClient()"));
        assertTrue(dataGenerators.contains("event.includeServer()"));
        assertTrue(blockStates.contains("ButcherCraft Block States"));
        assertTrue(blockStates.contains("horizontalBlock(ModBlocks.GRINDER.get(), grinderModel)"));
        assertTrue(blockStates.contains("simpleBlockItem(ModBlocks.GRINDER.get(), grinderModel)"));
        assertTrue(blockStates.contains("models().getBuilder(\"grinder\")"));
        assertTrue(lootTables.contains("extends LootTableProvider"));
        assertTrue(lootTables.contains("SubProviderEntry(ButcherCraftBlockLootProvider::new, LootContextParamSets.BLOCK)"));
        assertTrue(lootTables.contains("extends BlockLootSubProvider"));
        assertTrue(lootTables.contains("dropSelf(ModBlocks.GRINDER.get())"));
        assertTrue(lootTables.contains("protected Iterable<Block> getKnownBlocks()"));
        assertTrue(lootTables.contains("ModBlocks.GRINDER.get()"));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }

    private static void assertGeneratedGrindingCapability(String operationName) throws IOException {
        Path definition = TestProjectPaths.projectPath(
                "src/generated/resources/data/butchercraft/butchercraft/processing_operation/" + operationName + ".json"
        );
        assertTrue(
                Files.isRegularFile(definition),
                "Missing generated " + operationName + " definition. Run .\\gradlew.bat runData and copy src/generated/resources."
        );

        var json = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();

        assertEquals("butchercraft:grinding", json.get("workstation_capability").getAsString());
    }
}
