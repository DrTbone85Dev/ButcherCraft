package com.butchercraft.transformation;

import com.butchercraft.engine.EngineId;
import com.butchercraft.engine.operation.ProcessingDuration;
import com.butchercraft.engine.operation.YieldRatio;
import com.butchercraft.engine.quantity.ProductQuantity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformationTransactionTest {
    private static final EngineId BEEF_FOREQUARTER = EngineId.of("butchercraft:beef_forequarter");
    private static final EngineId BEEF_CHUCK = EngineId.of("butchercraft:beef_chuck");
    private static final EngineId BEEF_RIB = EngineId.of("butchercraft:beef_rib");
    private static final EngineId BEEF_TRIM = EngineId.of("butchercraft:beef_trim");
    private static final EngineId GROUND_BEEF = EngineId.of("butchercraft:ground_beef");
    private static final EngineId GRINDING = EngineId.of("butchercraft:grinding");
    private static final EngineId BANDSAW = EngineId.of("butchercraft:bandsaw");

    @Test
    void transactionCommitsMultipleOutputsAtomicallyInDefinitionOrder() {
        TransformationDefinition definition = fabricateForequarter();
        InMemoryTransformationMaterialStore inputStore = InMemoryTransformationMaterialStore.builder()
                .material(BEEF_FOREQUARTER, ProductQuantity.grams(1_000))
                .build();
        InMemoryTransformationMaterialStore outputStore = InMemoryTransformationMaterialStore.builder()
                .materialSlotCapacity(3)
                .build();
        TransformationContext context = inputStore.toContext(Optional.of(capability(BANDSAW)));
        TransformationEvaluation evaluation = TransformationEvaluator.evaluate(definition, context);

        TransformationExecution execution = TransformationExecutor.execute(
                definition,
                context,
                evaluation,
                inputStore,
                outputStore
        );

        assertTrue(execution.succeeded());
        assertEquals(TransformationExecutionCode.EXECUTED, execution.code());
        assertTrue(inputStore.quantity(BEEF_FOREQUARTER).isEmpty());
        assertEquals(List.of(BEEF_CHUCK, BEEF_RIB, BEEF_TRIM), outputStore.materials().stream()
                .map(MaterialAmount::materialId)
                .toList());
        assertEquals(List.of(300L, 200L, 450L), outputStore.materials().stream()
                .map(material -> material.quantity().amount())
                .toList());
    }

    @Test
    void capacityFailureRejectsWithoutChangingInputOrProducingPartialOutputs() {
        TransformationDefinition definition = fabricateForequarter();
        InMemoryTransformationMaterialStore inputStore = InMemoryTransformationMaterialStore.builder()
                .material(BEEF_FOREQUARTER, ProductQuantity.grams(1_000))
                .build();
        InMemoryTransformationMaterialStore outputStore = InMemoryTransformationMaterialStore.builder()
                .materialSlotCapacity(3)
                .capacity(BEEF_CHUCK, ProductQuantity.grams(300))
                .capacity(BEEF_RIB, ProductQuantity.grams(100))
                .capacity(BEEF_TRIM, ProductQuantity.grams(450))
                .build();
        TransformationContext context = inputStore.toContext(Optional.of(capability(BANDSAW)));
        TransformationEvaluation evaluation = TransformationEvaluator.evaluate(definition, context);

        TransformationExecution execution = TransformationExecutor.execute(
                definition,
                context,
                evaluation,
                inputStore,
                outputStore
        );

        assertEquals(TransformationExecutionCode.OUTPUT_REJECTED, execution.code());
        assertEquals(ProductQuantity.grams(1_000), inputStore.quantity(BEEF_FOREQUARTER).orElseThrow());
        assertTrue(outputStore.materials().isEmpty());
    }

    @Test
    void preparedTransactionRevalidatesCapacityBeforeCommit() {
        TransformationDefinition definition = fabricateForequarter();
        InMemoryTransformationMaterialStore inputStore = InMemoryTransformationMaterialStore.builder()
                .material(BEEF_FOREQUARTER, ProductQuantity.grams(1_000))
                .build();
        InMemoryTransformationMaterialStore outputStore = InMemoryTransformationMaterialStore.builder()
                .materialSlotCapacity(3)
                .build();
        TransformationContext context = inputStore.toContext(Optional.of(capability(BANDSAW)));
        TransformationTransaction transaction = TransformationTransaction.create(
                definition,
                context,
                TransformationEvaluator.evaluate(definition, context),
                inputStore,
                outputStore
        );

        TransformationExecution prepared = transaction.prepare();
        outputStore.insert(MaterialAmount.grams("butchercraft:blocking_output", 1));
        TransformationExecution committed = transaction.commit();

        assertTrue(prepared.succeeded());
        assertEquals(TransformationExecutionCode.OUTPUT_REJECTED, committed.code());
        assertEquals(TransformationTransactionState.REJECTED, transaction.state());
        assertEquals(ProductQuantity.grams(1_000), inputStore.quantity(BEEF_FOREQUARTER).orElseThrow());
        assertEquals(List.of(EngineId.of("butchercraft:blocking_output")), outputStore.materials().stream()
                .map(MaterialAmount::materialId)
                .toList());
    }

    @Test
    void unexpectedInsertionFailureRollsBackInputAndAlreadyInsertedOutputs() {
        TransformationDefinition definition = fabricateForequarter();
        InMemoryTransformationMaterialStore inputStore = InMemoryTransformationMaterialStore.builder()
                .material(BEEF_FOREQUARTER, ProductQuantity.grams(1_000))
                .build();
        FailingInsertStore outputStore = new FailingInsertStore(InMemoryTransformationMaterialStore.builder()
                .materialSlotCapacity(3)
                .build());
        TransformationContext context = inputStore.toContext(Optional.of(capability(BANDSAW)));
        TransformationTransaction transaction = TransformationTransaction.create(
                definition,
                context,
                TransformationEvaluator.evaluate(definition, context),
                inputStore,
                outputStore
        );

        assertTrue(transaction.prepare().succeeded());
        outputStore.failOnSecondInsert();
        TransformationExecution committed = transaction.commit();

        assertEquals(TransformationExecutionCode.TRANSACTION_ROLLED_BACK, committed.code());
        assertEquals(TransformationTransactionState.ROLLED_BACK, transaction.state());
        assertEquals(ProductQuantity.grams(1_000), inputStore.quantity(BEEF_FOREQUARTER).orElseThrow());
        assertTrue(outputStore.materials().isEmpty());
    }

    @Test
    void oneInputOneOutputGrinderCompatibilityIsPreserved() {
        TransformationDefinition definition = BuiltInTransformationRegistry.builtInRegistry()
                .find(TransformationId.of("butchercraft:grind_beef"))
                .orElseThrow()
                .withInputQuantity(ProductQuantity.grams(1_000));
        InMemoryTransformationMaterialStore inputStore = InMemoryTransformationMaterialStore.builder()
                .material(BEEF_TRIM, ProductQuantity.grams(1_000))
                .build();
        InMemoryTransformationMaterialStore outputStore = InMemoryTransformationMaterialStore.builder()
                .materialSlotCapacity(1)
                .build();
        TransformationContext context = inputStore.toContext(Optional.of(capability(GRINDING)));
        TransformationEvaluation evaluation = TransformationEvaluator.evaluate(definition, context);

        TransformationExecution sideEffectFreeExecution = TransformationExecutor.execute(definition, context, evaluation);
        TransformationExecution committedExecution = TransformationExecutor.execute(
                definition,
                context,
                evaluation,
                inputStore,
                outputStore
        );

        assertTrue(sideEffectFreeExecution.succeeded());
        assertTrue(committedExecution.succeeded());
        assertEquals(List.of(GROUND_BEEF), committedExecution.outputs().stream()
                .map(output -> output.producedAmount().materialId())
                .toList());
        assertTrue(inputStore.quantity(BEEF_TRIM).isEmpty());
        assertEquals(ProductQuantity.grams(900), outputStore.quantity(GROUND_BEEF).orElseThrow());
    }

    @Test
    void storeSnapshotsAreImmutableAndRestoreQuantitiesInOrder() {
        InMemoryTransformationMaterialStore store = InMemoryTransformationMaterialStore.builder()
                .material(BEEF_CHUCK, ProductQuantity.grams(300))
                .material(BEEF_RIB, ProductQuantity.grams(200))
                .build();

        TransformationMaterialStoreSnapshot snapshot = store.snapshot();
        store.extract(new MaterialAmount(BEEF_CHUCK, ProductQuantity.grams(300)));
        store.insert(new MaterialAmount(BEEF_TRIM, ProductQuantity.grams(450)));
        store.restore(snapshot);

        assertEquals(List.of(BEEF_CHUCK, BEEF_RIB), store.materials().stream()
                .map(MaterialAmount::materialId)
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.materials().clear());
    }

    private static TransformationDefinition fabricateForequarter() {
        return TransformationDefinition.builder()
                .id("butchercraft:break_test_forequarter")
                .displayName("Break Test Forequarter")
                .schemaVersion(TransformationDefinition.CURRENT_SCHEMA_VERSION)
                .requiredCapability(BANDSAW)
                .input(BEEF_FOREQUARTER, ProductQuantity.grams(1_000))
                .output(BEEF_CHUCK, ProductQuantity.grams(300), TransformationOutputClassification.PRIMARY)
                .output(BEEF_RIB, ProductQuantity.grams(200), TransformationOutputClassification.PRIMARY)
                .output(BEEF_TRIM, ProductQuantity.grams(450), TransformationOutputClassification.BYPRODUCT)
                .duration(ProcessingDuration.milliseconds(6_000))
                .yield(new YieldRatio(19, 20))
                .metadata("butchercraft:schema/source", "test")
                .build();
    }

    private static WorkstationCapability capability(EngineId advertisedCapability) {
        return new WorkstationCapability(EngineId.of("butchercraft:test_workstation"), java.util.Set.of(advertisedCapability));
    }

    private static final class FailingInsertStore implements TransformationMaterialStore {
        private final TransformationMaterialStore delegate;
        private boolean failOnSecondInsert;
        private int insertAttempts;

        private FailingInsertStore(TransformationMaterialStore delegate) {
            this.delegate = delegate;
        }

        private void failOnSecondInsert() {
            failOnSecondInsert = true;
            insertAttempts = 0;
        }

        @Override
        public Optional<ProductQuantity> quantity(EngineId materialId) {
            return delegate.quantity(materialId);
        }

        @Override
        public List<MaterialAmount> materials() {
            return delegate.materials();
        }

        @Override
        public boolean canExtract(MaterialAmount amount) {
            return delegate.canExtract(amount);
        }

        @Override
        public boolean canInsert(MaterialAmount amount) {
            return delegate.canInsert(amount);
        }

        @Override
        public boolean canExtractAll(List<MaterialAmount> amounts) {
            return delegate.canExtractAll(amounts);
        }

        @Override
        public boolean canInsertAll(List<MaterialAmount> amounts) {
            return true;
        }

        @Override
        public TransformationMaterialStoreSnapshot snapshot() {
            return delegate.snapshot();
        }

        @Override
        public void restore(TransformationMaterialStoreSnapshot snapshot) {
            delegate.restore(snapshot);
        }

        @Override
        public void extract(MaterialAmount amount) {
            delegate.extract(amount);
        }

        @Override
        public void insert(MaterialAmount amount) {
            insertAttempts++;
            if (failOnSecondInsert && insertAttempts == 2) {
                throw new IllegalStateException("Injected output insertion failure");
            }
            delegate.insert(amount);
        }
    }
}
