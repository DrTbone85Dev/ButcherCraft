package com.butchercraft.processing.definition;

import com.butchercraft.test.TestProjectPaths;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalButcherTerminologyTest {
    @Test
    void packerBrisketIsTheCanonicalBuiltInWholeBrisketProduct() {
        DefinitionRegistryView view = BuiltInProcessingDefinitions.builtInView();

        assertFalse(view.products().containsKey(retiredGenericBrisketId()));
        assertTrue(view.products().containsKey(BuiltInDefinitionIds.BEEF_PACKER_BRISKET));

        ProductDefinition product = view.products().get(BuiltInDefinitionIds.BEEF_PACKER_BRISKET);
        assertEquals("definition.butchercraft.product.beef_packer_brisket", product.displayNameKey());
        assertEquals(BuiltInDefinitionIds.BEEF, product.species());
        assertEquals(BoneState.BONE_IN, product.boneState());
        assertTrue(product.traits().contains(BuiltInDefinitionIds.BEEF_PACKER_BRISKET));
    }

    @Test
    void forequarterFabricationOutputsUseCanonicalPackerBrisket() {
        List<ResourceLocation> outputProducts = BuiltInProcessingDefinitions.breakBeefForequarterOperation()
                .outputs()
                .stream()
                .map(ProcessingOutputDefinition::product)
                .toList();

        assertEquals(List.of(
                BuiltInDefinitionIds.BEEF_CHUCK,
                BuiltInDefinitionIds.BEEF_RIB,
                BuiltInDefinitionIds.BEEF_PACKER_BRISKET,
                BuiltInDefinitionIds.BEEF_PLATE,
                BuiltInDefinitionIds.BEEF_SHANK,
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.BEEF_FAT,
                BuiltInDefinitionIds.BEEF_BONE
        ), outputProducts);
        assertFalse(outputProducts.contains(retiredGenericBrisketId()));
    }

    @Test
    void deferredRetailCutTermsAreNotInventedAsBuiltInProducts() {
        DefinitionRegistryView view = BuiltInProcessingDefinitions.builtInView();

        assertFalse(view.products().containsKey(BuiltInDefinitionIds.id("beef_kansas_city_strip")));
        assertFalse(view.products().containsKey(BuiltInDefinitionIds.id("beef_picanha")));
        assertFalse(view.products().containsKey(BuiltInDefinitionIds.id("beef_prime_rib")));
        assertFalse(view.products().containsKey(BuiltInDefinitionIds.id("beef_denver_steak")));
    }

    @Test
    void generatedPathsUseCanonicalPackerBrisketResources() {
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/generated/resources/data/butchercraft/butchercraft/product/beef_packer_brisket.json"
        )));
        assertTrue(Files.notExists(TestProjectPaths.projectPath(retiredGenericBrisketProductPath())));
        assertTrue(Files.isRegularFile(TestProjectPaths.projectPath(
                "src/generated/resources/assets/butchercraft/models/item/beef_packer_brisket_test.json"
        )));
        assertTrue(Files.notExists(TestProjectPaths.projectPath(retiredGenericBrisketItemModelPath())));
    }

    @Test
    void activeSourceAndGeneratedResourcesHaveNoRetiredTerminology() throws IOException {
        List<String> forbiddenTerms = List.of(
                retiredGenericBrisketId().toString(),
                "beef_" + "brisket",
                "Beef " + "Brisket",
                "new_" + "york_strip",
                "New " + "York Strip",
                "sirloin_" + "cap",
                "top_" + "sirloin_" + "cap",
                "ribeye_" + "roast",
                "Ribeye " + "Roast",
                "boneless_" + "short_rib",
                "Boneless " + "Short Ribs"
        );
        List<Path> roots = List.of(
                TestProjectPaths.projectPath("src/main/java"),
                TestProjectPaths.projectPath("src/main/resources"),
                TestProjectPaths.projectPath("src/generated/resources"),
                TestProjectPaths.projectPath("src/test/java")
        );

        List<String> violations = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(".java") || path.toString().endsWith(".json"))
                        .forEach(path -> collectForbiddenTermViolations(path, forbiddenTerms, violations));
            }
        }

        assertTrue(violations.isEmpty(), String.join(System.lineSeparator(), violations));
    }

    @Test
    void docsRecordCanonicalTermsAndAnatomicalDistinctions() throws IOException {
        String decisions = Files.readString(TestProjectPaths.projectPath("DECISIONS.md"));
        String definitions = Files.readString(TestProjectPaths.projectPath("docs/PRODUCT_AND_PROCESSING_DEFINITIONS.md"));
        String multiOutput = Files.readString(TestProjectPaths.projectPath("docs/MULTI_OUTPUT_PROCESSING.md"));

        assertTrue(decisions.contains("Midwestern Butcher-Cut Terminology Is Canonical"));
        assertTrue(decisions.contains("Kansas City Strip Steak"));
        assertTrue(decisions.contains("Picanha"));
        assertTrue(decisions.contains("Prime Rib"));
        assertTrue(decisions.contains("Denver Steak"));
        assertTrue(definitions.contains("Do not blanket-rename broader anatomical products"));
        assertTrue(multiOutput.contains("butchercraft:beef_packer_brisket"));
    }

    private static void collectForbiddenTermViolations(Path path, List<String> forbiddenTerms, List<String> violations) {
        try {
            String text = Files.readString(path);
            for (String forbiddenTerm : forbiddenTerms) {
                if (text.contains(forbiddenTerm)) {
                    violations.add(path + " contains retired term: " + forbiddenTerm);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static ResourceLocation retiredGenericBrisketId() {
        return BuiltInDefinitionIds.id("beef_" + "brisket");
    }

    private static String retiredGenericBrisketProductPath() {
        return "src/generated/resources/data/butchercraft/butchercraft/product/beef_" + "brisket.json";
    }

    private static String retiredGenericBrisketItemModelPath() {
        return "src/generated/resources/assets/butchercraft/models/item/beef_" + "brisket_test.json";
    }
}
