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
            new ExpectedTranslation("definition.butchercraft.product.retail_ground_beef", "Retail Ground Beef"),
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
            new ExpectedTranslation("definition.butchercraft.product.beef_hindquarter", "Beef Hindquarter"),
            new ExpectedTranslation("definition.butchercraft.product.beef_round", "Beef Round"),
            new ExpectedTranslation("definition.butchercraft.product.beef_sirloin", "Beef Sirloin"),
            new ExpectedTranslation("definition.butchercraft.product.beef_short_loin", "Beef Short Loin"),
            new ExpectedTranslation("definition.butchercraft.product.beef_flank", "Beef Flank"),
            new ExpectedTranslation("definition.butchercraft.product.t_bone_steak", "T-Bone Steak"),
            new ExpectedTranslation("definition.butchercraft.product.porterhouse_steak", "Porterhouse Steak"),
            new ExpectedTranslation("definition.butchercraft.product.beef_strip_loin", "Beef Strip Loin"),
            new ExpectedTranslation("definition.butchercraft.product.beef_tenderloin", "Beef Tenderloin"),
            new ExpectedTranslation("definition.butchercraft.product.top_round", "Top Round"),
            new ExpectedTranslation("definition.butchercraft.product.bottom_round", "Bottom Round"),
            new ExpectedTranslation("definition.butchercraft.product.eye_of_round", "Eye of Round"),
            new ExpectedTranslation("definition.butchercraft.product.sirloin_tip", "Sirloin Tip"),
            new ExpectedTranslation("definition.butchercraft.product.top_sirloin", "Top Sirloin"),
            new ExpectedTranslation("definition.butchercraft.product.sirloin_steak", "Sirloin Steak"),
            new ExpectedTranslation("definition.butchercraft.product.tri_tip", "Tri-Tip"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.grind_beef", "Grind Beef"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.grind_pork", "Grind Pork"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.grind_bison", "Grind Bison"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.break_beef_forequarter", "Break Beef Forequarter"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.break_beef_hindquarter", "Break Beef Hindquarter"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.cut_beef_short_loin", "Cut Beef Short Loin"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.cut_beef_round", "Cut Beef Round"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.cut_beef_sirloin", "Cut Beef Sirloin"),
            new ExpectedTranslation("definition.butchercraft.processing_operation.package_retail", "Package Retail")
    );

    @Test
    void allBuiltInGeneratedDefinitionJsonFilesExist() {
        assertTrue(Files.isRegularFile(path("species/beef.json")));
        assertTrue(Files.isRegularFile(path("species/pork.json")));
        assertTrue(Files.isRegularFile(path("species/bison.json")));
        assertTrue(Files.isRegularFile(path("processing_profile/red_meat.json")));
        assertTrue(Files.isRegularFile(path("product/beef_trim.json")));
        assertTrue(Files.isRegularFile(path("product/ground_beef.json")));
        assertTrue(Files.isRegularFile(path("product/retail_ground_beef.json")));
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
        assertTrue(Files.isRegularFile(path("product/beef_hindquarter.json")));
        assertTrue(Files.isRegularFile(path("product/beef_round.json")));
        assertTrue(Files.isRegularFile(path("product/beef_sirloin.json")));
        assertTrue(Files.isRegularFile(path("product/beef_short_loin.json")));
        assertTrue(Files.isRegularFile(path("product/beef_flank.json")));
        assertTrue(Files.isRegularFile(path("product/t_bone_steak.json")));
        assertTrue(Files.isRegularFile(path("product/porterhouse_steak.json")));
        assertTrue(Files.isRegularFile(path("product/beef_strip_loin.json")));
        assertTrue(Files.isRegularFile(path("product/beef_tenderloin.json")));
        assertTrue(Files.isRegularFile(path("product/top_round.json")));
        assertTrue(Files.isRegularFile(path("product/bottom_round.json")));
        assertTrue(Files.isRegularFile(path("product/eye_of_round.json")));
        assertTrue(Files.isRegularFile(path("product/sirloin_tip.json")));
        assertTrue(Files.isRegularFile(path("product/top_sirloin.json")));
        assertTrue(Files.isRegularFile(path("product/sirloin_steak.json")));
        assertTrue(Files.isRegularFile(path("product/tri_tip.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/grind_beef.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/grind_pork.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/grind_bison.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/break_beef_forequarter.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/break_beef_hindquarter.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/cut_beef_short_loin.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/cut_beef_round.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/cut_beef_sirloin.json")));
        assertTrue(Files.isRegularFile(path("processing_operation/package_retail.json")));
    }

    @Test
    void generatedDefinitionJsonUsesExpectedRegistryPathsAndDecodes() throws IOException {
        assertDecodes(SpeciesDefinition.CODEC, path("species/beef.json"));
        assertDecodes(SpeciesDefinition.CODEC, path("species/pork.json"));
        assertDecodes(SpeciesDefinition.CODEC, path("species/bison.json"));
        assertDecodes(ProcessingProfileDefinition.CODEC, path("processing_profile/red_meat.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_trim.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/ground_beef.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/retail_ground_beef.json"));
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
        assertDecodes(ProductDefinition.CODEC, path("product/beef_hindquarter.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_round.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_sirloin.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_short_loin.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_flank.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/t_bone_steak.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/porterhouse_steak.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_strip_loin.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/beef_tenderloin.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/top_round.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/bottom_round.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/eye_of_round.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/sirloin_tip.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/top_sirloin.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/sirloin_steak.json"));
        assertDecodes(ProductDefinition.CODEC, path("product/tri_tip.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/grind_beef.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/grind_pork.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/grind_bison.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/break_beef_forequarter.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/break_beef_hindquarter.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/cut_beef_short_loin.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/cut_beef_round.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/cut_beef_sirloin.json"));
        assertDecodes(ProcessingOperationDefinition.CODEC, path("processing_operation/package_retail.json"));
    }

    @Test
    void generatedRetailProductRegistryJsonCanDeclarePackagingMetadata() throws IOException {
        JsonObject retailGroundBeef = JsonParser.parseString(Files.readString(path("product/retail_ground_beef.json")))
                .getAsJsonObject();

        assertTrue(retailGroundBeef.has("packaging"));
        JsonObject packaging = retailGroundBeef.getAsJsonObject("packaging");
        assertEquals("butchercraft:retail_package", packaging.get("definition").getAsString());
        assertEquals("butchercraft:ground_beef", packaging.get("source_product").getAsString());
    }

    @Test
    void generatedProductRegistryJsonUsesRuntimeSchemaNotSerializedContentSchema() throws IOException {
        JsonObject topSirloin = JsonParser.parseString(Files.readString(path("product/top_sirloin.json")))
                .getAsJsonObject();

        for (String runtimeField : List.of(
                "display_name_key",
                "species",
                "product_category",
                "processing_state",
                "quantity_unit",
                "edible",
                "bone_state",
                "spoilage_eligible",
                "graph_input",
                "graph_output"
        )) {
            assertTrue(topSirloin.has(runtimeField), "Missing runtime product registry field: " + runtimeField);
        }

        for (String serializedContentField : List.of(
                "schema_version",
                "id",
                "display_name",
                "category",
                "default_quantity_unit",
                "metadata"
        )) {
            assertFalse(
                    topSirloin.has(serializedContentField),
                    "Runtime product registry JSON must not use serialized content field: " + serializedContentField
            );
        }
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
