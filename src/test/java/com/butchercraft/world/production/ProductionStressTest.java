package com.butchercraft.world.production;

import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.goods.GoodYieldRatio;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryChange;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.production.persistence.ProductionStorage;
import com.butchercraft.world.production.scheduler.ProductionSimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.transaction.EconomicTransaction;
import com.butchercraft.world.transaction.TransactionId;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.transaction.TransactionRegistry;
import com.butchercraft.world.transaction.TransactionStatus;
import com.butchercraft.world.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionStressTest {
    private static final int PROCESS_COUNT = 100_000;
    private static final int PLAN_COUNT = 250_000;
    private static final int SCHEDULED_RUN_COUNT = 50_000;
    private static final int BLOCKED_RUN_COUNT = 100_000;
    private static final int COMPLETED_RUN_COUNT = 100_000;

    @Test
    void registryIndexesOneHundredThousandProcessesAndFiveHundredThousandLines() {
        List<ProductionInputDefinition> inputs = inputLines();
        List<ProductionOutputDefinition> outputs = outputLines();
        ProductionProcessRegistryBuilder builder = ProductionProcessRegistry.builder();
        for (int index = 0; index < PROCESS_COUNT; index++) {
            builder.register(process("test:process_" + index, inputs, outputs));
        }

        ProductionProcessRegistry registry = builder.build();
        assertEquals(PROCESS_COUNT, registry.size());
        assertEquals(PROCESS_COUNT, registry.findByIndustry(ProductionTestFixtures.INDUSTRY).size());
        assertEquals(PROCESS_COUNT, registry.findByInputGood(ProductionTestFixtures.INPUT).size());
        assertEquals(PROCESS_COUNT, registry.findByOutputGood(ProductionTestFixtures.OUTPUT).size());
        assertEquals(PROCESS_COUNT, registry.findByCapability(ActorCapability.TRANSFORM).size());
        assertEquals(500_000L, registry.stream()
                .mapToLong(definition -> definition.inputs().size() + definition.outputs().size()).sum());
    }

    @Test
    void registryIndexesTwoHundredFiftyThousandPlansDeterministically() {
        List<ProductionInventoryBinding> bindings = bindings();
        ProductionPlanRegistryBuilder builder = ProductionPlanRegistry.builder();
        for (int index = 0; index < PLAN_COUNT; index++) {
            builder.register(plan("test:plan_" + index, index, bindings));
        }

        ProductionPlanRegistry registry = builder.build();
        assertEquals(PLAN_COUNT, registry.size());
        assertEquals(PLAN_COUNT, registry.findByProcess(ProductionTestFixtures.PROCESS_ID).size());
        assertEquals(PLAN_COUNT, registry.findByActor(ProductionTestFixtures.ACTOR).size());
        assertEquals(PLAN_COUNT, registry.findByInputInventory(
                ProductionTestFixtures.INPUT_INVENTORY).size());
        assertEquals(1, registry.findCreatedBetween(PLAN_COUNT - 1L, PLAN_COUNT - 1L).size());
        assertEquals("test:plan_0", registry.definitions().getFirst().id().value());
        assertEquals("test:plan_249999", registry.definitions().getLast().id().value());
    }

    @Test
    void managerIndexesTwoHundredFiftyThousandRunsIncludingOneHundredThousandCompleted() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        List<EconomicTransaction> transactions = IntStream.range(0, COMPLETED_RUN_COUNT)
                .mapToObj(ProductionStressTest::appliedTransaction)
                .toList();
        TransactionManager transactionManager = new TransactionManager(
                new TransactionRegistry(transactions),
                context.dependencies().inventoryManager()
        );
        ProductionDependencies dependencies = new ProductionDependencies(
                context.dependencies().goodManager(),
                context.dependencies().actorManager(),
                context.dependencies().businessRuntimeManager(),
                context.dependencies().workforceManager(),
                context.dependencies().inventoryManager(),
                transactionManager,
                context.dependencies().orderManager(),
                context.dependencies().contractManager()
        );
        ProductionProcessDefinition process = ProductionTestFixtures.process();
        ProductionProcessRegistry processes = ProductionProcessRegistry.builder().register(process).build();
        List<ProductionInventoryBinding> bindings = ProductionTestFixtures.plan().inventoryBindings();
        ProductionPlanRegistryBuilder planBuilder = ProductionPlanRegistry.builder();
        int totalRuns = SCHEDULED_RUN_COUNT + BLOCKED_RUN_COUNT + COMPLETED_RUN_COUNT;
        List<ProductionRunSnapshot> runs = new ArrayList<>(totalRuns);
        for (int index = 0; index < totalRuns; index++) {
            ProductionPlanDefinition plan = plan("test:runtime_plan_" + index, index, bindings);
            planBuilder.register(plan);
            ProductionRunId runId = ProductionRunId.forPlan(plan.id());
            boolean scheduled = index < SCHEDULED_RUN_COUNT;
            boolean blocked = index >= SCHEDULED_RUN_COUNT
                    && index < SCHEDULED_RUN_COUNT + BLOCKED_RUN_COUNT;
            int completedIndex = index - SCHEDULED_RUN_COUNT - BLOCKED_RUN_COUNT;
            boolean completed = completedIndex >= 0;
            ProductionRunStatus status = scheduled ? ProductionRunStatus.SCHEDULED
                    : blocked ? ProductionRunStatus.BLOCKED : ProductionRunStatus.COMPLETED;
            runs.add(new ProductionRunSnapshot(
                    runId,
                    plan.id(),
                    status,
                    index,
                    OptionalLong.empty(),
                    OptionalLong.empty(),
                    completed ? OptionalLong.of(completedIndex) : OptionalLong.empty(),
                    1L,
                    completed ? 1L : 0L,
                    0,
                    blocked ? OptionalLong.of(index + 1L) : OptionalLong.empty(),
                    scheduled
                            ? Optional.of(com.butchercraft.world.simulation.scheduler.SimulationWorkId.of(
                                    "test:runtime_plan_" + index + "/work"))
                            : Optional.empty(),
                    completed ? Optional.of(TransactionId.of("test:completed_" + completedIndex)) : Optional.empty(),
                    blocked ? Optional.of(ProductionFailureCode.INSUFFICIENT_INPUT) : Optional.empty(),
                    blocked ? Optional.of("Synthetic blocked stress fixture") : Optional.empty(),
                    0L,
                    ProductionSchema.CURRENT_VERSION
            ));
        }

        ProductionManager manager = new ProductionManager(
                dependencies, processes, planBuilder.build(), runs
        );
        assertEquals(totalRuns, manager.runs().size());
        assertEquals(SCHEDULED_RUN_COUNT + BLOCKED_RUN_COUNT, manager.activeRuns().size());
        assertEquals(SCHEDULED_RUN_COUNT, manager.findByStatus(ProductionRunStatus.SCHEDULED).size());
        assertEquals(BLOCKED_RUN_COUNT, manager.blockedRuns().size());
        assertEquals(COMPLETED_RUN_COUNT, manager.completedBetween(0L, COMPLETED_RUN_COUNT - 1L).size());
        assertEquals(1, manager.findByProcess(process.id()).stream()
                .filter(run -> run.id().value().equals("test:runtime_plan_0/run")).count());
        assertEquals(totalRuns, manager.findByActor(ProductionTestFixtures.ACTOR).size());
    }

    @Test
    void schedulerBoundsOneThousandEligibleRunsToStageBudget() {
        int scheduledRuns = 1_000;
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = context.manager();
        manager.registerProcess(ProductionTestFixtures.process());
        SimulationSchedulerManager scheduler = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(new ProductionSimulationWorkHandler(manager))),
                0L
        );
        List<ProductionInventoryBinding> bindings = ProductionTestFixtures.plan().inventoryBindings();
        for (int index = 0; index < scheduledRuns; index++) {
            ProductionPlanDefinition plan = plan("test:scheduled_plan_" + index, 0L, bindings);
            assertTrue(manager.registerPlan(plan).accepted());
            assertTrue(manager.schedule(ProductionRunId.forPlan(plan.id()), scheduler, 0L).accepted());
        }

        com.butchercraft.world.simulation.scheduler.SimulationTickReport report =
                new SimulationPipeline(scheduler, SimulationExecutionBudget.standard()).execute(1L);
        assertEquals(250, report.attemptedWorkItems());
        assertEquals(250, manager.findByStatus(ProductionRunStatus.RUNNING).size());
        assertEquals(750, manager.findByStatus(ProductionRunStatus.SCHEDULED).size());
    }

    @Test
    void representativeTenThousandRecordPersistenceRoundTrips() {
        int records = 10_000;
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        List<ProductionInventoryBinding> bindings = ProductionTestFixtures.plan().inventoryBindings();
        ProductionPlanRegistryBuilder plans = ProductionPlanRegistry.builder();
        List<ProductionRunSnapshot> runs = new ArrayList<>(records);
        for (int index = 0; index < records; index++) {
            ProductionPlanDefinition plan = plan("test:persisted_plan_" + index, index, bindings);
            plans.register(plan);
            runs.add(ProductionRunRuntime.planned(
                    ProductionRunId.forPlan(plan.id()), plan.id(), 2L, index
            ).snapshot());
        }
        ProductionStorage storage = new ProductionStorage(
                java.nio.file.Path.of("unused_processes.json"),
                java.nio.file.Path.of("unused_plans.json"),
                java.nio.file.Path.of("unused_runs.json"),
                context.dependencies()
        );

        String planJson = storage.serializePlans(plans.build());
        String runJson = storage.serializeRuns(runs);
        assertEquals(records, storage.deserializePlans(planJson).size());
        assertEquals(records, storage.deserializeRuns(runJson).size());
    }

    private static ProductionProcessDefinition process(
            String id,
            List<ProductionInputDefinition> inputs,
            List<ProductionOutputDefinition> outputs
    ) {
        return new ProductionProcessDefinition(
                ProductionProcessId.of(id),
                "Synthetic Process",
                ProductionTestFixtures.INDUSTRY,
                ActorCapability.TRANSFORM,
                Set.of(),
                inputs,
                outputs,
                List.of(),
                ProductionDuration.ofTicks(1L),
                ProductionBatchPolicy.wholeBatches(1L, 1L, 1L),
                ProductionWorkforceRequirement.none(),
                ProductionBusinessRequirement.none(),
                ProductionExecutionPolicy.standard(),
                ProductionMetadata.empty(),
                ProductionSchema.CURRENT_VERSION
        );
    }

    private static ProductionPlanDefinition plan(
            String id,
            long tick,
            List<ProductionInventoryBinding> bindings
    ) {
        return new ProductionPlanDefinition(
                ProductionPlanId.of(id),
                ProductionTestFixtures.PROCESS_ID,
                ProductionTestFixtures.ACTOR,
                Optional.empty(),
                1L,
                bindings,
                tick,
                tick,
                OptionalLong.empty(),
                ProductionPriority.NORMAL,
                Optional.empty(),
                Optional.empty(),
                ProductionPlanMetadata.empty(),
                ProductionSchema.CURRENT_VERSION
        );
    }

    private static List<ProductionInputDefinition> inputLines() {
        return IntStream.range(0, 2).mapToObj(index -> new ProductionInputDefinition(
                ProductionLineId.of("input_" + index),
                ProductionTestFixtures.INPUT,
                GoodQuantity.of(1L),
                UnitOfMeasure.EACH,
                index == 0 ? ProductionInputRole.PRIMARY : ProductionInputRole.SECONDARY,
                ConsumptionPolicy.CONSUME_FULL,
                Optional.empty(),
                ProductionInventoryConstraint.any(),
                ProductionLineMetadata.empty()
        )).toList();
    }

    private static List<ProductionOutputDefinition> outputLines() {
        return IntStream.range(0, 3).mapToObj(index -> new ProductionOutputDefinition(
                ProductionLineId.of("output_" + index),
                index == 1 ? ProductionTestFixtures.BYPRODUCT : ProductionTestFixtures.OUTPUT,
                GoodQuantity.of(1L),
                UnitOfMeasure.EACH,
                index == 0 ? ProductionOutputRole.PRIMARY : ProductionOutputRole.BYPRODUCT,
                GoodYieldRatio.identity(),
                Optional.empty(),
                ProductionInventoryConstraint.any(),
                ProductionLineMetadata.empty()
        )).toList();
    }

    private static List<ProductionInventoryBinding> bindings() {
        return List.of(
                new ProductionInventoryBinding(
                        ProductionLineId.of("input_0"), ProductionBindingDirection.INPUT,
                        ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT,
                        UnitOfMeasure.EACH
                ),
                new ProductionInventoryBinding(
                        ProductionLineId.of("input_1"), ProductionBindingDirection.INPUT,
                        ProductionTestFixtures.INPUT_INVENTORY, ProductionTestFixtures.INPUT,
                        UnitOfMeasure.EACH
                ),
                new ProductionInventoryBinding(
                        ProductionLineId.of("output_0"), ProductionBindingDirection.OUTPUT,
                        ProductionTestFixtures.OUTPUT_INVENTORY, ProductionTestFixtures.OUTPUT,
                        UnitOfMeasure.EACH
                ),
                new ProductionInventoryBinding(
                        ProductionLineId.of("output_1"), ProductionBindingDirection.OUTPUT,
                        ProductionTestFixtures.BYPRODUCT_INVENTORY, ProductionTestFixtures.BYPRODUCT,
                        UnitOfMeasure.EACH
                ),
                new ProductionInventoryBinding(
                        ProductionLineId.of("output_2"), ProductionBindingDirection.OUTPUT,
                        ProductionTestFixtures.OUTPUT_INVENTORY, ProductionTestFixtures.OUTPUT,
                        UnitOfMeasure.EACH
                )
        );
    }

    private static EconomicTransaction appliedTransaction(int index) {
        return EconomicTransaction.builder()
                .id(TransactionId.of("test:completed_" + index))
                .type(TransactionType.PRODUCTION)
                .sourceActorId(ProductionTestFixtures.ACTOR)
                .destinationActorId(ProductionTestFixtures.ACTOR)
                .goodId(ProductionTestFixtures.OUTPUT)
                .quantity(1L)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .simulationTick(index)
                .status(TransactionStatus.APPLIED)
                .inventoryChange(InventoryChange.add(
                        ProductionTestFixtures.OUTPUT_INVENTORY,
                        new InventoryEntry(ProductionTestFixtures.OUTPUT, 1L, UnitOfMeasure.EACH)
                ))
                .build();
    }
}
