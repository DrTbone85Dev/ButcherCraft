package com.butchercraft.processing.definition;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessingGraphTest {
    @Test
    void builtInGraphContainsBeefTrimToGroundBeefEdge() {
        ProcessingGraph graph = ProcessingGraph.fromDefinitions(DefinitionTestFixtures.builtIns());

        assertFalse(graph.validationReport().hasErrors());
        assertTrue(graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_TRIM, BuiltInDefinitionIds.GROUND_BEEF));
        assertEquals(List.of(BuiltInDefinitionIds.GROUND_BEEF), graph.outputsReachableThroughOneOperation(BuiltInDefinitionIds.BEEF_TRIM));
        assertTrue(graph.hasDirectTransformation(BuiltInDefinitionIds.PORK_TRIM, BuiltInDefinitionIds.GROUND_PORK));
        assertEquals(List.of(BuiltInDefinitionIds.GROUND_PORK), graph.outputsReachableThroughOneOperation(BuiltInDefinitionIds.PORK_TRIM));
        assertTrue(graph.hasDirectTransformation(BuiltInDefinitionIds.BISON_TRIM, BuiltInDefinitionIds.GROUND_BISON));
        assertEquals(List.of(BuiltInDefinitionIds.GROUND_BISON), graph.outputsReachableThroughOneOperation(BuiltInDefinitionIds.BISON_TRIM));
        assertEquals(List.of(BuiltInDefinitionIds.BREAK_BEEF_FOREQUARTER), graph.operationsAvailableFor(BuiltInDefinitionIds.BEEF_FOREQUARTER).stream()
                .map(ProcessingGraphEdge::operationId)
                .toList());
        assertTrue(graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_FOREQUARTER, BuiltInDefinitionIds.BEEF_CHUCK));
        assertTrue(graph.hasDirectTransformation(BuiltInDefinitionIds.BEEF_FOREQUARTER, BuiltInDefinitionIds.BEEF_BONE));
        assertEquals(List.of(
                        BuiltInDefinitionIds.BEEF_BONE,
                        BuiltInDefinitionIds.BEEF_CHUCK,
                        BuiltInDefinitionIds.BEEF_FAT,
                        BuiltInDefinitionIds.BEEF_PACKER_BRISKET,
                        BuiltInDefinitionIds.BEEF_PLATE,
                        BuiltInDefinitionIds.BEEF_RIB,
                        BuiltInDefinitionIds.BEEF_SHANK,
                        BuiltInDefinitionIds.BEEF_TRIM
                ),
                graph.outputsReachableThroughOneOperation(BuiltInDefinitionIds.BEEF_FOREQUARTER));
    }

    @Test
    void operationLookupIsDeterministicallySorted() {
        ResourceLocation later = DefinitionTestFixtures.id("z_later_operation");
        ResourceLocation earlier = DefinitionTestFixtures.id("a_earlier_operation");
        ProcessingOperationDefinition operation = BuiltInProcessingDefinitions.grindBeefOperation();
        DefinitionRegistryView view = DefinitionTestFixtures.builtIns();

        ProcessingGraph graph = ProcessingGraph.fromOperationEntries(view, List.of(
                new OperationDefinitionEntry(later, operation),
                new OperationDefinitionEntry(earlier, operation)
        ));

        assertEquals(
                List.of(earlier, later),
                graph.operationsAvailableFor(BuiltInDefinitionIds.BEEF_TRIM).stream()
                        .map(ProcessingGraphEdge::operationId)
                        .toList()
        );
    }

    @Test
    void unknownInputLookupReturnsEmptyList() {
        ProcessingGraph graph = ProcessingGraph.fromDefinitions(DefinitionTestFixtures.builtIns());

        assertTrue(graph.operationsAvailableFor(DefinitionTestFixtures.id("unknown_product")).isEmpty());
    }

    @Test
    void graphIsReadOnlyAfterConstruction() {
        ProcessingGraph graph = ProcessingGraph.fromDefinitions(DefinitionTestFixtures.builtIns());

        assertThrows(UnsupportedOperationException.class, () -> graph.edgesByInput().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.operationsAvailableFor(BuiltInDefinitionIds.BEEF_TRIM).clear());
    }

    @Test
    void selfLoopIsRejectedUnlessExplicitlyAllowed() {
        ProcessingOperationDefinition selfLoop = new ProcessingOperationDefinition(
                "definition.test.self_loop",
                BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.id("trim"),
                BuiltInDefinitionIds.id("trim"),
                1_000,
                new YieldDefinition(1, 1),
                0,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(),
                java.util.Optional.empty(),
                false,
                false
        );

        ProcessingGraph graph = ProcessingGraph.fromOperationEntries(
                DefinitionTestFixtures.builtIns(),
                List.of(new OperationDefinitionEntry(DefinitionTestFixtures.id("self_loop"), selfLoop))
        );

        assertTrue(graph.validationReport().hasErrors());
        assertTrue(graph.validationReport().issues().stream().anyMatch(issue -> issue.reasonCode().equals("self_loop_not_permitted")));
    }

    @Test
    void unresolvedReferenceIsReported() {
        ProcessingOperationDefinition unresolved = DefinitionTestFixtures.operation(
                DefinitionTestFixtures.id("missing_input"),
                BuiltInDefinitionIds.GROUND_BEEF,
                List.of(BuiltInDefinitionIds.RED_MEAT)
        );

        ProcessingGraph graph = ProcessingGraph.fromOperationEntries(
                DefinitionTestFixtures.builtIns(),
                List.of(new OperationDefinitionEntry(DefinitionTestFixtures.id("unresolved"), unresolved))
        );

        assertTrue(graph.validationReport().hasErrors());
        assertTrue(graph.validationReport().issues().stream().anyMatch(issue -> issue.reasonCode().equals("missing_input_product")));
    }

    @Test
    void profileIncompatibilityIsReported() {
        ProcessingOperationDefinition incompatible = DefinitionTestFixtures.operation(
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.GROUND_BEEF,
                List.of(DefinitionTestFixtures.id("other_profile"))
        );

        ProcessingGraph graph = ProcessingGraph.fromOperationEntries(
                DefinitionTestFixtures.builtIns(),
                List.of(new OperationDefinitionEntry(DefinitionTestFixtures.id("incompatible"), incompatible))
        );

        assertTrue(graph.validationReport().hasErrors());
        assertTrue(graph.validationReport().issues().stream().anyMatch(issue -> issue.reasonCode().equals("profile_mismatch")));
    }

    @Test
    void stateTransitionMismatchIsReported() {
        ProcessingOperationDefinition mismatch = new ProcessingOperationDefinition(
                "definition.test.state_mismatch",
                BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.GROUND_BEEF,
                BuiltInDefinitionIds.id("ground"),
                BuiltInDefinitionIds.id("trim"),
                1_000,
                new YieldDefinition(1, 1),
                0,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(),
                java.util.Optional.empty(),
                false,
                false
        );

        ProcessingGraph graph = ProcessingGraph.fromOperationEntries(
                DefinitionTestFixtures.builtIns(),
                List.of(new OperationDefinitionEntry(DefinitionTestFixtures.id("state_mismatch"), mismatch))
        );

        assertTrue(graph.validationReport().hasErrors());
        assertTrue(graph.validationReport().issues().stream().anyMatch(issue -> issue.reasonCode().equals("input_state_mismatch")));
        assertTrue(graph.validationReport().issues().stream().anyMatch(issue -> issue.reasonCode().equals("output_state_mismatch")));
    }

    @Test
    void duplicateConflictingOperationIdsAreReported() {
        ProcessingOperationDefinition first = BuiltInProcessingDefinitions.grindBeefOperation();
        ProcessingOperationDefinition second = new ProcessingOperationDefinition(
                first.displayNameKey(),
                first.operationCategory(),
                first.requiredProcessingProfiles(),
                first.inputProduct(),
                first.outputProduct(),
                first.requiredInputProcessingState(),
                first.outputProcessingState(),
                first.baseDurationMilliseconds() + 1,
                first.baseYield(),
                first.baseQualityDelta(),
                first.minimumInputQuantity(),
                first.minimumCleanlinessFactor(),
                first.minimumEquipmentConditionFactor(),
                first.zeroOutputPolicy(),
                first.staticModifiers(),
                first.workstationCapability(),
                first.selfLoopPermitted(),
                first.crossSpeciesPermitted()
        );

        ProcessingGraph graph = ProcessingGraph.fromOperationEntries(
                DefinitionTestFixtures.builtIns(),
                List.of(
                        new OperationDefinitionEntry(DefinitionTestFixtures.id("duplicate"), first),
                        new OperationDefinitionEntry(DefinitionTestFixtures.id("duplicate"), second)
                )
        );

        assertTrue(graph.validationReport().hasErrors());
        assertTrue(graph.validationReport().issues().stream().anyMatch(issue -> issue.reasonCode().equals("duplicate_operation_id")));
    }

    @Test
    void cyclesAreWarningsButNotUniversalErrors() {
        DefinitionRegistryView base = DefinitionTestFixtures.builtIns();
        Map<ResourceLocation, ProcessingOperationDefinition> operations = new LinkedHashMap<>();
        operations.put(BuiltInDefinitionIds.GRIND_BEEF, BuiltInProcessingDefinitions.grindBeefOperation());
        operations.put(DefinitionTestFixtures.id("rework_ground"), new ProcessingOperationDefinition(
                "definition.test.rework_ground",
                BuiltInDefinitionIds.OPERATION_CATEGORY_GRINDING,
                List.of(BuiltInDefinitionIds.RED_MEAT),
                BuiltInDefinitionIds.GROUND_BEEF,
                BuiltInDefinitionIds.BEEF_TRIM,
                BuiltInDefinitionIds.id("ground"),
                BuiltInDefinitionIds.id("trim"),
                1_000,
                new YieldDefinition(1, 1),
                0,
                new QuantityDefinition(100, "gram"),
                600,
                500,
                ZeroOutputPolicy.FORBID,
                List.of(),
                java.util.Optional.empty(),
                false,
                false
        ));
        DefinitionRegistryView view = new DefinitionRegistryView(base.species(), base.processingProfiles(), base.products(), operations);

        ProcessingGraph graph = ProcessingGraph.fromDefinitions(view);

        assertFalse(graph.validationReport().hasErrors());
        assertTrue(graph.validationReport().hasWarnings());
        assertTrue(graph.validationReport().issues().stream().anyMatch(issue -> issue.reasonCode().equals("cycle_detected")));
    }
}
