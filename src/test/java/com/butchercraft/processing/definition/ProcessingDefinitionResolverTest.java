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
        assertTrue(resolver.resolveSpecies(BuiltInDefinitionIds.PORK).succeeded());
        assertTrue(resolver.resolveSpecies(BuiltInDefinitionIds.BISON).succeeded());
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
        DefinitionResolution<ProcessingOperation> porkEngineOperation = resolver.toEngineOperation(BuiltInDefinitionIds.GRIND_PORK);
        DefinitionResolution<ProcessingOperation> bisonEngineOperation = resolver.toEngineOperation(BuiltInDefinitionIds.GRIND_BISON);
        DefinitionResolution<ProcessingOperation> bandsawEngineOperation =
                resolver.toEngineOperation(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER);
        DefinitionResolution<ProcessingOperation> hindquarterEngineOperation =
                resolver.toEngineOperation(BuiltInDefinitionIds.BREAK_BEEF_HINDQUARTER);
        DefinitionResolution<ProcessingOperation> shortLoinEngineOperation =
                resolver.toEngineOperation(BuiltInDefinitionIds.CUT_BEEF_SHORT_LOIN);
        DefinitionResolution<ProcessingOperation> roundEngineOperation =
                resolver.toEngineOperation(BuiltInDefinitionIds.CUT_BEEF_ROUND);
        DefinitionResolution<ProcessingOperation> sirloinEngineOperation =
                resolver.toEngineOperation(BuiltInDefinitionIds.CUT_BEEF_SIRLOIN);

        assertTrue(resolved.succeeded());
        assertTrue(engineOperation.succeeded());
        assertEquals("butchercraft:grind_beef", engineOperation.orThrow().id().value());
        assertTrue(porkEngineOperation.succeeded());
        assertEquals("butchercraft:grind_pork", porkEngineOperation.orThrow().id().value());
        assertTrue(bisonEngineOperation.succeeded());
        assertEquals("butchercraft:grind_bison", bisonEngineOperation.orThrow().id().value());
        assertTrue(bandsawEngineOperation.succeeded(), bandsawEngineOperation.report().issues().toString());
        assertEquals(8, bandsawEngineOperation.orThrow().outputs().size());
        assertTrue(hindquarterEngineOperation.succeeded(), hindquarterEngineOperation.report().issues().toString());
        assertEquals(7, hindquarterEngineOperation.orThrow().outputs().size());
        assertTrue(shortLoinEngineOperation.succeeded(), shortLoinEngineOperation.report().issues().toString());
        assertEquals(6, shortLoinEngineOperation.orThrow().outputs().size());
        assertTrue(roundEngineOperation.succeeded(), roundEngineOperation.report().issues().toString());
        assertEquals(7, roundEngineOperation.orThrow().outputs().size());
        assertTrue(sirloinEngineOperation.succeeded(), sirloinEngineOperation.report().issues().toString());
        assertEquals(6, sirloinEngineOperation.orThrow().outputs().size());
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

    @Test
    void duplicateOutputProductsAndExcessYieldAreRejected() {
        ProcessingOperationDefinition invalid = new ProcessingOperationDefinition(
                "definition.butchercraft_test.operation.invalid_multi_output",
                BuiltInDefinitionIds.OPERATION_CATEGORY_FABRICATION,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                BuiltInDefinitionIds.BEEF_FOREQUARTER,
                BuiltInDefinitionIds.id("forequarter"),
                1_000,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(
                        new ProcessingOutputDefinition(
                                BuiltInDefinitionIds.BEEF_CHUCK,
                                BuiltInDefinitionIds.id("primal"),
                                new YieldDefinition(60, 100),
                                0,
                                "gram",
                                false
                        ),
                        new ProcessingOutputDefinition(
                                BuiltInDefinitionIds.BEEF_CHUCK,
                                BuiltInDefinitionIds.id("primal"),
                                new YieldDefinition(60, 100),
                                0,
                                "gram",
                                false
                        )
                ),
                List.of(),
                java.util.Optional.empty(),
                false,
                false
        );

        DefinitionResolution<ResolvedProcessingOperationDefinition> result =
                new ProcessingDefinitionResolver(DefinitionTestFixtures.withOperation(DefinitionTestFixtures.id("bad_outputs"), invalid))
                        .resolveOperation(DefinitionTestFixtures.id("bad_outputs"));

        assertFalse(result.succeeded());
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("duplicate_output_product")));
        assertTrue(result.report().issues().stream().anyMatch(issue -> issue.reasonCode().equals("total_yield_too_high")));
    }
}
