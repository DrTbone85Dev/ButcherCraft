package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.butchercraft.test.TestProjectPaths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoultryProcessingProfileArchitectureTest {
    @Test
    void poultryProfileCanExistAsDataWithoutJavaWorkflowEnum() {
        DefinitionRegistryView view = DefinitionTestFixtures.withProfilesAndPoultryProducts();

        assertTrue(view.processingProfiles().containsKey(DefinitionTestFixtures.POULTRY_PROFILE));
        assertTrue(view.species().containsKey(DefinitionTestFixtures.POULTRY_SPECIES));
    }

    @Test
    void redMeatOperationIsRejectedForPoultryProfile() {
        DefinitionRegistryView base = DefinitionTestFixtures.withProfilesAndPoultryProducts();
        Map<ResourceLocation, ProcessingOperationDefinition> operations = new LinkedHashMap<>(base.operations());
        operations.put(DefinitionTestFixtures.id("red_meat_on_poultry"), DefinitionTestFixtures.operation(
                DefinitionTestFixtures.POULTRY_PRODUCT,
                DefinitionTestFixtures.POULTRY_OUTPUT,
                List.of(BuiltInDefinitionIds.RED_MEAT)
        ));
        DefinitionRegistryView view = new DefinitionRegistryView(base.species(), base.processingProfiles(), base.products(), operations);

        DefinitionResolution<ResolvedProcessingOperationDefinition> result =
                new ProcessingDefinitionResolver(view).resolveOperation(DefinitionTestFixtures.id("red_meat_on_poultry"));

        assertFalse(result.succeeded());
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("profile_mismatch")));
    }

    @Test
    void poultryOnlyOperationCanBeAcceptedForPoultryProfile() {
        DefinitionRegistryView base = DefinitionTestFixtures.withProfilesAndPoultryProducts();
        Map<ResourceLocation, ProcessingOperationDefinition> operations = new LinkedHashMap<>(base.operations());
        operations.put(DefinitionTestFixtures.POULTRY_OPERATION, DefinitionTestFixtures.poultryOperation());
        DefinitionRegistryView view = new DefinitionRegistryView(base.species(), base.processingProfiles(), base.products(), operations);

        DefinitionResolution<ResolvedProcessingOperationDefinition> result =
                new ProcessingDefinitionResolver(view).resolveOperation(DefinitionTestFixtures.POULTRY_OPERATION);

        assertTrue(result.succeeded(), result.report().issues().toString());
    }

    @Test
    void mainSourceDoesNotContainLiteralSpeciesSpecificWorkflowCheck() throws IOException {
        Path sourceRoot = TestProjectPaths.projectPath("src/main/java");
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(PoultryProcessingProfileArchitectureTest::containsLiteralChickenReference)
                    .toList();

            assertTrue(offenders.isEmpty(), "Main source should not hardcode a literal chicken species workflow: " + offenders);
        }
    }

    private static boolean containsLiteralChickenReference(Path path) {
        try {
            return Files.readString(path).toLowerCase(java.util.Locale.ROOT).contains("chicken");
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to scan " + path, exception);
        }
    }
}
