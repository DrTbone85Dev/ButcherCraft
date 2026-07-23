package com.butchercraft.world.production;

import com.butchercraft.world.economy.actor.ActorRuntimeStatus;
import com.butchercraft.world.economy.order.OrderId;
import com.butchercraft.world.production.scheduler.ProductionSimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.workforce.WorkforceDefinitionId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionRequirementTest {
    @Test
    void runtimeActorLossBlocksWithoutProgressOrInventoryMutation() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        SimulationSchedulerManager scheduler = scheduler(manager, SimulationExecutionBudget.standard());
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());
        context.dependencies().actorManager().requireRuntime(ProductionTestFixtures.ACTOR)
                .transitionTo(ActorRuntimeStatus.SUSPENDED, 1L);

        new SimulationPipeline(scheduler, SimulationExecutionBudget.standard()).execute(1L);

        ProductionRunSnapshot run = manager.findRun(runId).orElseThrow();
        assertEquals(ProductionRunStatus.BLOCKED, run.status());
        assertEquals(0L, run.currentWorkUnits());
        assertEquals(20L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
    }

    @Test
    void requireOnlyInputIsValidatedButNeverConsumed() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionProcessDefinition process = withInputPolicy(
                ProductionTestFixtures.process(), ConsumptionPolicy.REQUIRE_ONLY
        );
        ProductionManager manager = context.manager();
        assertTrue(manager.registerProcess(process).accepted());
        assertTrue(manager.registerPlan(ProductionTestFixtures.plan()).accepted());
        SimulationSchedulerManager scheduler = scheduler(manager, SimulationExecutionBudget.standard());
        SimulationPipeline pipeline = new SimulationPipeline(scheduler, SimulationExecutionBudget.standard());
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());

        pipeline.execute(1L);
        pipeline.execute(2L);
        pipeline.execute(3L);

        assertEquals(ProductionRunStatus.COMPLETED, manager.findRun(runId).orElseThrow().status());
        assertEquals(20L, context.dependencies().inventoryManager().quantityIn(
                ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT));
        assertEquals(2, context.dependencies().transactionManager().history()
                .getFirst().inventoryChangePlan().size());
    }

    @Test
    void schedulerBudgetDefersBeforeMutatingProductionState() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        SimulationExecutionBudget tiny = new SimulationExecutionBudget(10, 10, 1L, 10, 10, 10, 2);
        SimulationSchedulerManager scheduler = scheduler(manager, tiny);
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
        assertTrue(manager.schedule(runId, scheduler, 0L).accepted());

        new SimulationPipeline(scheduler, tiny).execute(1L);

        ProductionRunSnapshot run = manager.findRun(runId).orElseThrow();
        assertEquals(ProductionRunStatus.SCHEDULED, run.status());
        assertEquals(0, run.executionAttemptCount());
        assertEquals(0L, run.currentWorkUnits());
    }

    @Test
    void schedulerRejectionLeavesReadyRunWithoutDanglingWork() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        SimulationSchedulerManager scheduler = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), SimulationWorkHandlerRegistry.empty(), 0L
        );
        ProductionRunId runId = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);

        assertFalse(manager.schedule(runId, scheduler, 0L).accepted());
        ProductionRunSnapshot run = manager.findRun(runId).orElseThrow();
        assertEquals(ProductionRunStatus.READY, run.status());
        assertTrue(run.scheduledWorkId().isEmpty());
        assertEquals(0, scheduler.registry().size());
    }

    @Test
    void missingBusinessWorkforceAndOrderReferencesAreTypedRejections() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionProcessDefinition businessProcess = copyProcess(
                ProductionTestFixtures.process(),
                ProductionBusinessRequirement.operational(),
                ProductionWorkforceRequirement.none()
        );
        assertTrue(context.manager().registerProcess(businessProcess).accepted());
        ProductionOperationResult<ProductionRunSnapshot> noBusiness =
                context.manager().registerPlan(ProductionTestFixtures.plan());
        assertFalse(noBusiness.accepted());
        assertTrue(noBusiness.failures().stream().anyMatch(failure ->
                failure.code() == ProductionFailureCode.UNKNOWN_BUSINESS));

        ProductionTestFixtures.TestContext workforceContext = ProductionTestFixtures.context();
        ProductionWorkforceRequirement missingWorkforce = new ProductionWorkforceRequirement(
                Optional.of(new WorkforceDefinitionId("missing")),
                Set.of(),
                0,
                Set.of(),
                Optional.empty(),
                false
        );
        ProductionOperationResult<ProductionProcessDefinition> workforce =
                workforceContext.manager().registerProcess(copyProcess(
                        ProductionTestFixtures.process(),
                        ProductionBusinessRequirement.none(),
                        missingWorkforce
                ));
        assertFalse(workforce.accepted());
        assertEquals(ProductionFailureCode.UNKNOWN_WORKFORCE_REFERENCE,
                workforce.failures().getFirst().code());

        ProductionTestFixtures.TestContext orderContext = ProductionTestFixtures.context();
        assertTrue(orderContext.manager().registerProcess(ProductionTestFixtures.process()).accepted());
        ProductionPlanDefinition unknownOrder = copyPlanWithOrder(
                ProductionTestFixtures.plan(), OrderId.of("test:missing_order")
        );
        ProductionOperationResult<ProductionRunSnapshot> order =
                orderContext.manager().registerPlan(unknownOrder);
        assertFalse(order.accepted());
        assertTrue(order.failures().stream().anyMatch(failure ->
                failure.code() == ProductionFailureCode.UNKNOWN_ORDER));
    }

    private static SimulationSchedulerManager scheduler(
            ProductionManager manager,
            SimulationExecutionBudget ignored
    ) {
        return new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(new ProductionSimulationWorkHandler(manager))),
                0L
        );
    }

    private static ProductionProcessDefinition withInputPolicy(
            ProductionProcessDefinition source,
            ConsumptionPolicy policy
    ) {
        ProductionInputDefinition old = source.inputs().getFirst();
        ProductionInputDefinition replacement = new ProductionInputDefinition(
                old.id(), old.goodId(), old.quantityPerBatch(), old.unit(), old.role(), policy,
                old.transformationReference(), old.sourceConstraint(), old.metadata()
        );
        ProductionProcessDefinition.Builder builder = baseProcess(source);
        builder.input(replacement);
        source.outputs().forEach(builder::output);
        source.transformationReferences().forEach(builder::transformationReference);
        return builder.build();
    }

    private static ProductionProcessDefinition copyProcess(
            ProductionProcessDefinition source,
            ProductionBusinessRequirement business,
            ProductionWorkforceRequirement workforce
    ) {
        ProductionProcessDefinition.Builder builder = ProductionProcessDefinition.builder()
                .id(source.id())
                .displayName(source.displayName())
                .owningIndustryId(source.owningIndustryId())
                .requiredActorCapability(source.requiredActorCapability())
                .duration(source.duration())
                .batchPolicy(source.batchPolicy())
                .businessRequirement(business)
                .workforceRequirement(workforce);
        source.inputs().forEach(builder::input);
        source.outputs().forEach(builder::output);
        source.transformationReferences().forEach(builder::transformationReference);
        return builder.build();
    }

    private static ProductionProcessDefinition.Builder baseProcess(ProductionProcessDefinition source) {
        return ProductionProcessDefinition.builder()
                .id(source.id())
                .displayName(source.displayName())
                .owningIndustryId(source.owningIndustryId())
                .requiredActorCapability(source.requiredActorCapability())
                .duration(source.duration())
                .batchPolicy(source.batchPolicy());
    }

    private static ProductionPlanDefinition copyPlanWithOrder(
            ProductionPlanDefinition source,
            OrderId orderId
    ) {
        ProductionPlanDefinition.Builder builder = ProductionPlanDefinition.builder()
                .id(source.id())
                .processId(source.processId())
                .producerActorId(source.producerActorId())
                .batchCount(source.batchCount())
                .createdSimulationTick(source.createdSimulationTick())
                .earliestStartTick(source.earliestStartTick())
                .priority(source.priority())
                .requestingOrderId(orderId);
        source.inventoryBindings().forEach(builder::inventoryBinding);
        return builder.build();
    }
}
