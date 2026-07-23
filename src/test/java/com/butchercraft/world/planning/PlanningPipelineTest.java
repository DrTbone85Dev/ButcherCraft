package com.butchercraft.world.planning;

import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.actor.ActorRuntimeStatus;
import com.butchercraft.world.production.ProductionBindingDirection;
import com.butchercraft.world.production.ProductionInventoryBinding;
import com.butchercraft.world.production.ProductionPlanDefinition;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionPriority;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningPipelineTest {
    @Test
    void openOrderCompilesIntoOneExactSubmittedProductionPlan() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        long inputBefore = context.inventoryManager().runtimeFor(
                PlanningTestFixtures.INPUT_INVENTORY).orElseThrow()
                .quantityOf(PlanningTestFixtures.INPUT, com.butchercraft.world.goods.UnitOfMeasure.EACH);
        int transactionsBefore = context.transactionManager().size();

        PlanningCycleSnapshot cycle = PlanningTestFixtures.manager(context).executeCycle(20L);

        assertEquals(PlanningCycleStatus.COMPLETED, cycle.status());
        assertEquals(1, cycle.needs().size());
        assertEquals(GoodQuantity.of(3L), cycle.needs().getFirst().requestedQuantity().orElseThrow());
        assertEquals(1, cycle.opportunities().size());
        assertEquals(1, cycle.candidates().size());
        assertEquals(3L, cycle.candidates().getFirst().action().batchCount());
        assertEquals(GoodQuantity.of(3L), cycle.candidates().getFirst().metrics().expectedOutput());
        assertTrue(cycle.candidates().getFirst().metrics().overproduction().isZero());
        assertEquals(1, cycle.approvedPlans().size());
        assertEquals(PlanningSubmissionStatus.SUBMITTED, cycle.submissionRuntimes().getFirst().status());
        assertEquals(1, context.productionManager().planRegistry().size());
        assertEquals(1, context.productionManager().runs().size());
        assertEquals(ProductionRunStatus.SCHEDULED, context.productionManager().runs().getFirst().status());
        assertEquals(1, context.schedulerManager().registry().size());

        assertEquals(inputBefore, context.inventoryManager().runtimeFor(
                PlanningTestFixtures.INPUT_INVENTORY).orElseThrow()
                .quantityOf(PlanningTestFixtures.INPUT, com.butchercraft.world.goods.UnitOfMeasure.EACH));
        assertEquals(transactionsBefore, context.transactionManager().size());
        assertEquals(GoodQuantity.of(3L), context.orderManager().remainingQuantity(
                PlanningTestFixtures.ORDER, PlanningTestFixtures.ORDER_LINE));
    }

    @Test
    void activeProductionCommitmentPreventsDuplicatePlanning() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningManager manager = PlanningTestFixtures.manager(context);
        manager.executeCycle(20L);

        PlanningCycleSnapshot second = manager.executeCycle(21L);

        assertTrue(second.observations().stream()
                .anyMatch(value -> value.type() == ObservationType.PRODUCTION_RUN_ACTIVE));
        assertTrue(second.needs().isEmpty());
        assertTrue(second.approvedPlans().isEmpty());
        assertEquals(1, context.productionManager().planRegistry().size());
        assertEquals(1, context.schedulerManager().registry().size());
    }

    @Test
    void fractionalNeedUsesExactWholeBatchCeilingAndReportsOverproduction() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, GoodQuantity.of("2.5"));

        PlanningCycleSnapshot cycle = PlanningTestFixtures.manager(context).executeCycle(20L);

        CandidatePlanDefinition candidate = cycle.candidates().getFirst();
        assertEquals(3L, candidate.action().batchCount());
        assertEquals(GoodQuantity.of(3L), candidate.metrics().expectedOutput());
        assertEquals(GoodQuantity.of("2.5"), candidate.metrics().quantityAddressed());
        assertEquals(GoodQuantity.of("0.5"), candidate.metrics().overproduction());
        assertEquals(GoodQuantity.of("2.5"),
                cycle.approvedPlans().getFirst().needAllocations().getFirst().quantity());
    }

    @Test
    void outputCapacityBoundsApprovalWithoutMutatingInventory() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context(2L);
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);

        PlanningCycleSnapshot cycle = PlanningTestFixtures.manager(context).executeCycle(20L);

        assertEquals(2L, cycle.opportunities().getFirst().capacity().outputBatches());
        assertEquals(2L, cycle.candidates().getFirst().action().batchCount());
        assertEquals(NeedResolutionStatus.PARTIALLY_RESOLVED,
                cycle.needRuntimes().getFirst().status());
        assertEquals(GoodQuantity.of(1L), cycle.needRuntimes().getFirst().unresolved());
        assertEquals(0L, context.inventoryManager().runtimeFor(
                PlanningTestFixtures.OUTPUT_INVENTORY).orElseThrow()
                .quantityOf(PlanningTestFixtures.OUTPUT,
                        com.butchercraft.world.goods.UnitOfMeasure.EACH));
    }

    @Test
    void authoritativeActorReadinessBecomesATypedBlockingConstraint() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        context.dependencies().actorManager().requireRuntime(PlanningTestFixtures.PRODUCER)
                .transitionTo(ActorRuntimeStatus.SUSPENDED, 12L);

        PlanningCycleSnapshot cycle = PlanningTestFixtures.manager(context).executeCycle(20L);

        assertFalse(cycle.opportunities().getFirst().available());
        assertEquals(List.of(ConstraintType.ACTOR_CAPABILITY_MISSING),
                cycle.opportunities().getFirst().blockingReasons());
        assertEquals(CandidateFeasibility.BLOCKED,
                cycle.candidates().getFirst().feasibility());
        assertTrue(cycle.approvedPlans().isEmpty());
        assertTrue(cycle.constraints().stream()
                .anyMatch(value -> value.type() == ConstraintType.ACTOR_CAPABILITY_MISSING));
        assertFalse(cycle.needRuntimes().getFirst().blockingConstraints().isEmpty());
        assertEquals(NeedResolutionStatus.BLOCKED, cycle.needRuntimes().getFirst().status());
    }

    @Test
    void repeatedEquivalentWorldStateProducesByteStableDomainResults() {
        PlanningTestFixtures.Context firstContext = PlanningTestFixtures.context();
        PlanningTestFixtures.Context secondContext = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(firstContext, 3L);
        PlanningTestFixtures.submitAcceptedOrder(secondContext, 3L);

        PlanningCycleSnapshot first = PlanningTestFixtures.manager(firstContext).executeCycle(20L);
        PlanningCycleSnapshot second = PlanningTestFixtures.manager(secondContext).executeCycle(20L);

        assertEquals(first, second);
        assertEquals(first.id(), PlanningCycleId.forTick(20L));
    }

    @Test
    void submissionAdapterIsIdempotentForAnAlreadySubmittedApprovedPlan() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningCycleSnapshot cycle = PlanningTestFixtures.manager(context).executeCycle(20L);
        ProductionPlanningSubmissionAdapter adapter =
                new ProductionPlanningSubmissionAdapter(context.dependencies());
        int plansBefore = context.productionManager().planRegistry().size();
        int workBefore = context.schedulerManager().registry().size();

        PlanningSubmissionResult replay = adapter.submit(cycle.approvedPlans().getFirst(), 20L);

        assertEquals(PlanningSubmissionStatus.SUBMITTED, replay.status());
        assertEquals(plansBefore, context.productionManager().planRegistry().size());
        assertEquals(workBefore, context.schedulerManager().registry().size());
        assertEquals(cycle.submissionRuntimes().getFirst().targetPlanReference(),
                replay.targetPlanReference());
        assertEquals(cycle.submissionRuntimes().getFirst().schedulerWorkReference(),
                replay.workReference());
    }

    @Test
    void observationBudgetExhaustionCompletesWithVisibleRemainder() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningExecutionBudget standard = PlanningExecutionBudget.standard();
        PlanningExecutionBudget constrained = new PlanningExecutionBudget(
                1, standard.maximumNeeds(), standard.maximumConstraints(),
                standard.maximumOpportunities(), standard.maximumOpportunitiesPerNeed(),
                standard.maximumCandidates(), standard.maximumCandidatesPerNeed(),
                standard.maximumEvaluations(), standard.maximumApprovedPlans(),
                standard.maximumApprovedPlansPerNeed(), standard.maximumSubmissions(),
                standard.maximumAggregationGroupSize(), standard.maximumRecursiveDepth(),
                standard.maximumProviderWorkUnits(), standard.maximumTotalWorkUnits(),
                standard.maximumPayloadSize()
        );

        PlanningCycleSnapshot cycle = new PlanningManager(
                context.dependencies(), PlanningSelectionPolicy.standard(), constrained
        ).executeCycle(20L);

        assertEquals(PlanningCycleStatus.COMPLETED_WITH_REMAINDER, cycle.status());
        assertTrue(cycle.report().truncated());
        assertEquals(1, cycle.observations().size());
    }

    @Test
    void totalWorkBudgetStopsAtADeterministicArtifactBoundary() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningExecutionBudget standard = PlanningExecutionBudget.standard();
        PlanningExecutionBudget constrained = new PlanningExecutionBudget(
                standard.maximumObservations(), standard.maximumNeeds(), standard.maximumConstraints(),
                standard.maximumOpportunities(), standard.maximumOpportunitiesPerNeed(),
                standard.maximumCandidates(), standard.maximumCandidatesPerNeed(),
                standard.maximumEvaluations(), standard.maximumApprovedPlans(),
                standard.maximumApprovedPlansPerNeed(), standard.maximumSubmissions(),
                standard.maximumAggregationGroupSize(), standard.maximumRecursiveDepth(),
                standard.maximumProviderWorkUnits(), 1L, standard.maximumPayloadSize()
        );

        PlanningCycleSnapshot cycle = new PlanningManager(
                context.dependencies(), PlanningSelectionPolicy.standard(), constrained
        ).executeCycle(20L);

        assertEquals(PlanningCycleStatus.COMPLETED_WITH_REMAINDER, cycle.status());
        assertEquals(1L, cycle.report().providerWorkUnits());
        assertEquals(1L, cycle.report().totalWorkUnits());
        assertTrue(cycle.report().truncated());
        assertTrue(cycle.needs().isEmpty());
        assertTrue(cycle.approvedPlans().isEmpty());
    }

    @Test
    void payloadBudgetProducesAVisibleRemainderInsteadOfOversizedArtifacts() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningExecutionBudget standard = PlanningExecutionBudget.standard();
        PlanningExecutionBudget constrained = new PlanningExecutionBudget(
                standard.maximumObservations(), standard.maximumNeeds(), standard.maximumConstraints(),
                standard.maximumOpportunities(), standard.maximumOpportunitiesPerNeed(),
                standard.maximumCandidates(), standard.maximumCandidatesPerNeed(),
                standard.maximumEvaluations(), standard.maximumApprovedPlans(),
                standard.maximumApprovedPlansPerNeed(), standard.maximumSubmissions(),
                standard.maximumAggregationGroupSize(), standard.maximumRecursiveDepth(),
                standard.maximumProviderWorkUnits(), standard.maximumTotalWorkUnits(), 1
        );

        PlanningCycleSnapshot cycle = new PlanningManager(
                context.dependencies(), PlanningSelectionPolicy.standard(), constrained
        ).executeCycle(20L);

        assertEquals(PlanningCycleStatus.COMPLETED_WITH_REMAINDER, cycle.status());
        assertTrue(cycle.observations().isEmpty());
        assertTrue(cycle.report().truncated());
    }

    @Test
    void managerRejectsASecondCycleForTheSameAuthoritativeTick() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningManager manager = PlanningTestFixtures.manager(context);
        manager.executeCycle(20L);

        assertThrows(IllegalArgumentException.class, () -> manager.executeCycle(20L));
        assertFalse(manager.reportsBetween(20L, 20L).isEmpty());
    }

    @Test
    void rejectedSchedulerSubmissionRollsBackANewProductionPlanAndRun() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        ProductionPlanId planId = ProductionPlanId.of("test:rejected_planning_plan");
        ProductionPlanDefinition plan = ProductionPlanDefinition.builder()
                .id(planId).processId(PlanningTestFixtures.PROCESS)
                .producerActorId(PlanningTestFixtures.PRODUCER).batchCount(1L)
                .inventoryBinding(new ProductionInventoryBinding(
                        PlanningTestFixtures.INPUT_LINE, ProductionBindingDirection.INPUT,
                        PlanningTestFixtures.INPUT_INVENTORY, PlanningTestFixtures.INPUT,
                        com.butchercraft.world.goods.UnitOfMeasure.EACH
                ))
                .inventoryBinding(new ProductionInventoryBinding(
                        PlanningTestFixtures.OUTPUT_LINE, ProductionBindingDirection.OUTPUT,
                        PlanningTestFixtures.OUTPUT_INVENTORY, PlanningTestFixtures.OUTPUT,
                        com.butchercraft.world.goods.UnitOfMeasure.EACH
                ))
                .createdSimulationTick(20L).earliestStartTick(20L)
                .priority(ProductionPriority.NORMAL).build();
        SimulationSchedulerManager rejectingScheduler = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), SimulationWorkHandlerRegistry.empty(), 19L
        );

        var result = context.productionManager()
                .registerAndSchedulePlan(plan, rejectingScheduler, 20L);

        assertFalse(result.accepted());
        assertTrue(context.productionManager().planRegistry().find(planId).isEmpty());
        assertTrue(context.productionManager().findRunByPlan(planId).isEmpty());
        assertEquals(0, rejectingScheduler.registry().size());
    }
}
