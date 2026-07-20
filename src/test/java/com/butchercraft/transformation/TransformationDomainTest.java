package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.ProcessingOperation;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.product.ProcessingState;
import com.butchercraft.engine.product.ProductCategory;
import com.butchercraft.engine.quantity.ProductQuantity;
import com.butchercraft.engine.validation.ValidationRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationDomainTest {
    private static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    private static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");
    private static final EngineId BEEF_FAT = EngineId.of("butchercraft:beef_fat");
    private static final EngineId GRINDING = EngineId.of("butchercraft:grinding");
    private static final EngineId BANDSAW = EngineId.of("butchercraft:bandsaw");

    @Test
    void transformationIdentifiersUseExistingEngineIdValidation() {
        TransformationId id = TransformationId.of("butchercraft:grind_beef");

        assertEquals("butchercraft:grind_beef", id.value());
        assertThrows(NullPointerException.class, () -> new TransformationId(null));
        assertThrows(NullPointerException.class, () -> TransformationId.of(null));
        assertThrows(IllegalArgumentException.class, () -> TransformationId.of("ButcherCraft:Grind Beef"));
    }

    @Test
    void materialAmountsMustBePositiveAndBoundedForExactArithmetic() {
        assertEquals(ProductQuantity.grams(100), MaterialAmount.grams("butchercraft:beef_trim", 100).quantity());

        assertThrows(IllegalArgumentException.class, () -> MaterialAmount.grams("butchercraft:beef_trim", 0));
        assertThrows(IllegalArgumentException.class, () -> ProductQuantity.grams(-1));
        assertThrows(IllegalArgumentException.class, () -> MaterialAmount.grams(
                "butchercraft:beef_trim",
                MaterialAmount.MAX_SAFE_AMOUNT + 1
        ));
    }

    @Test
    void definitionsRejectEmptyDuplicateAndContradictoryEntries() {
        TransformationInput input = input(BEEF_TRIM, 1_000);
        TransformationOutput output = output(GROUND_BEEF, 900, TransformationOutputClassification.PRIMARY);

        assertThrows(IllegalArgumentException.class, () -> new TransformationDefinition(
                TransformationId.of("butchercraft:grind_beef"),
                List.of(),
                List.of(output),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(GRINDING)
        ));
        assertThrows(IllegalArgumentException.class, () -> new TransformationDefinition(
                TransformationId.of("butchercraft:grind_beef"),
                List.of(input),
                List.of(),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(GRINDING)
        ));
        assertThrows(IllegalArgumentException.class, () -> new TransformationDefinition(
                TransformationId.of("butchercraft:grind_beef"),
                List.of(input, input(BEEF_TRIM, 500)),
                List.of(output),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(GRINDING)
        ));
        assertThrows(IllegalArgumentException.class, () -> new TransformationDefinition(
                TransformationId.of("butchercraft:grind_beef"),
                List.of(input),
                List.of(output, output(GROUND_BEEF, 10, TransformationOutputClassification.BYPRODUCT)),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(GRINDING)
        ));
        assertThrows(IllegalArgumentException.class, () -> ProcessingDuration.milliseconds(0));
    }

    @Test
    void definitionsDefensivelyCopyCollectionsAndPreserveOrder() {
        List<TransformationInput> inputs = new ArrayList<>(List.of(
                input(BEEF_TRIM, 1_000),
                input(BEEF_FAT, 50)
        ));
        List<TransformationOutput> outputs = new ArrayList<>(List.of(
                output(GROUND_BEEF, 900, TransformationOutputClassification.PRIMARY),
                output(BEEF_FAT, 25, TransformationOutputClassification.BYPRODUCT)
        ));

        TransformationDefinition definition = new TransformationDefinition(
                TransformationId.of("butchercraft:trim_with_fat"),
                inputs,
                outputs,
                ProcessingDuration.milliseconds(3_000),
                Optional.of(GRINDING)
        );
        inputs.clear();
        outputs.clear();

        assertEquals(List.of(BEEF_TRIM, BEEF_FAT), definition.inputs().stream()
                .map(input -> input.requiredAmount().materialId())
                .toList());
        assertEquals(List.of(GROUND_BEEF, BEEF_FAT), definition.outputs().stream()
                .map(output -> output.producedAmount().materialId())
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> definition.inputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> definition.outputs().clear());
    }

    @Test
    void evaluatorAcceptsValidTransformationWithoutMutatingContext() {
        TransformationDefinition definition = grindDefinition();
        TransformationContext context = new TransformationContext(
                List.of(MaterialAmount.grams("butchercraft:beef_trim", 1_000)),
                Optional.of(capability(GRINDING))
        );

        TransformationEvaluation evaluation = TransformationEvaluator.evaluate(definition, context);

        assertTrue(evaluation.acceptedResult());
        assertEquals(TransformationEvaluationCode.ACCEPTED, evaluation.code());
        assertEquals(Optional.of(definition.id()), evaluation.transformationId());
        assertEquals(1_000, context.availableMaterials().getFirst().quantity().amount());
    }

    @Test
    void evaluatorReportsMissingAndInsufficientInputsInDefinitionOrder() {
        TransformationDefinition definition = new TransformationDefinition(
                TransformationId.of("butchercraft:ordered_check"),
                List.of(input(BEEF_TRIM, 1_000), input(BEEF_FAT, 50)),
                List.of(output(GROUND_BEEF, 900, TransformationOutputClassification.PRIMARY)),
                ProcessingDuration.milliseconds(3_000),
                Optional.empty()
        );

        TransformationEvaluation missing = TransformationEvaluator.evaluate(definition, new TransformationContext(
                List.of(),
                Optional.empty()
        ));
        TransformationEvaluation insufficient = TransformationEvaluator.evaluate(definition, new TransformationContext(
                List.of(MaterialAmount.grams("butchercraft:beef_trim", 999)),
                Optional.empty()
        ));
        TransformationEvaluation repeated = TransformationEvaluator.evaluate(definition, new TransformationContext(
                List.of(MaterialAmount.grams("butchercraft:beef_trim", 999)),
                Optional.empty()
        ));

        assertFalse(missing.acceptedResult());
        assertEquals(TransformationEvaluationCode.MISSING_INPUT, missing.code());
        assertTrue(missing.message().contains("butchercraft:beef_trim"));
        assertEquals(TransformationEvaluationCode.INSUFFICIENT_INPUT, insufficient.code());
        assertEquals(insufficient, repeated);
    }

    @Test
    void evaluatorReportsUnsupportedCapabilityBeforeMaterialChecks() {
        TransformationEvaluation missingCapability = TransformationEvaluator.evaluate(grindDefinition(), new TransformationContext(
                List.of(MaterialAmount.grams("butchercraft:beef_trim", 1_000)),
                Optional.empty()
        ));
        TransformationEvaluation wrongCapability = TransformationEvaluator.evaluate(grindDefinition(), new TransformationContext(
                List.of(),
                Optional.of(capability(BANDSAW))
        ));

        assertEquals(TransformationEvaluationCode.UNSUPPORTED_CAPABILITY, missingCapability.code());
        assertEquals(TransformationEvaluationCode.UNSUPPORTED_CAPABILITY, wrongCapability.code());
    }

    @Test
    void evaluatorRejectsInvalidContextInsteadOfThrowing() {
        assertEquals(
                TransformationEvaluationCode.INVALID_CONTEXT,
                TransformationEvaluator.evaluate(null, new TransformationContext(List.of(), Optional.empty())).code()
        );
        assertEquals(
                TransformationEvaluationCode.INVALID_CONTEXT,
                TransformationEvaluator.evaluate(grindDefinition(), null).code()
        );
    }

    @Test
    void workstationCapabilityAdvertisesSupportedCapabilities() {
        WorkstationCapability capability = new WorkstationCapability(
                EngineId.of("butchercraft:grinder"),
                java.util.Set.of(GRINDING)
        );

        assertTrue(capability.advertises(GRINDING));
        assertFalse(capability.advertises(BANDSAW));
        assertThrows(NullPointerException.class, () -> new WorkstationCapability(null, java.util.Set.of(GRINDING)));
        assertThrows(NullPointerException.class, () -> new WorkstationCapability(EngineId.of("butchercraft:grinder"), null));
    }

    @Test
    void executorRequiresAcceptedMatchingEvaluationBeforeProducingOutputs() {
        TransformationDefinition definition = grindDefinition();
        TransformationContext context = new TransformationContext(
                List.of(MaterialAmount.grams("butchercraft:beef_trim", 1_000)),
                Optional.of(capability(GRINDING))
        );
        TransformationEvaluation accepted = TransformationEvaluator.evaluate(definition, context);

        TransformationExecution executed = TransformationExecutor.execute(definition, context, accepted);
        TransformationExecution rejectedEvaluation = TransformationExecutor.execute(
                definition,
                context,
                TransformationEvaluation.rejected(TransformationEvaluationCode.MISSING_INPUT, "Missing input")
        );
        TransformationExecution staleEvaluation = TransformationExecutor.execute(
                definition,
                new TransformationContext(List.of(MaterialAmount.grams("butchercraft:beef_trim", 500)), Optional.of(capability(GRINDING))),
                accepted
        );
        TransformationExecution wrongTransformation = TransformationExecutor.execute(
                new TransformationDefinition(
                        TransformationId.of("butchercraft:other"),
                        definition.inputs(),
                        definition.outputs(),
                        definition.duration(),
                        definition.workstationCapability()
                ),
                context,
                accepted
        );

        assertTrue(executed.succeeded());
        assertEquals(TransformationExecutionCode.EXECUTED, executed.code());
        assertEquals(List.of(GROUND_BEEF), executed.outputs().stream()
                .map(output -> output.producedAmount().materialId())
                .toList());
        assertEquals(TransformationExecutionCode.EVALUATION_NOT_ACCEPTED, rejectedEvaluation.code());
        assertEquals(TransformationExecutionCode.EVALUATION_MISMATCH, staleEvaluation.code());
        assertEquals(TransformationExecutionCode.EVALUATION_MISMATCH, wrongTransformation.code());
    }

    @Test
    void processingOperationAdapterCreatesCompatibleTransformationDefinition() {
        ProcessingOperation operation = new ProcessingOperation(
                EngineId.of("butchercraft:grind_beef"),
                "Grind Beef",
                BEEF_TRIM,
                Optional.of(ProductCategory.BEEF),
                ProcessingState.RAW,
                GROUND_BEEF,
                ProcessingState.PREPARED,
                ProcessingDuration.milliseconds(3_000),
                new YieldRatio(9, 10),
                -5,
                List.of(
                        ValidationRules.requiredProductType(),
                        ValidationRules.requiredProcessingState()
                ),
                List.of(),
                false
        );

        TransformationDefinition definition = ProcessingOperationTransformationAdapter.fromProcessingOperation(
                operation,
                ProductQuantity.grams(1_000),
                Optional.of(GRINDING)
        );

        assertEquals("butchercraft:grind_beef", definition.id().value());
        assertEquals(List.of(BEEF_TRIM), definition.inputs().stream()
                .map(input -> input.requiredAmount().materialId())
                .toList());
        assertEquals(List.of(GROUND_BEEF), definition.outputs().stream()
                .map(output -> output.producedAmount().materialId())
                .toList());
        assertEquals(900, definition.outputs().getFirst().producedAmount().quantity().amount());
        assertEquals(TransformationOutputClassification.PRIMARY, definition.outputs().getFirst().classification());
        assertEquals(Optional.of(GRINDING), definition.workstationCapability());
    }

    private static TransformationDefinition grindDefinition() {
        return new TransformationDefinition(
                TransformationId.of("butchercraft:grind_beef"),
                List.of(input(BEEF_TRIM, 1_000)),
                List.of(output(GROUND_BEEF, 900, TransformationOutputClassification.PRIMARY)),
                ProcessingDuration.milliseconds(3_000),
                Optional.of(GRINDING)
        );
    }

    private static TransformationInput input(EngineId materialId, long grams) {
        return new TransformationInput(new MaterialAmount(materialId, ProductQuantity.grams(grams)));
    }

    private static TransformationOutput output(
            EngineId materialId,
            long grams,
            TransformationOutputClassification classification
    ) {
        return new TransformationOutput(new MaterialAmount(materialId, ProductQuantity.grams(grams)), classification);
    }

    private static WorkstationCapability capability(EngineId advertisedCapability) {
        return new WorkstationCapability(EngineId.of("butchercraft:test_workstation"), java.util.Set.of(advertisedCapability));
    }
}
