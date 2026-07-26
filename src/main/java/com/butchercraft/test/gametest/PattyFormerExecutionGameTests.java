package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.machine.grinder.production.GrinderProductionAdapter;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.machine.pattyformer.execution.PattyFormerWorkstationReference;
import com.butchercraft.machine.pattyformer.production.PattyFormerProductionAdapter;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.business.runtime.BusinessRuntimeManager;
import com.butchercraft.world.business.runtime.BusinessRuntimeRegistry;
import com.butchercraft.world.economy.actor.ActorCapability;
import com.butchercraft.world.economy.actor.ActorId;
import com.butchercraft.world.economy.actor.ActorRuntimeStatus;
import com.butchercraft.world.economy.actor.ActorType;
import com.butchercraft.world.economy.actor.EconomicActorDefinition;
import com.butchercraft.world.economy.actor.EconomicActorManager;
import com.butchercraft.world.economy.actor.EconomicActorRegistry;
import com.butchercraft.world.economy.order.ContractManager;
import com.butchercraft.world.economy.order.GoodQuantity;
import com.butchercraft.world.economy.order.OrderManager;
import com.butchercraft.world.goods.BuiltInIndustryCatalog;
import com.butchercraft.world.goods.CommodityDefinition;
import com.butchercraft.world.goods.CommodityType;
import com.butchercraft.world.goods.EconomicFlag;
import com.butchercraft.world.goods.GoodId;
import com.butchercraft.world.goods.GoodManager;
import com.butchercraft.world.goods.GoodRegistry;
import com.butchercraft.world.goods.GoodTransformation;
import com.butchercraft.world.goods.GoodYieldRatio;
import com.butchercraft.world.goods.IndustryId;
import com.butchercraft.world.goods.Stackability;
import com.butchercraft.world.goods.StorageRequirement;
import com.butchercraft.world.goods.TransportRequirement;
import com.butchercraft.world.goods.UnitOfMeasure;
import com.butchercraft.world.inventory.InventoryContainer;
import com.butchercraft.world.inventory.InventoryEntry;
import com.butchercraft.world.inventory.InventoryId;
import com.butchercraft.world.inventory.InventoryManager;
import com.butchercraft.world.inventory.InventoryRegistry;
import com.butchercraft.world.inventory.InventoryRuntime;
import com.butchercraft.world.inventory.InventorySchema;
import com.butchercraft.world.inventory.InventoryStatus;
import com.butchercraft.world.inventory.InventoryType;
import com.butchercraft.world.inventory.StorageCapacity;
import com.butchercraft.world.inventory.StorageNode;
import com.butchercraft.world.inventory.StorageNodeId;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.production.ConsumptionPolicy;
import com.butchercraft.world.production.ProductionBatchPolicy;
import com.butchercraft.world.production.ProductionBindingDirection;
import com.butchercraft.world.production.ProductionChainStepStatus;
import com.butchercraft.world.production.ProductionDependencies;
import com.butchercraft.world.production.ProductionDuration;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionInputDefinition;
import com.butchercraft.world.production.ProductionInputRole;
import com.butchercraft.world.production.ProductionInventoryBinding;
import com.butchercraft.world.production.ProductionInventoryConstraint;
import com.butchercraft.world.production.ProductionLineId;
import com.butchercraft.world.production.ProductionLineMetadata;
import com.butchercraft.world.production.ProductionManager;
import com.butchercraft.world.production.ProductionOperationResult;
import com.butchercraft.world.production.ProductionOutputDefinition;
import com.butchercraft.world.production.ProductionOutputRole;
import com.butchercraft.world.production.ProductionPlanDefinition;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionPriority;
import com.butchercraft.world.production.ProductionProcessDefinition;
import com.butchercraft.world.production.ProductionProcessId;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionTransformationReference;
import com.butchercraft.world.production.ProductionWorkstationChain;
import com.butchercraft.world.production.ProductionWorkstationChainStatus;
import com.butchercraft.world.production.ProductionWorkstationChainStep;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRuntime;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import com.butchercraft.world.transaction.TransactionManager;
import com.butchercraft.world.workforce.WorkforceManager;
import com.butchercraft.world.workforce.WorkforceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PattyFormerExecutionGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos GRINDER_POS = new BlockPos(1, 1, 2);
    private static final BlockPos PATTY_FORMER_POS = new BlockPos(3, 1, 2);
    private static final int COMPLETION_ASSERTION_TICK = 100;
    private static final int EXTENDED_ASSERTION_TICK = 130;

    private static final IndustryId INDUSTRY = BuiltInIndustryCatalog.MANUFACTURING;
    private static final GoodId INPUT = GoodId.of("test:input_a");
    private static final GoodId OUTPUT = GoodId.of("test:output_b");
    private static final GoodId BYPRODUCT = GoodId.of("test:byproduct_c");
    private static final ActorId ACTOR = ActorId.of("test:producer");
    private static final InventoryId INPUT_INVENTORY = InventoryId.of("test:input_inventory");
    private static final InventoryId OUTPUT_INVENTORY = InventoryId.of("test:output_inventory");
    private static final InventoryId BYPRODUCT_INVENTORY = InventoryId.of("test:byproduct_inventory");
    private static final ProductionProcessId PROCESS_ID = ProductionProcessId.of("test:generic_process");
    private static final ProductionPlanId PLAN_ID = ProductionPlanId.of("test:plan");
    private static final ProductionRunId RUN_ID = ProductionRunId.forPlan(PLAN_ID);
    private static final ProductionLineId INPUT_LINE = ProductionLineId.of("input");
    private static final ProductionLineId OUTPUT_LINE = ProductionLineId.of("output");
    private static final ProductionLineId BYPRODUCT_LINE = ProductionLineId.of("byproduct");

    private PattyFormerExecutionGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void placementCreatesIdlePattyFormerBlockEntity(GameTestHelper helper) {
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);

        helper.assertTrue(pattyFormer.workstationState() == WorkstationState.IDLE,
                "Placed Patty Former starts in IDLE state");
        helper.assertTrue(pattyFormer.inventory().input().isEmpty(), "Placed Patty Former input starts empty");
        helper.assertTrue(pattyFormer.inventory().output().isEmpty(), "Placed Patty Former output starts empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void groundBeefProcessesIntoBeefPatties(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(4, () -> {
            PattyFormerBlockEntity active = pattyFormer(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    "Patty Former begins processing through server block-entity tick");
            ExecutionOperationSnapshot operation = onlyNewPattyFormerOperation(helper, before);
            helper.assertTrue(operation.status() == ExecutionStatus.AUTHORIZED
                            || operation.status() == ExecutionStatus.READY,
                    "Patty Former Execution operation is authorized before scheduler dispatch");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            PattyFormerBlockEntity completed = pattyFormer(helper);
            assertCompletedBeefPatties(helper, completed);
            assertCompletedExecutionAndScheduler(helper, onlyNewPattyFormerOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void pattyFormerProcessingDoesNotCompleteEarly(GameTestHelper helper) {
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(30, () -> {
            PattyFormerBlockEntity active = pattyFormer(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    "Patty Former remains processing before the 60 tick duration");
            helper.assertTrue(active.inventory().output().isEmpty(), "No Beef Patties are produced early");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void repeatedInteractionDoesNotDuplicatePattyFormerOutput(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(8, () -> {
            PattyFormerBlockEntity active = pattyFormer(helper);
            ItemStack duplicateRemainder = active.inventory().insertItem(
                    WorkstationInventory.INPUT_SLOT,
                    groundBeef(),
                    false
            );
            helper.assertFalse(duplicateRemainder.isEmpty(),
                    "Second Ground Beef insertion is rejected while slot is occupied");
            helper.useBlock(PATTY_FORMER_POS);
            helper.assertTrue(newPattyFormerOperations(helper, before).size() == 1,
                    "Repeated use while processing does not create another Patty Former Execution operation");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertCompletedBeefPatties(helper, pattyFormer(helper));
            helper.assertTrue(newPattyFormerOperations(helper, before).size() == 1,
                    "Only one Patty Former Execution operation exists after repeated interaction");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void repeatedTicksAfterCompletionDoNotRerunPattyFormer(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            assertCompletedBeefPatties(helper, pattyFormer(helper));
            assertCompletedExecutionAndScheduler(helper, onlyNewPattyFormerOperation(helper, before));
            helper.assertTrue(newPattyFormerOperations(helper, before).size() == 1,
                    "Completed Patty Former does not create another Execution operation on later ticks");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void menuCloseDoesNotCancelPattyFormerProcessing(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(8, () -> {
            var player = helper.makeMockPlayer(GameType.CREATIVE);
            helper.useBlock(PATTY_FORMER_POS, player);
            player.closeContainer();
            helper.assertTrue(pattyFormer(helper).workstationState() == WorkstationState.PROCESSING,
                    "Closing the Patty Former menu does not cancel active processing");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertCompletedBeefPatties(helper, pattyFormer(helper));
            assertCompletedExecutionAndScheduler(helper, onlyNewPattyFormerOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void preEffectPattyFormerSerializationResumesSafely(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(16, () -> {
            PattyFormerBlockEntity active = pattyFormer(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    "Patty Former is processing before serialization");
            CompoundTag saved = active.saveWithFullMetadata(helper.getLevel().registryAccess());
            PattyFormerBlockEntity restored = replacePattyFormerBlockEntity(helper, saved);
            helper.assertTrue(restored.workstationState() == WorkstationState.PROCESSING,
                    "Restored Patty Former preserves active processing state");
            helper.assertTrue(elapsedTicks(restored) > 0 && elapsedTicks(restored) < totalTicks(restored),
                    "Restored Patty Former preserves bounded progress before effect publication");
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            assertCompletedBeefPatties(helper, pattyFormer(helper));
            assertCompletedExecutionAndScheduler(helper, onlyNewPattyFormerOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void completedPattyFormerSerializationDoesNotDuplicateOutput(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            PattyFormerBlockEntity completed = pattyFormer(helper);
            assertCompletedBeefPatties(helper, completed);
            replacePattyFormerBlockEntity(helper, completed.saveWithFullMetadata(helper.getLevel().registryAccess()));
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            assertCompletedBeefPatties(helper, pattyFormer(helper));
            assertCompletedExecutionAndScheduler(helper, onlyNewPattyFormerOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void changedInputDuringPattyFormerProcessingBlocksVisibly(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(8, () -> pattyFormer(helper).inventory().setInputInternal(groundPork()));

        helper.runAtTickTime(20, () -> {
            PattyFormerBlockEntity blocked = pattyFormer(helper);
            assertBlockedWith(helper, blocked, WorkstationFailureCode.PRODUCT_DATA_MISMATCH);
            helper.assertTrue(blocked.inventory().output().isEmpty(), "Changed input does not produce Beef Patties");
            helper.assertTrue(newPattyFormerOperations(helper, before).size() == 1,
                    "Changed input does not create a replacement Patty Former Execution operation");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void blockedOutputDuringPattyFormerProcessingFailsSafely(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIdsForPattyFormer(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(8, () -> pattyFormer(helper).inventory().setOutputInternal(beefPatties()));

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            PattyFormerBlockEntity blocked = pattyFormer(helper);
            assertBlockedWith(helper, blocked, WorkstationFailureCode.OUTPUT_OCCUPIED);
            helper.assertFalse(blocked.inventory().input().isEmpty(), "Blocked Patty Former completion preserves input");
            helper.assertFalse(blocked.inventory().output().isEmpty(), "Blocked Patty Former preserves occupied output");
            assertProductId(helper, blocked.inventory().input(), "butchercraft:ground_beef",
                    "Blocked input remains Ground Beef");
            assertProductId(helper, blocked.inventory().output(), "butchercraft:beef_patties",
                    "Blocked output remains the existing Beef Patties stack");
            ExecutionOperationSnapshot operation = onlyNewPattyFormerOperation(helper, before);
            helper.assertTrue(operation.status() == ExecutionStatus.FAILED,
                    "Execution operation fails visibly when Patty Former owner commit is blocked");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140)
    public static void activePattyFormerBlockBreakPreservesGroundBeefWithoutPatties(GameTestHelper helper) {
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(8, () -> {
            helper.assertTrue(pattyFormer(helper).workstationState() == WorkstationState.PROCESSING,
                    "Patty Former is actively processing before block break");
            helper.getLevel().destroyBlock(helper.absolutePos(PATTY_FORMER_POS), true);
        });

        helper.runAtTickTime(14, () -> {
            helper.assertBlockNotPresent(ModBlocks.PATTY_FORMER.get(), PATTY_FORMER_POS);
            helper.assertItemEntityPresent(ModItems.PATTY_FORMER.get(), PATTY_FORMER_POS, 3.0);
            helper.assertItemEntityPresent(ModItems.GROUND_BEEF.get(), PATTY_FORMER_POS, 3.0);
            helper.assertItemEntityNotPresent(ModItems.BEEF_PATTIES.get(), PATTY_FORMER_POS, 3.0);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void malformedPattyFormerRestoredStateStopsVisibly(GameTestHelper helper) {
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        pattyFormer.inventory().setInputInternal(groundBeef());
        CompoundTag saved = pattyFormer.saveWithFullMetadata(helper.getLevel().registryAccess());
        CompoundTag controller = saved.getCompound("Controller");
        controller.putString("State", WorkstationState.PROCESSING.name());
        controller.putInt("ElapsedTicks", 12);
        controller.putInt("TotalTicks", 60);
        controller.remove("SelectedOperation");
        saved.put("Controller", controller);

        PattyFormerBlockEntity restored = replacePattyFormerBlockEntity(helper, saved);

        helper.assertTrue(restored.workstationState() == WorkstationState.ERROR,
                "Malformed active Patty Former state is restored as ERROR");
        helper.assertTrue(restored.lastFailure().orElseThrow().code() == WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                "Malformed Patty Former state exposes invalid workstation failure");
        helper.assertFalse(restored.inventory().input().isEmpty(), "Malformed restore preserves Ground Beef for recovery");
        helper.assertTrue(restored.inventory().output().isEmpty(), "Malformed restore does not publish Beef Patties");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void uncertainPattyFormerConsequentialRestoreRemainsStopped(GameTestHelper helper) {
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertGroundBeef(helper, pattyFormer);

        helper.runAtTickTime(8, () -> {
            CompoundTag saved = pattyFormer(helper).saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag controller = saved.getCompound("Controller");
            controller.putBoolean("CompletionCommitted", true);
            saved.put("Controller", controller);
            PattyFormerBlockEntity restored = replacePattyFormerBlockEntity(helper, saved);
            helper.assertTrue(restored.workstationState() == WorkstationState.ERROR,
                    "Unresolved committed Patty Former effect restores as ERROR");
            helper.assertTrue(restored.lastFailure().orElseThrow().code()
                            == WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Unresolved committed Patty Former effect exposes invalid workstation failure");
            helper.assertFalse(restored.inventory().input().isEmpty(),
                    "Uncertain committed restore preserves Ground Beef for recovery");
            helper.assertTrue(restored.inventory().output().isEmpty(),
                    "Uncertain committed restore does not publish partial Beef Patties");
        });

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(pattyFormer(helper).workstationState() == WorkstationState.ERROR,
                    "Uncertain committed Patty Former restore remains stopped");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void chainGrinderProducesGroundBeef(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertCompletedGroundBeef(helper, grinder(helper));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void chainManualTransferMovesGroundBeefOnlyByPlayerAction(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertCompletedGroundBeef(helper, grinder(helper));
            helper.assertTrue(pattyFormer.inventory().input().isEmpty(),
                    "Patty Former input remains empty before manual transfer");
            transferGroundBeef(helper, grinder(helper), pattyFormer);
            helper.assertTrue(grinder(helper).inventory().output().isEmpty(),
                    "Manual transfer removes Ground Beef from Grinder output");
            assertProductId(helper, pattyFormer.inventory().input(), "butchercraft:ground_beef",
                    "Manual transfer places Ground Beef in Patty Former input");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 360)
    public static void chainPattyFormerProducesBeefPattiesAfterManualTransfer(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertCompletedGroundBeef(helper, grinder(helper));
            transferGroundBeef(helper, grinder(helper), pattyFormer);
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK + 80, () -> {
            assertCompletedBeefPatties(helper, pattyFormer(helper));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 360)
    public static void productionObservesBothOrderedWorkstationSteps(GameTestHelper helper) {
        ProductionManager production = productionManager();
        ProductionWorkstationChain chain = assignBeefPattyChain(helper, production);
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(2, () -> assertAccepted(helper, GrinderProductionAdapter.requestAndObserveChainStep(
                production,
                execution(helper),
                RUN_ID,
                grinderStep(chain).stepIdentity(),
                grinder(helper),
                context(helper, GRINDER_POS),
                BuiltInDefinitionIds.GRIND_BEEF,
                2L
        ), "Production observes Grinder step start"));

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertAccepted(helper, GrinderProductionAdapter.observeChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    grinderStep(chain).stepIdentity(),
                    grinder(helper),
                    context(helper, GRINDER_POS),
                    BuiltInDefinitionIds.GRIND_BEEF,
                    COMPLETION_ASSERTION_TICK
            ), "Production observes Grinder step completion");
            transferGroundBeef(helper, grinder(helper), pattyFormer);
            assertAccepted(helper, PattyFormerProductionAdapter.requestAndObserveChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    pattyFormerStep(chain).stepIdentity(),
                    pattyFormer(helper),
                    context(helper, PATTY_FORMER_POS),
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                    COMPLETION_ASSERTION_TICK
            ), "Production observes Patty Former step start");
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK + 80, () -> {
            ProductionOperationResult<ProductionRunSnapshot> observed =
                    PattyFormerProductionAdapter.observeChainStep(
                            production,
                            execution(helper),
                            RUN_ID,
                            pattyFormerStep(chain).stepIdentity(),
                            pattyFormer(helper),
                            context(helper, PATTY_FORMER_POS),
                            BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                            EXTENDED_ASSERTION_TICK + 80L
                    );
            assertAccepted(helper, observed, "Production observes Patty Former completion");
            ProductionRunSnapshot run = observed.value().orElseThrow();
            helper.assertTrue(run.status() == ProductionRunStatus.COMPLETED,
                    "Production Run completes after both owner results are observed");
            helper.assertTrue(run.workstationChain().orElseThrow().completionEvidence().isPresent(),
                    "Production publishes chain completion evidence");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void productionDoesNotAdvanceBeforeManualTransferAndPattyFormerCompletion(GameTestHelper helper) {
        ProductionManager production = productionManager();
        ProductionWorkstationChain chain = assignBeefPattyChain(helper, production);
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(2, () -> assertAccepted(helper, GrinderProductionAdapter.requestAndObserveChainStep(
                production,
                execution(helper),
                RUN_ID,
                grinderStep(chain).stepIdentity(),
                grinder(helper),
                context(helper, GRINDER_POS),
                BuiltInDefinitionIds.GRIND_BEEF,
                2L
        ), "Production observes Grinder step start"));

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            ProductionOperationResult<ProductionRunSnapshot> observed = GrinderProductionAdapter.observeChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    grinderStep(chain).stepIdentity(),
                    grinder(helper),
                    context(helper, GRINDER_POS),
                    BuiltInDefinitionIds.GRIND_BEEF,
                    COMPLETION_ASSERTION_TICK
            );
            assertAccepted(helper, observed, "Production observes Grinder completion");
            ProductionRunSnapshot run = observed.value().orElseThrow();
            helper.assertTrue(run.status() == ProductionRunStatus.READY,
                    "Production Run remains nonterminal before Patty Former completion");
            helper.assertTrue(run.workstationChain().orElseThrow().status()
                            == ProductionWorkstationChainStatus.AWAITING_MANUAL_TRANSFER,
                    "Production exposes the manual-transfer boundary");
            helper.assertTrue(pattyFormer.inventory().input().isEmpty(),
                    "Production does not auto-transfer Ground Beef into the Patty Former");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 380)
    public static void fullChainProducesExactlyOneFinalOutputWithNoDuplication(GameTestHelper helper) {
        Set<ExecutionOperationId> beforePatty = operationIdsForPattyFormer(helper);
        ProductionManager production = productionManager();
        ProductionWorkstationChain chain = assignBeefPattyChain(helper, production);
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(2, () -> assertAccepted(helper, GrinderProductionAdapter.requestAndObserveChainStep(
                production,
                execution(helper),
                RUN_ID,
                grinderStep(chain).stepIdentity(),
                grinder(helper),
                context(helper, GRINDER_POS),
                BuiltInDefinitionIds.GRIND_BEEF,
                2L
        ), "Production observes Grinder start"));

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertAccepted(helper, GrinderProductionAdapter.observeChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    grinderStep(chain).stepIdentity(),
                    grinder(helper),
                    context(helper, GRINDER_POS),
                    BuiltInDefinitionIds.GRIND_BEEF,
                    COMPLETION_ASSERTION_TICK
            ), "Production observes Grinder completion");
            transferGroundBeef(helper, grinder(helper), pattyFormer);
            assertAccepted(helper, PattyFormerProductionAdapter.requestAndObserveChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    pattyFormerStep(chain).stepIdentity(),
                    pattyFormer(helper),
                    context(helper, PATTY_FORMER_POS),
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                    COMPLETION_ASSERTION_TICK
            ), "Production observes Patty Former start");
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK + 100, () -> {
            assertAccepted(helper, PattyFormerProductionAdapter.observeChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    pattyFormerStep(chain).stepIdentity(),
                    pattyFormer(helper),
                    context(helper, PATTY_FORMER_POS),
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                    EXTENDED_ASSERTION_TICK + 100L
            ), "Production observes Patty Former completion");
            assertCompletedBeefPatties(helper, pattyFormer(helper));
            helper.assertTrue(pattyFormer(helper).inventory().output().getCount() == 1,
                    "Full chain produces exactly one Beef Patties item");
            helper.assertTrue(newPattyFormerOperations(helper, beforePatty).size() == 1,
                    "Full chain creates one Patty Former Execution operation");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 380)
    public static void repeatedChainObservationDoesNotDuplicateProductionCompletion(GameTestHelper helper) {
        ProductionManager production = productionManager();
        ProductionWorkstationChain chain = assignBeefPattyChain(helper, production);
        GrinderBlockEntity grinder = placeGrinder(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(2, () -> assertAccepted(helper, GrinderProductionAdapter.requestAndObserveChainStep(
                production,
                execution(helper),
                RUN_ID,
                grinderStep(chain).stepIdentity(),
                grinder(helper),
                context(helper, GRINDER_POS),
                BuiltInDefinitionIds.GRIND_BEEF,
                2L
        ), "Production observes Grinder start"));

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertAccepted(helper, GrinderProductionAdapter.observeChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    grinderStep(chain).stepIdentity(),
                    grinder(helper),
                    context(helper, GRINDER_POS),
                    BuiltInDefinitionIds.GRIND_BEEF,
                    COMPLETION_ASSERTION_TICK
            ), "Production observes Grinder completion");
            transferGroundBeef(helper, grinder(helper), pattyFormer);
            assertAccepted(helper, PattyFormerProductionAdapter.requestAndObserveChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    pattyFormerStep(chain).stepIdentity(),
                    pattyFormer(helper),
                    context(helper, PATTY_FORMER_POS),
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                    COMPLETION_ASSERTION_TICK
            ), "Production observes Patty Former start");
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK + 100, () -> {
            ProductionOperationResult<ProductionRunSnapshot> first = PattyFormerProductionAdapter.observeChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    pattyFormerStep(chain).stepIdentity(),
                    pattyFormer(helper),
                    context(helper, PATTY_FORMER_POS),
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                    EXTENDED_ASSERTION_TICK + 100L
            );
            ProductionOperationResult<ProductionRunSnapshot> duplicate = PattyFormerProductionAdapter.observeChainStep(
                    production,
                    execution(helper),
                    RUN_ID,
                    pattyFormerStep(chain).stepIdentity(),
                    pattyFormer(helper),
                    context(helper, PATTY_FORMER_POS),
                    BuiltInDefinitionIds.FORM_BEEF_PATTIES,
                    EXTENDED_ASSERTION_TICK + 100L
            );
            assertAccepted(helper, first, "First Production completion observation is accepted");
            assertAccepted(helper, duplicate, "Duplicate Production completion observation is accepted");
            helper.assertTrue(first.value().orElseThrow().equals(duplicate.value().orElseThrow()),
                    "Duplicate Production observation returns the existing authoritative Run");
            helper.assertTrue(production.findByStatus(ProductionRunStatus.COMPLETED).size() == 1,
                    "Duplicate Production observation does not create a second completed Run");
            helper.assertTrue(pattyFormer(helper).inventory().output().getCount() == 1,
                    "Duplicate Production observation does not duplicate Beef Patties");
            helper.succeed();
        });
    }

    private static GrinderBlockEntity placeGrinder(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        return grinder(helper);
    }

    private static PattyFormerBlockEntity placePattyFormer(GameTestHelper helper) {
        helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        return pattyFormer(helper);
    }

    private static GrinderBlockEntity grinder(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(GRINDER_POS));
        helper.assertTrue(blockEntity instanceof GrinderBlockEntity,
                "Expected grinder block entity at test position");
        return (GrinderBlockEntity) blockEntity;
    }

    private static PattyFormerBlockEntity pattyFormer(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(PATTY_FORMER_POS));
        helper.assertTrue(blockEntity instanceof PattyFormerBlockEntity,
                "Expected Patty Former block entity at test position");
        return (PattyFormerBlockEntity) blockEntity;
    }

    private static void insertBeefTrim(GameTestHelper helper, GrinderBlockEntity grinder) {
        ItemStack remainder = grinder.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                beefTrim(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Beef Trim inserts into Grinder input");
    }

    private static void insertGroundBeef(GameTestHelper helper, PattyFormerBlockEntity pattyFormer) {
        ItemStack remainder = pattyFormer.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                groundBeef(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Ground Beef inserts into Patty Former input");
    }

    private static void transferGroundBeef(
            GameTestHelper helper,
            GrinderBlockEntity grinder,
            PattyFormerBlockEntity pattyFormer
    ) {
        ItemStack extracted = grinder.inventory().extractItem(WorkstationInventory.OUTPUT_SLOT, 1, false);
        helper.assertFalse(extracted.isEmpty(), "Ground Beef can be manually removed from Grinder output");
        ItemStack remainder = pattyFormer.inventory().insertItem(WorkstationInventory.INPUT_SLOT, extracted, false);
        helper.assertTrue(remainder.isEmpty(), "Ground Beef can be manually inserted into Patty Former input");
    }

    private static ItemStack beefTrim() {
        return ModItems.BEEF_TRIM.get().getDefaultInstance();
    }

    private static ItemStack groundBeef() {
        return ModItems.GROUND_BEEF.get().getDefaultInstance();
    }

    private static ItemStack groundPork() {
        return ModItems.GROUND_PORK.get().getDefaultInstance();
    }

    private static ItemStack beefPatties() {
        return ModItems.BEEF_PATTIES.get().getDefaultInstance();
    }

    private static void assertCompletedGroundBeef(GameTestHelper helper, GrinderBlockEntity grinder) {
        helper.assertTrue(grinder.workstationState() == WorkstationState.COMPLETE,
                "Grinder reaches COMPLETE state");
        helper.assertTrue(grinder.inventory().input().isEmpty(), "Beef Trim is consumed exactly once");
        ItemStack output = grinder.inventory().output();
        helper.assertFalse(output.isEmpty(), "Ground Beef output is present after completion");
        helper.assertTrue(output.getCount() == 1, "Ground Beef output stack count remains one");
        assertProductId(helper, output, "butchercraft:ground_beef", "Output product type is Ground Beef");
    }

    private static void assertCompletedBeefPatties(GameTestHelper helper, PattyFormerBlockEntity pattyFormer) {
        helper.assertTrue(pattyFormer.workstationState() == WorkstationState.COMPLETE,
                "Patty Former reaches COMPLETE state");
        helper.assertTrue(pattyFormer.inventory().input().isEmpty(), "Ground Beef is consumed exactly once");
        ItemStack output = pattyFormer.inventory().output();
        helper.assertFalse(output.isEmpty(), "Beef Patties output is present after completion");
        helper.assertTrue(output.getCount() == 1, "Beef Patties output stack count remains one");
        helper.assertTrue(output.getItem() == ModItems.BEEF_PATTIES.get(),
                "Output item is Beef Patties");
        ProductStackData data = ProductStackAdapter.readProductData(output).orThrow();
        helper.assertTrue(Objects.equals("butchercraft:beef_patties", data.productTypeId()),
                "Output product type is Beef Patties");
        helper.assertTrue(Objects.equals("butchercraft:beef", data.sourceCategoryId()),
                "Output source category remains beef");
        helper.assertTrue(data.quantityValue() == 900, "Output quantity is 900 grams");
    }

    private static void assertProductId(GameTestHelper helper, ItemStack stack, String productId, String message) {
        ProductStackData data = ProductStackAdapter.readProductData(stack).orThrow();
        helper.assertTrue(Objects.equals(productId, data.productTypeId()), message);
    }

    private static void assertBlockedWith(
            GameTestHelper helper,
            PattyFormerBlockEntity pattyFormer,
            WorkstationFailureCode failureCode
    ) {
        helper.assertTrue(pattyFormer.workstationState() == WorkstationState.BLOCKED,
                "Patty Former reaches BLOCKED state");
        helper.assertTrue(pattyFormer.lastFailure().orElseThrow().code() == failureCode,
                "Patty Former reports expected failure " + failureCode.reasonCode());
    }

    private static Set<ExecutionOperationId> operationIdsForPattyFormer(GameTestHelper helper) {
        return operationIdsForWorkstation(helper, pattyFormerIdentity(helper));
    }

    private static Set<ExecutionOperationId> operationIdsForWorkstation(GameTestHelper helper, String workstationIdentity) {
        Set<ExecutionOperationId> ids = new HashSet<>();
        for (ExecutionOperationSnapshot operation : operationsForWorkstation(helper, workstationIdentity)) {
            ids.add(operation.operationId());
        }
        return Set.copyOf(ids);
    }

    private static List<ExecutionOperationSnapshot> operationsForWorkstation(
            GameTestHelper helper,
            String workstationIdentity
    ) {
        return execution(helper).operations().stream()
                .filter(operation -> operation.authorizationEvidence()
                        .executableWorkReferenceId()
                        .equals(workstationIdentity))
                .toList();
    }

    private static List<ExecutionOperationSnapshot> newPattyFormerOperations(
            GameTestHelper helper,
            Set<ExecutionOperationId> before
    ) {
        return operationsForWorkstation(helper, pattyFormerIdentity(helper)).stream()
                .filter(operation -> !before.contains(operation.operationId()))
                .toList();
    }

    private static ExecutionOperationSnapshot onlyNewPattyFormerOperation(
            GameTestHelper helper,
            Set<ExecutionOperationId> before
    ) {
        List<ExecutionOperationSnapshot> operations = newPattyFormerOperations(helper, before);
        helper.assertTrue(operations.size() == 1,
                "Expected exactly one new Patty Former Execution operation, found " + operations.size());
        return operations.getFirst();
    }

    private static void assertCompletedExecutionAndScheduler(
            GameTestHelper helper,
            ExecutionOperationSnapshot operation
    ) {
        ExecutionOperationSnapshot current = execution(helper).find(operation.operationId()).orElseThrow();
        helper.assertTrue(current.status() == ExecutionStatus.SUCCEEDED,
                "Execution operation succeeds after owner result publication");
        helper.assertTrue(current.ownerResultEvidence().isPresent(),
                "Execution operation records owner result evidence");
        helper.assertTrue(current.resultEvidence().isPresent(),
                "Execution operation records result evidence");
        SimulationWorkRuntime runtime = scheduler(helper)
                .runtimeFor(workIdFor(current.operationId()))
                .orElseThrow();
        helper.assertTrue(runtime.status() == SimulationWorkStatus.COMPLETED,
                "Scheduler work completes after observing owner result evidence");
        helper.assertTrue(runtime.ownerResultIdentity().orElseThrow()
                        .equals(current.resultEvidence().orElseThrow().evidenceIdentity()),
                "Scheduler records the Execution result evidence identity");
        helper.assertTrue(runtime.effectContentDigest().orElseThrow()
                        .equals(current.resultEvidence().orElseThrow().resultContentDigest()),
                "Scheduler records the Execution result content digest");
    }

    private static PattyFormerBlockEntity replacePattyFormerBlockEntity(GameTestHelper helper, CompoundTag tag) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(PATTY_FORMER_POS);
        BlockState state = level.getBlockState(absolutePos);
        BlockEntity loaded = BlockEntity.loadStatic(
                absolutePos,
                state,
                tag,
                level.registryAccess()
        );
        helper.assertTrue(loaded instanceof PattyFormerBlockEntity,
                "Serialized Patty Former state reloads as Patty Former block entity");
        PattyFormerBlockEntity restored = (PattyFormerBlockEntity) loaded;
        restored.setLevel(level);
        level.removeBlockEntity(absolutePos);
        level.setBlockEntity(restored);
        return restored;
    }

    private static int elapsedTicks(PattyFormerBlockEntity pattyFormer) {
        return pattyFormer.menuData().get(1);
    }

    private static int totalTicks(PattyFormerBlockEntity pattyFormer) {
        return pattyFormer.menuData().get(2);
    }

    private static WorkstationTickContext context(GameTestHelper helper, BlockPos pos) {
        return new WorkstationTickContext(helper.getLevel(), helper.absolutePos(pos));
    }

    private static ExecutionManager execution(GameTestHelper helper) {
        return ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer());
    }

    private static SimulationSchedulerManager scheduler(GameTestHelper helper) {
        return SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer());
    }

    private static String pattyFormerIdentity(GameTestHelper helper) {
        return PattyFormerWorkstationReference.of(helper.getLevel(), helper.absolutePos(PATTY_FORMER_POS)).identity();
    }

    private static SimulationWorkId workIdFor(ExecutionOperationId operationId) {
        return SimulationWorkId.of(operationId.value() + "/work");
    }

    private static ProductionWorkstationChain assignBeefPattyChain(
            GameTestHelper helper,
            ProductionManager production
    ) {
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        ProductionOperationResult<ProductionRunSnapshot> assigned =
                production.assignWorkstationChain(RUN_ID, chain, 0L);
        assertAccepted(helper, assigned, "Production assigns Beef Patty chain");
        return chain;
    }

    private static ProductionWorkstationChainStep grinderStep(ProductionWorkstationChain chain) {
        return chain.steps().getFirst();
    }

    private static ProductionWorkstationChainStep pattyFormerStep(ProductionWorkstationChain chain) {
        return chain.steps().get(1);
    }

    private static <T> void assertAccepted(
            GameTestHelper helper,
            ProductionOperationResult<T> result,
            String message
    ) {
        helper.assertTrue(result.accepted(),
                message + ": " + result.failures().stream().map(failure -> failure.message()).toList());
    }

    private static ProductionManager productionManager() {
        ProductionDependencies dependencies = productionDependencies();
        ProductionManager manager = new ProductionManager(dependencies);
        if (!manager.registerProcess(process()).accepted()) {
            throw new IllegalStateException("GameTest production fixture process rejected");
        }
        if (!manager.registerPlan(plan()).accepted()) {
            throw new IllegalStateException("GameTest production fixture plan rejected");
        }
        return manager;
    }

    private static ProductionDependencies productionDependencies() {
        GoodRegistry goods = goods();
        EconomicActorDefinition actor = EconomicActorDefinition.builder()
                .id(ACTOR)
                .displayName("Generic Producer")
                .actorType(ActorType.PROCESSOR)
                .industryId(INDUSTRY)
                .capability(ActorCapability.TRANSFORM)
                .capability(ActorCapability.PRODUCE)
                .capability(ActorCapability.STORE)
                .build();
        EconomicActorRegistry actors = EconomicActorRegistry.of(
                List.of(actor), goods, BuiltInIndustryCatalog.all()
        );
        EconomicActorManager actorManager = new EconomicActorManager(actors);
        actorManager.requireRuntime(ACTOR).transitionTo(ActorRuntimeStatus.OPERATIONAL, 0L);

        StorageNodeId nodeId = StorageNodeId.of("test:production_node");
        StorageNode node = StorageNode.builder()
                .id(nodeId)
                .displayName("Production Node")
                .storageRequirement(StorageRequirement.AMBIENT)
                .capacity(StorageCapacity.unlimited())
                .build();
        InventoryContainer input = container(INPUT_INVENTORY, "Input Inventory", nodeId, StorageCapacity.unlimited());
        InventoryContainer output = container(
                OUTPUT_INVENTORY,
                "Output Inventory",
                nodeId,
                StorageCapacity.builder().maximumUnits(100L).maximumDistinctGoods(10).build()
        );
        InventoryContainer byproduct =
                container(BYPRODUCT_INVENTORY, "Byproduct Inventory", nodeId, StorageCapacity.unlimited());
        InventoryRegistry inventories = InventoryRegistry.of(
                List.of(input, output, byproduct), List.of(node), goods, actors
        );
        InventoryManager inventoryManager = new InventoryManager(inventories, List.of(
                runtime(INPUT_INVENTORY, List.of(new InventoryEntry(INPUT, 20L, UnitOfMeasure.EACH))),
                runtime(OUTPUT_INVENTORY, List.of()),
                runtime(BYPRODUCT_INVENTORY, List.of())
        ));
        TransactionManager transactionManager = new TransactionManager(inventoryManager);
        ContractManager contractManager = new ContractManager(actors);
        OrderManager orderManager = new OrderManager(
                actors, inventories, transactionManager, contractManager
        );
        return new ProductionDependencies(
                new GoodManager(goods),
                actorManager,
                new BusinessRuntimeManager(BusinessRuntimeRegistry.empty(), SimulationConfiguration.standard()),
                new WorkforceManager(WorkforceRegistry.empty()),
                inventoryManager,
                transactionManager,
                orderManager,
                contractManager
        );
    }

    private static GoodRegistry goods() {
        return GoodRegistry.of(
                List.of(good(INPUT, "Input A"), good(OUTPUT, "Output B"), good(BYPRODUCT, "Byproduct C")),
                List.of(
                        new GoodTransformation(INPUT, OUTPUT, GoodYieldRatio.identity(), INDUSTRY),
                        new GoodTransformation(INPUT, BYPRODUCT, GoodYieldRatio.identity(), INDUSTRY)
                ),
                BuiltInIndustryCatalog.all()
        );
    }

    private static ProductionProcessDefinition process() {
        ProductionTransformationReference primary = new ProductionTransformationReference(INPUT, OUTPUT, INDUSTRY);
        ProductionTransformationReference secondary = new ProductionTransformationReference(INPUT, BYPRODUCT, INDUSTRY);
        return ProductionProcessDefinition.builder()
                .id(PROCESS_ID)
                .displayName("Generic A to B")
                .owningIndustryId(INDUSTRY)
                .requiredActorCapability(ActorCapability.TRANSFORM)
                .input(new ProductionInputDefinition(
                        INPUT_LINE, INPUT, GoodQuantity.of(2L), UnitOfMeasure.EACH,
                        ProductionInputRole.PRIMARY, ConsumptionPolicy.CONSUME_FULL,
                        Optional.of(primary), ProductionInventoryConstraint.any(), ProductionLineMetadata.empty()
                ))
                .output(new ProductionOutputDefinition(
                        OUTPUT_LINE, OUTPUT, GoodQuantity.of(1L), UnitOfMeasure.EACH,
                        ProductionOutputRole.PRIMARY, GoodYieldRatio.identity(),
                        Optional.of(primary), ProductionInventoryConstraint.any(), ProductionLineMetadata.empty()
                ))
                .output(new ProductionOutputDefinition(
                        BYPRODUCT_LINE, BYPRODUCT, GoodQuantity.of(1L), UnitOfMeasure.EACH,
                        ProductionOutputRole.BYPRODUCT, GoodYieldRatio.identity(),
                        Optional.of(secondary), ProductionInventoryConstraint.any(), ProductionLineMetadata.empty()
                ))
                .transformationReference(primary)
                .transformationReference(secondary)
                .duration(new ProductionDuration(2L, 2L))
                .batchPolicy(ProductionBatchPolicy.wholeBatches(1L, 100L, 1L))
                .build();
    }

    private static ProductionPlanDefinition plan() {
        return ProductionPlanDefinition.builder()
                .id(PLAN_ID)
                .processId(PROCESS_ID)
                .producerActorId(ACTOR)
                .batchCount(1L)
                .inventoryBinding(binding(INPUT_LINE, ProductionBindingDirection.INPUT, INPUT_INVENTORY, INPUT))
                .inventoryBinding(binding(OUTPUT_LINE, ProductionBindingDirection.OUTPUT, OUTPUT_INVENTORY, OUTPUT))
                .inventoryBinding(binding(BYPRODUCT_LINE, ProductionBindingDirection.OUTPUT, BYPRODUCT_INVENTORY, BYPRODUCT))
                .createdSimulationTick(0L)
                .earliestStartTick(1L)
                .priority(ProductionPriority.NORMAL)
                .build();
    }

    private static CommodityDefinition good(GoodId id, String name) {
        return CommodityDefinition.builder()
                .id(id)
                .displayName(name)
                .industryId(INDUSTRY)
                .unitOfMeasure(UnitOfMeasure.EACH)
                .stackability(Stackability.STACKABLE)
                .economicFlag(EconomicFlag.TRADEABLE)
                .storageRequirement(StorageRequirement.AMBIENT)
                .transportRequirement(TransportRequirement.STANDARD)
                .commodityType(CommodityType.RAW_MATERIAL)
                .build();
    }

    private static InventoryContainer container(
            InventoryId id,
            String name,
            StorageNodeId nodeId,
            StorageCapacity capacity
    ) {
        return InventoryContainer.builder()
                .id(id)
                .displayName(name)
                .ownerActorId(ACTOR)
                .storageNodeId(nodeId)
                .inventoryType(InventoryType.PROCESSING)
                .capacity(capacity)
                .build();
    }

    private static InventoryRuntime runtime(InventoryId id, List<InventoryEntry> entries) {
        return new InventoryRuntime(
                id, InventoryStatus.ACTIVE, entries, 0L, InventorySchema.CURRENT_VERSION
        );
    }

    private static ProductionInventoryBinding binding(
            ProductionLineId lineId,
            ProductionBindingDirection direction,
            InventoryId inventoryId,
            GoodId goodId
    ) {
        return new ProductionInventoryBinding(
                lineId, direction, inventoryId, goodId, UnitOfMeasure.EACH
        );
    }
}
