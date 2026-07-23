package com.butchercraft.world.production;

import com.butchercraft.world.production.scheduler.ProductionSimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.transaction.TransactionStatus;
import com.butchercraft.world.transaction.TransactionType;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.goods.UnitOfMeasure;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionExecutionTest {
    @Test
    void schedulerCompletesMultiOutputProductionThroughOneAppliedTransaction() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        SimulationSchedulerManager scheduler = scheduler(manager);
        SimulationPipeline pipeline = new SimulationPipeline(scheduler, SimulationExecutionBudget.standard());

        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());
        pipeline.execute(1L);
        pipeline.execute(2L);
        pipeline.execute(3L);

        ProductionRunSnapshot run = manager.findRun(runId).orElseThrow();
        assertEquals(ProductionRunStatus.COMPLETED, run.status());
        assertEquals(run.requiredWorkUnits(), run.currentWorkUnits());
        assertTrue(run.completionTransactionId().isPresent());
        assertEquals(18L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
        assertEquals(1L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.OUTPUT_INVENTORY, ProductionTestFixtures.OUTPUT));
        assertEquals(1L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.BYPRODUCT_INVENTORY, ProductionTestFixtures.BYPRODUCT));
        assertEquals(1, context.dependencies().transactionManager().size());
        assertEquals(TransactionType.PRODUCTION, context.dependencies().transactionManager().history()
                .getFirst().type());
        assertEquals(TransactionStatus.APPLIED, context.dependencies().transactionManager().history()
                .getFirst().status());
        assertEquals(3, context.dependencies().transactionManager().history()
                .getFirst().inventoryChangePlan().size());
        assertTrue(context.dependencies().orderManager().definitions().isEmpty());
    }

    @Test
    void destinationCapacityFailureRollsBackEveryInputAndOutput() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context(0L, 0L);
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        SimulationSchedulerManager scheduler = scheduler(manager);
        SimulationPipeline pipeline = new SimulationPipeline(scheduler, SimulationExecutionBudget.standard());
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);

        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());
        pipeline.execute(1L);
        pipeline.execute(2L);
        pipeline.execute(3L);

        ProductionRunSnapshot run = manager.findRun(runId).orElseThrow();
        assertEquals(ProductionRunStatus.BLOCKED, run.status());
        assertEquals(ProductionFailureCode.DESTINATION_CAPACITY_EXCEEDED, run.failureCode().orElseThrow());
        assertEquals(20L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
        assertEquals(0L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.OUTPUT_INVENTORY, ProductionTestFixtures.OUTPUT));
        assertEquals(0L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.BYPRODUCT_INVENTORY, ProductionTestFixtures.BYPRODUCT));
        assertEquals(0, context.dependencies().transactionManager().size());
    }

    @Test
    void destinationCapacityUsesDedicatedLossPolicy() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context(0L, 0L);
        ProductionProcessDefinition base = ProductionTestFixtures.process();
        ProductionProcessDefinition.Builder builder = copyProcess(base)
                .executionPolicy(new ProductionExecutionPolicy(
                        ProductionRequirementLossPolicy.BLOCK,
                        ProductionRequirementLossPolicy.PAUSE,
                        ProductionRequirementLossPolicy.BLOCK,
                        ProductionRequirementLossPolicy.FAIL,
                        ProductionTransactionFailurePolicy.BLOCK,
                        20L,
                        100
                ));
        ProductionManager manager = context.manager();
        assertTrue(manager.registerProcess(builder.build()).accepted());
        assertTrue(manager.registerPlan(ProductionTestFixtures.plan()).accepted());
        SimulationSchedulerManager scheduler = scheduler(manager);
        SimulationPipeline pipeline = new SimulationPipeline(scheduler, SimulationExecutionBudget.standard());
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);

        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());
        pipeline.execute(1L);
        pipeline.execute(2L);
        pipeline.execute(3L);

        assertEquals(ProductionRunStatus.FAILED, manager.findRun(runId).orElseThrow().status());
        assertEquals(20L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
        assertEquals(0, context.dependencies().transactionManager().size());
    }

    @Test
    void secondSchedulingRequestReturnsTypedRejection() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        SimulationSchedulerManager scheduler = scheduler(manager);
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);

        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());
        ProductionOperationResult<ProductionRunSnapshot> duplicate = manager.schedule(runId, scheduler, 0L);

        assertFalse(duplicate.accepted());
        assertEquals(ProductionFailureCode.WORK_ALREADY_BOUND, duplicate.failures().getFirst().code());
        assertEquals(ProductionRunStatus.SCHEDULED, manager.findRun(runId).orElseThrow().status());
    }

    @Test
    void multiInputMultiOutputCompletionPreservesDeclaredChangeOrdering() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionLineId secondInputId = ProductionLineId.of("secondary_input");
        ProductionInputDefinition secondInput = new ProductionInputDefinition(
                secondInputId,
                ProductionTestFixtures.INPUT,
                GoodQuantity.of(1L),
                UnitOfMeasure.EACH,
                ProductionInputRole.SECONDARY,
                ConsumptionPolicy.CONSUME_FULL,
                Optional.empty(),
                ProductionInventoryConstraint.any(),
                ProductionLineMetadata.empty()
        );
        ProductionProcessDefinition base = ProductionTestFixtures.process();
        ProductionProcessDefinition.Builder processBuilder = copyProcess(base);
        processBuilder.input(secondInput);
        ProductionManager manager = context.manager();
        assertTrue(manager.registerProcess(processBuilder.build()).accepted());

        ProductionPlanDefinition basePlan = ProductionTestFixtures.plan();
        ProductionPlanDefinition.Builder planBuilder = ProductionPlanDefinition.builder()
                .id(basePlan.id())
                .processId(basePlan.processId())
                .producerActorId(basePlan.producerActorId())
                .batchCount(basePlan.batchCount())
                .createdSimulationTick(basePlan.createdSimulationTick())
                .earliestStartTick(basePlan.earliestStartTick());
        basePlan.inventoryBindings().forEach(planBuilder::inventoryBinding);
        planBuilder.inventoryBinding(new ProductionInventoryBinding(
                secondInputId,
                ProductionBindingDirection.INPUT,
                ProductionTestFixtures.INPUT_INVENTORY,
                ProductionTestFixtures.INPUT,
                UnitOfMeasure.EACH
        ));
        assertTrue(manager.registerPlan(planBuilder.build()).accepted());

        SimulationSchedulerManager scheduler = scheduler(manager);
        SimulationPipeline pipeline = new SimulationPipeline(scheduler, SimulationExecutionBudget.standard());
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());
        pipeline.execute(1L);
        pipeline.execute(2L);
        pipeline.execute(3L);

        assertEquals(17L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
        assertEquals(4, context.dependencies().transactionManager().history()
                .getFirst().inventoryChangePlan().size());
        assertEquals(com.butchercraft.world.inventory.InventoryChangeType.REMOVE,
                context.dependencies().transactionManager().history()
                        .getFirst().inventoryChangePlan().get(0).type());
        assertEquals(com.butchercraft.world.inventory.InventoryChangeType.REMOVE,
                context.dependencies().transactionManager().history()
                        .getFirst().inventoryChangePlan().get(1).type());
        assertEquals(com.butchercraft.world.inventory.InventoryChangeType.ADD,
                context.dependencies().transactionManager().history()
                        .getFirst().inventoryChangePlan().get(2).type());
    }

    @Test
    void cancellationClosesRunAndSchedulerWorkWithoutInventoryMutation() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        SimulationSchedulerManager scheduler = scheduler(manager);
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());

        assertTrue(manager.cancel(runId, scheduler, 0L, "fixture cancellation").accepted());
        assertEquals(ProductionRunStatus.CANCELLED, manager.findRun(runId).orElseThrow().status());
        assertEquals(20L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
        assertEquals(0, context.dependencies().transactionManager().size());
        assertFalse(manager.cancel(runId, scheduler, 0L, "again").accepted());
    }

    @Test
    void runtimeRejectsBackwardTicksAndTerminalTransitions() {
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(
                ProductionRunId.of("test:runtime"),
                ProductionPlanId.of("test:runtime_plan"),
                10L,
                5L
        );
        runtime.cancel("cancelled", 5L);
        assertThrows(IllegalStateException.class, () -> runtime.markReady(6L));
        ProductionRunRuntime active = ProductionRunRuntime.planned(
                ProductionRunId.of("test:active"),
                ProductionPlanId.of("test:active_plan"),
                10L,
                5L
        );
        assertThrows(IllegalArgumentException.class, () -> active.markReady(4L));
    }

    private static SimulationSchedulerManager scheduler(ProductionManager manager) {
        return new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(new ProductionSimulationWorkHandler(manager))),
                0L
        );
    }

    private static ProductionProcessDefinition.Builder copyProcess(ProductionProcessDefinition base) {
        ProductionProcessDefinition.Builder builder = ProductionProcessDefinition.builder()
                .id(base.id())
                .displayName(base.displayName())
                .owningIndustryId(base.owningIndustryId())
                .requiredActorCapability(base.requiredActorCapability())
                .duration(base.duration())
                .batchPolicy(base.batchPolicy())
                .workforceRequirement(base.workforceRequirement())
                .businessRequirement(base.businessRequirement())
                .executionPolicy(base.executionPolicy())
                .metadata(base.metadata())
                .schemaVersion(base.schemaVersion());
        base.additionalRequiredCapabilities().forEach(builder::additionalCapability);
        base.inputs().forEach(builder::input);
        base.outputs().forEach(builder::output);
        base.transformationReferences().forEach(builder::transformationReference);
        return builder;
    }
}
