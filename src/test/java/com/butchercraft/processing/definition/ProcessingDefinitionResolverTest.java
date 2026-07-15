package com.butchercraft.processing.definition;

import com.butchercraft.engine.operation.ProcessingOperation;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingDefinitionResolverTest {
    @Test
    void resolvesValidSpeciesAndProfile() {
        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(DefinitionTestFixtures.builtIns());

        assertTrue(resolver.resolveSpecies(BuiltInDefinitionIds.BEEF).succeeded());
        assertTrue(resolver.resolveProcessingProfile(BuiltInDefinitionIds.RED_MEAT).succeeded());
    }

    @Test
    void missingProfileIsExplicitFailure() {
        DefinitionRegistryView base = DefinitionTestFixtures.builtIns();
        DefinitionRegistryView view = new DefinitionRegistryView(
                Map.of(BuiltInDefinitionIds.BEEF, new SpeciesDefinition(
                        "definition.test",
                        DefinitionTestFixtures.id("missing_profile"),
                        BuiltInDefinitionIds.BEEF,
                        true,
                        true,
                        List.of()
                )),
                Map.of(),
                base.products(),
                base.operations()
        );

        DefinitionResolution<SpeciesDefinition> result = new ProcessingDefinitionResolver(view).resolveSpecies(BuiltInDefinitionIds.BEEF);

        assertFalse(result.succeeded());
        assertEquals("missing_processing_profile", result.report().issues().getFirst().reasonCode());
    }

    @Test
    void resolvesValidProductAndReportsMissingSpecies() {
        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(DefinitionTestFixtures.builtIns());
        assertTrue(resolver.resolveProduct(BuiltInDefinitionIds.BEEF_TRIM).succeeded());

        ProductDefinition invalid = new ProductDefinition(
                "definition.test",
                DefinitionTestFixtures.id("missing_species"),
                BuiltInDefinitionIds.BEEF,
                BuiltInDefinitionIds.id("trim"),
                "gram",
                true,
                BoneState.BONELESS,
                true,
                List.of(),
                true,
                false
        );
        DefinitionResolution<ProductDefinition> result =
                new ProcessingDefinitionResolver(DefinitionTestFixtures.withProduct(DefinitionTestFixtures.id("bad_product"), invalid))
                        .resolveProduct(DefinitionTestFixtures.id("bad_product"));

        assertFalse(result.succeeded());
        assertEquals("missing_species", result.report().issues().getFirst().reasonCode());
    }

    @Test
    void resolvesValidOperationAndConvertsToEngineOperation() {
        ProcessingDefinitionResolver resolver = new ProcessingDefinitionResolver(DefinitionTestFixtures.builtIns());

        DefinitionResolution<ResolvedProcessingOperationDefinition> resolved = resolver.resolveOperation(BuiltInDefinitionIds.GRIND_BEEF);
        DefinitionResolution<ProcessingOperation> engineOperation = resolver.toEngineOperation(BuiltInDefinitionIds.GRIND_BEEF);

        assertTrue(resolved.succeeded());
        assertTrue(engineOperation.succeeded());
        assertEquals("butchercraft:grind_beef", engineOperation.orThrow().id().value());
    }

    @Test
    void missingInputAndOutputProductsAreExplicitFailures() {
        ProcessingOperationDefinition invalid = DefinitionTestFixtures.operation(
                DefinitionTestFixtures.id("missing_input"),
                DefinitionTestFixtures.id("missing_output"),
                List.of(BuiltInDefinitionIds.RED_MEAT)
        );

        DefinitionResolution<ResolvedProcessingOperationDefinition> result =
                new ProcessingDefinitionResolver(DefinitionTestFixtures.withOperation(DefinitionTestFixtures.id("bad_operation"), invalid))
                        .resolveOperation(DefinitionTestFixtures.id("bad_operation"));

        assertFalse(result.succeeded());
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("missing_input_product")));
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("missing_output_product")));
    }

    @Test
    void speciesMismatchIsRejected() {
        DefinitionRegistryView base = DefinitionTestFixtures.withProfilesAndPoultryProducts();
        Map<ResourceLocation, ProcessingOperationDefinition> operations = new LinkedHashMap<>(base.operations());
        operations.put(DefinitionTestFixtures.id("mixed_species"), DefinitionTestFixtures.operation(
                BuiltInDefinitionIds.BEEF_TRIM,
                DefinitionTestFixtures.POULTRY_OUTPUT,
                List.of(BuiltInDefinitionIds.RED_MEAT)
        ));
        DefinitionRegistryView view = new DefinitionRegistryView(base.species(), base.processingProfiles(), base.products(), operations);

        DefinitionResolution<ResolvedProcessingOperationDefinition> result =
                new ProcessingDefinitionResolver(view).resolveOperation(DefinitionTestFixtures.id("mixed_species"));

        assertFalse(result.succeeded());
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("species_mismatch")));
    }

    @Test
    void profileMismatchIsRejected() {
        DefinitionRegistryView view = DefinitionTestFixtures.withOperation(
                DefinitionTestFixtures.id("profile_mismatch"),
                DefinitionTestFixtures.operation(
                        BuiltInDefinitionIds.BEEF_TRIM,
                        BuiltInDefinitionIds.GROUND_BEEF,
                        List.of(DefinitionTestFixtures.POULTRY_PROFILE)
                )
        );

        DefinitionResolution<ResolvedProcessingOperationDefinition> result =
                new ProcessingDefinitionResolver(view).resolveOperation(DefinitionTestFixtures.id("profile_mismatch"));

        assertFalse(result.succeeded());
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("missing_required_processing_profile")));
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("profile_mismatch")));
    }
}
