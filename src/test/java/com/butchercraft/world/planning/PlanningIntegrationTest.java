package com.butchercraft.world.planning;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.world.production.scheduler.ProductionSimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.PipelineStatus;
import com.butchercraft.world.simulation.scheduler.RetryPolicy;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import com.butchercraft.world.simulation.scheduler.WorkOrigin;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPayloadEntry;
import com.butchercraft.world.simulation.scheduler.WorkPriority;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningIntegrationTest {
    @Test
    void schedulerPlanningStageExecutesExactlyOneCycleAndDefersContinuation() {
        PlanningTestFixtures.Context base = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(base, 3L);
        AtomicReference<PlanningManager> managerReference = new AtomicReference<>();
        EconomicPlanningWorkHandler planningHandler =
                new EconomicPlanningWorkHandler(managerReference::get);
        SimulationSchedulerManager scheduler = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(
                        new ProductionSimulationWorkHandler(base.productionManager()),
                        planningHandler
                )),
                19L
        );
        PlanningDependencies source = base.dependencies();
        PlanningDependencies dependencies = new PlanningDependencies(
                source.goodManager(), source.actorManager(), source.businessRuntimeManager(),
                source.workforceManager(), source.inventoryManager(), source.transactionManager(),
                source.orderManager(), source.contractManager(), source.productionManager(), scheduler
        );
        PlanningManager manager = new PlanningManager(
                dependencies, PlanningSelectionPolicy.standard(), PlanningExecutionBudget.standard()
        );
        managerReference.set(manager);
        SimulationWorkId workId = SimulationWorkId.of("test:economic_planning_cycle");
        SimulationWorkRequest request = SimulationWorkRequest.builder()
                .id(workId).typeId(EconomicPlanningWorkHandler.TYPE)
                .stageId(BuiltInSimulationStages.PLANNING).scheduledTick(20L)
                .priority(WorkPriority.NORMAL)
                .origin(WorkOrigin.of("test:planning", 19L, "test:integration"))
                .payload(new WorkPayload(List.of(WorkPayloadEntry.identifier(
                        EconomicPlanningWorkHandler.POLICY_PAYLOAD_KEY,
                        PlanningSelectionPolicy.DEFAULT_ID.value()
                ))))
                .retryPolicy(RetryPolicy.never()).maximumAttempts(10).build();
        assertTrue(scheduler.submit(request, 19L).accepted());

        var report = new SimulationPipeline(
                scheduler, SimulationExecutionBudget.standard()).execute(20L);

        assertEquals(PipelineStatus.COMPLETED, report.status());
        assertEquals(1, manager.cycles().size());
        assertEquals(20L, manager.cycles().getFirst().simulationTick());
        assertEquals(SimulationWorkStatus.DEFERRED,
                scheduler.runtimeFor(workId).orElseThrow().status());
        assertEquals(21L, scheduler.runtimeFor(workId).orElseThrow()
                .nextEligibleTick().orElseThrow());
    }

    @Test
    void modLifecycleLoadsAndSavesPlanningAroundSchedulerAuthorities() throws Exception {
        String source = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"));
        int productionInitialize = source.indexOf("ProductionService.INSTANCE::initialize");
        int planningHandler = source.indexOf("EconomicPlanningService.INSTANCE::prepareHandler");
        int schedulerInitialize = source.indexOf("SimulationSchedulerService.INSTANCE::initialize");
        int productionBind = source.indexOf("ProductionService.INSTANCE::bindScheduler");
        int planningInitialize = source.indexOf("EconomicPlanningService.INSTANCE::initialize");
        int schedulerSave = source.indexOf("SimulationSchedulerService.INSTANCE::save");
        int planningSave = source.indexOf("EconomicPlanningService.INSTANCE::save");

        assertTrue(productionInitialize < planningHandler);
        assertTrue(planningHandler < schedulerInitialize);
        assertTrue(schedulerInitialize < productionBind);
        assertTrue(productionBind < planningInitialize);
        assertTrue(schedulerSave < planningSave);
    }

    @Test
    void serviceDeclaresPlanningStageAndAllSixWorldOwnedFiles() throws Exception {
        String service = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/EconomicPlanningService.java"));
        String storage = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/planning/PlanningStorage.java"));

        assertTrue(service.contains("BuiltInSimulationStages.PLANNING"));
        assertTrue(service.contains("butchercraft:economic_planning_cycle/continuation"));
        assertTrue(service.contains("PlanningStorage"));
        for (String file : List.of(
                "planning_observations.json", "planning_needs.json",
                "planning_opportunities.json", "planning_candidates.json",
                "planning_approved_plans.json", "planning_runtime.json"
        )) {
            assertTrue(storage.contains(file), file);
        }
    }
}
