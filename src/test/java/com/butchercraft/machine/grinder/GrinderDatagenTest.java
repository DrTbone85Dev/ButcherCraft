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
        assertGeneratedGrindingCapability("grind_chicken");
        assertGeneratedGrindingCapability("grind_bison");
        assertGeneratedGrindingCapability("grind_lamb");
        assertGeneratedGrindingCapability("grind_venison");
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
                BuiltInProcessingDefinitions.grindChickenOperation().workstationCapability().orElseThrow().toString()
        );
        assertEquals(
                "butchercraft:grinding",
                BuiltInProcessingDefinitions.grindBisonOperation().workstationCapability().orElseThrow().toString()
        );
        assertEquals(
                "butchercraft:grinding",
                BuiltInProcessingDefinitions.grindLambOperation().workstationCapability().orElseThrow().toString()
        );
        assertEquals(
                "butchercraft:grinding",
                BuiltInProcessingDefinitions.grindVenisonOperation().workstationCapability().orElseThrow().toString()
        );
    }

    @Test
    void grinderResourceProvidersAreRegistered() throws IOException {
        String dataGenerators = source("src/main/java/com/butchercraft/data/ButcherCraftDataGenerators.java");
        String blockStates = source("src/main/java/com/butchercraft/data/ButcherCraftBlockStateProvider.java");
        String lootTables = source("src/main/java/com/butchercraft/data/ButcherCraftLootTableProvider.java");
        String recipes = source("src/main/java/com/butchercraft/data/ButcherCraftRecipeProvider.java");

        assertTrue(dataGenerators.contains("ButcherCraftBlockStateProvider"));
        assertTrue(dataGenerators.contains("ButcherCraftLootTableProvider"));
        assertTrue(dataGenerators.contains("ButcherCraftRecipeProvider"));
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
        assertTrue(recipes.contains("extends RecipeProvider"));
        assertTrue(recipes.contains("ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.GRINDER.get())"));
        assertTrue(recipes.contains("Items.IRON_INGOT"));
        assertTrue(recipes.contains("Items.COPPER_INGOT"));
    }

    @Test
    void generatedGrinderRecipeMakesGrinderSurvivalObtainable() throws IOException {
        Path recipe = TestProjectPaths.projectPath(
                "src/generated/resources/data/butchercraft/recipe/grinder.json"
        );
        assertTrue(
                Files.isRegularFile(recipe),
                "Missing generated grinder recipe. Run .\\gradlew.bat runData and copy src/generated/resources."
        );

        var json = JsonParser.parseString(Files.readString(recipe)).getAsJsonObject();

        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals("ICI", json.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("SRS", json.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("ICI", json.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("butchercraft:grinder", recipeResult(json));
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

    private static String recipeResult(com.google.gson.JsonObject json) {
        var result = json.getAsJsonObject("result");
        if (result.has("id")) {
            return result.get("id").getAsString();
        }
        return result.get("item").getAsString();
    }
}
