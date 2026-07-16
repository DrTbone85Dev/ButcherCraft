package com.butchercraft.processing.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.butchercraft.test.TestProjectPaths;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionDatagenResourceTest {
    private static final List<ExpectedTranslation> BUILT_IN_DEFINITION_TRANSLATIONS = List.of(
            new ExpectedTranslation("definition.butchercraft.species.beef", "Beef"),
            new ExpectedTranslation("definition.butchercraft.species.pork", "Pork"),
            new ExpectedTranslation("definition.butchercraft.species.bison", "Bison"),
            new ExpectedTranslation("definition.butchercraft.processing_profile.red_meat", "Red Meat"),
            new ExpectedTranslation("definition.butchercraft.product.beef_trim", "Beef Trim"),
            new ExpectedTranslation("definition.butchercraft.product.ground_beef", "Ground Beef"),
            new ExpectedTranslation("definition.butchercraft.product.pork_trim", "Pork Trim"),
            new ExpectedTranslation("definition.butchercraft.product.ground_pork", "Ground Pork"),
            new ExpectedTranslation("definition.butchercraft.product.bison_trim", "Bison Trim"),
            new ExpectedTranslation("definition.butchercraft.product.ground_bison", "Ground Bison"),
            new ExpectedTranslation("definition.butchercraft.product.beef_forequarter", "Beef Forequarter"),
            new ExpectedTranslation("definition.butchercraft.product.beef_chuck", "Beef Chuck"),
            new ExpectedTranslation("definition.butchercraft.product.beef_rib", "Beef Rib"),
            new ExpectedTranslation("definition.butchercraft.product.beef_packer_brisket", "Packer Brisket"),
            new ExpectedTranslation("definition.butchercraft.product.beef_plate", "Beef Plate"),
            new ExpectedTranslation("definition.butchercraft.product.beef_shank", "Beef Shank"),
            new ExpectedTranslation("definition.butchercraft.product.beef_fat", "Beef Fat"),
            new ExpectedTranslation("definition.butchercraft.product.beef_bone", "Beef Bone"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.grind_beef", "Grind Beef"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.grind_pork", "Grind Pork"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.grind_bison", "Grind Bison"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.break_beef_forequarter", "Break Beef Forequarter")
    );

    @Test
    void allBuiltInGeneratedDefinitionJsonFilesExist() {
        assertTrue(Files.isRegularFile(path("species/beef.json")));
        assertTrue(Files.isRegularFile(path("species/pork.json")));
        assertTrue(Files.isRegularFile(path("species/bison.json")));
        assertTrue(Files.isRegularFile(path("processing_profile/red_meat.json")));
        assertTrue(Files.isRegularFile(path("product/beef_trim.json")));
        assertTrue(Files.isRegularFile(path("product/ground_beef.json")));
        assertTrue(Files.isRegularFile(path("product/pork_trim.json")));
        assertTrue(Files.isRegularFile(path("product/ground_pork.json")));
        assertTrue(Files.isRegularFile(path("product/bison_trim.json")));
        assertTrue(Files.isRegularFile(path("product/ground_bison.json")));
        assertTrue(Files.isRegularFile(path("product/beef_forequarter.json")));
        assertTrue(Files.isRegularFile(path("product/beef_chuck.json")));
        assertTrue(Files.isRegularFile(path("product/beef_rib.json")));
        assertTrue(Files.isRegularFile(path("product/beef_packer_brisket.json")));
        assertFalse(Files.exists(path(retiredGenericBrisketProductPath())));
        assertTrue(Files.isRegularFile(path("product/beef_plate.json")));
        assertTrue(Files.isRegularFile(path("product/beef_shank.json")));
        assertTrue(Files.isRegularFile(path("product/beef_fat.json")));
        assertTrue(Files.isRegularFile(path("product/beef_bone.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/grind_beef.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/grind_pork.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/grind_bison.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/break_beef_forequarter.json")));
    }

    @Test
    void generatedDefinitionJsonUsesExpectedRegistryPathsAndDecodes() throws IOException {
        assertDecodes(SpeciesDefinition.CODEC, path("species/beef.json"));
        assertDecodes(SpeciesDefinition.CODEC, path("species/pork.json"));
        assertDecodes(SpeciesDefinition.CODEC, path("species/bison.json"));
        assertDecodes(ProcessingProfileDefinition.CODEC, path("processing_profile/red_meat.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_trim.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/ground_beef.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/pork_trim.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/ground_pork.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/bison_trim.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/ground_bison.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_forequarter.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_chuck.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_rib.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_packer_brisket.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_plate.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_shank.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_fat.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_bone.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/grind_beef.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/grind_pork.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/grind_bison.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/break_beef_forequarter.json"));
    }

    @Test
    void builtInDefinitionsHaveNoMissingReferenceErrors() {
        DefinitionValidationReport report = new ProcessingDefinitionResolver(BuiltInProcessingDefinitions.builtInView()).validateAll();

        assertFalse(report.hasErrors(), report.issues().toString());
    }

    @Test
    void translationEntriesExistForBuiltInDefinitions() throws IOException {
        JsonObject language = JsonParser.parseString(Files.readString(
                TestProjectPaths.projectPath("src/main/resources/assets/butchercraft/lang/en_us.json")
        )).getAsJsonObject();

        for (ExpectedTranslation translation : BUILT_IN_DEFINITION_TRANSLATIONS) {
            assertTrue(language.has(translation.key()), "Missing translation key: " + translation.key());
            assertEquals(
                    translation.value(),
                    language.get(translation.key()).getAsString(),
                    "Mismatched translation value for key: " + translation.key()
            );
        }
    }

    private static Path path(String suffix) {
        return TestProjectPaths.projectPath("src/generated/resources/data/butchercraft/butchercraft/" + suffix);
    }

    private static String retiredGenericBrisketProductPath() {
        return "product/beef_" + "brisket.json";
    }

    private static <T> void assertDecodes(Codec<T> codec, Path path) throws IOException {
        JsonElement json = JsonParser.parseString(Files.readString(path));
        var result = codec.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isEmpty(), result.toString());
    }

    private record ExpectedTranslation(String key, String value) {
    }
}
