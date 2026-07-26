package com.butchercraft.machine.pattyformer;

import com.butchercraft.processing.definition.BuiltInProcessingDefinitions;
import com.butchercraft.test.TestProjectPaths;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PattyFormerDatagenTest {
    @Test
    void generatedFormBeefPattiesDefinitionUsesPattyFormerCapability() throws IOException {
        assertGeneratedPattyFormingCapability("form_beef_patties");
        assertEquals(
                "butchercraft:patty_forming",
                BuiltInProcessingDefinitions.formBeefPattiesOperation().workstationCapability().orElseThrow().toString()
        );
    }

    @Test
    void pattyFormerResourceProvidersAreRegistered() throws IOException {
        String blockStates = source("src/main/java/com/butchercraft/data/ButcherCraftBlockStateProvider.java");
        String itemModels = source("src/main/java/com/butchercraft/data/ButcherCraftItemModelProvider.java");
        String language = source("src/main/java/com/butchercraft/data/ButcherCraftLanguageProvider.java");
        String lootTables = source("src/main/java/com/butchercraft/data/ButcherCraftLootTableProvider.java");
        String recipes = source("src/main/java/com/butchercraft/data/ButcherCraftRecipeProvider.java");

        assertTrue(blockStates.contains("horizontalBlock(ModBlocks.PATTY_FORMER.get(), pattyFormerModel)"));
        assertTrue(blockStates.contains("simpleBlockItem(ModBlocks.PATTY_FORMER.get(), pattyFormerModel)"));
        assertTrue(blockStates.contains("models().getBuilder(\"patty_former\")"));
        assertTrue(itemModels.contains("generatedItem(ModItems.BEEF_PATTIES.get(), BEEF_PATTIES_TEXTURE)"));
        assertTrue(language.contains("add(ModBlocks.PATTY_FORMER.get(), \"Patty Former\")"));
        assertTrue(language.contains("add(ModItems.BEEF_PATTIES.get(), \"Beef Patties\")"));
        assertTrue(lootTables.contains("dropSelf(ModBlocks.PATTY_FORMER.get())"));
        assertTrue(recipes.contains("ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.PATTY_FORMER.get())"));
        assertTrue(recipes.contains("Items.PISTON"));
    }

    @Test
    void generatedPattyFormerRecipeMakesPattyFormerSurvivalObtainable() throws IOException {
        Path recipe = TestProjectPaths.projectPath(
                "src/generated/resources/data/butchercraft/recipe/patty_former.json"
        );
        assertTrue(
                Files.isRegularFile(recipe),
                "Missing generated Patty Former recipe. Run .\\gradlew.bat runData and copy src/generated/resources."
        );

        var json = JsonParser.parseString(Files.readString(recipe)).getAsJsonObject();

        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals("IPI", json.getAsJsonArray("pattern").get(0).getAsString());
        assertEquals("SRS", json.getAsJsonArray("pattern").get(1).getAsString());
        assertEquals("ICI", json.getAsJsonArray("pattern").get(2).getAsString());
        assertEquals("butchercraft:patty_former", recipeResult(json));
    }

    private static String source(String relativePath) throws IOException {
        return Files.readString(TestProjectPaths.projectPath(relativePath));
    }

    private static void assertGeneratedPattyFormingCapability(String operationName) throws IOException {
        Path definition = TestProjectPaths.projectPath(
                "src/generated/resources/data/butchercraft/butchercraft/processing_operation/" + operationName + ".json"
        );
        assertTrue(
                Files.isRegularFile(definition),
                "Missing generated " + operationName + " definition. Run .\\gradlew.bat runData and copy src/generated/resources."
        );

        var json = JsonParser.parseString(Files.readString(definition)).getAsJsonObject();

        assertEquals("butchercraft:patty_forming", json.get("workstation_capability").getAsString());
    }

    private static String recipeResult(com.google.gson.JsonObject json) {
        var result = json.getAsJsonObject("result");
        if (result.has("id")) {
            return result.get("id").getAsString();
        }
        return result.get("item").getAsString();
    }
}
