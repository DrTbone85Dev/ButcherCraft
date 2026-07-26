package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.GrinderWorkstation;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.processing.definition.BuiltInDefinitionIds;
import com.butchercraft.processing.definition.DefinitionRegistryView;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationOperationResolver;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.execution.ExecutionManager;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRuntime;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GrinderExecutionGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos GRINDER_POS = new BlockPos(2, 1, 2);
    private static final int COMPLETION_ASSERTION_TICK = 100;
    private static final int EXTENDED_ASSERTION_TICK = 130;
    private static final List<RecipeCase> PROMOTED_RECIPES = List.of(
            new RecipeCase("Beef", ModItems.BEEF_TRIM, ModItems.GROUND_BEEF,
                    BuiltInDefinitionIds.GRIND_BEEF, "butchercraft:beef_trim", "butchercraft:ground_beef", "butchercraft:beef"),
            new RecipeCase("Pork", ModItems.PORK_TRIM, ModItems.GROUND_PORK,
                    BuiltInDefinitionIds.GRIND_PORK, "butchercraft:pork_trim", "butchercraft:ground_pork", "butchercraft:pork"),
            new RecipeCase("Chicken", ModItems.CHICKEN_TRIM, ModItems.GROUND_CHICKEN,
                    BuiltInDefinitionIds.GRIND_CHICKEN, "butchercraft:chicken_trim", "butchercraft:ground_chicken", "butchercraft:chicken"),
            new RecipeCase("Buffalo", ModItems.BUFFALO_TRIM, ModItems.GROUND_BUFFALO,
                    BuiltInDefinitionIds.GRIND_BISON, "butchercraft:bison_trim", "butchercraft:ground_bison", "butchercraft:bison"),
            new RecipeCase("Lamb", ModItems.LAMB_TRIM, ModItems.GROUND_LAMB,
                    BuiltInDefinitionIds.GRIND_LAMB, "butchercraft:lamb_trim", "butchercraft:ground_lamb", "butchercraft:lamb"),
            new RecipeCase("Venison", ModItems.VENISON_TRIM, ModItems.GROUND_VENISON,
                    BuiltInDefinitionIds.GRIND_VENISON, "butchercraft:venison_trim", "butchercraft:ground_venison", "butchercraft:venison")
    );

    private GrinderExecutionGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void registrationSmokeDiscoversButchercraftGameTests(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer() != null, "GameTest server is available");
        helper.assertTrue(ModBlocks.GRINDER.get() != null, "Grinder block is registered");
        helper.assertTrue(ModItems.GRINDER.get() != null, "Grinder item is registered");
        helper.assertTrue(ModItems.BEEF_TRIM.get() != null, "Beef Trim gameplay item is registered");
        helper.assertTrue(ModItems.GROUND_BEEF.get() != null, "Ground Beef gameplay item is registered");
        helper.assertTrue(ModItems.PORK_TRIM.get() != null, "Pork Trim gameplay item is registered");
        helper.assertTrue(ModItems.GROUND_PORK.get() != null, "Ground Pork gameplay item is registered");
        helper.assertTrue(ModItems.CHICKEN_TRIM.get() != null, "Chicken Trim gameplay item is registered");
        helper.assertTrue(ModItems.GROUND_CHICKEN.get() != null, "Ground Chicken gameplay item is registered");
        helper.assertTrue(ModItems.BUFFALO_TRIM.get() != null, "Buffalo Trim gameplay item is registered");
        helper.assertTrue(ModItems.GROUND_BUFFALO.get() != null, "Ground Buffalo gameplay item is registered");
        helper.assertTrue(ModItems.LAMB_TRIM.get() != null, "Lamb Trim gameplay item is registered");
        helper.assertTrue(ModItems.GROUND_LAMB.get() != null, "Ground Lamb gameplay item is registered");
        helper.assertTrue(ModItems.VENISON_TRIM.get() != null, "Venison Trim gameplay item is registered");
        helper.assertTrue(ModItems.GROUND_VENISON.get() != null, "Ground Venison gameplay item is registered");
        helper.assertTrue(ModItems.BEEF_TRIM.get() == ModItems.BEEF_TRIM_TEST.get(),
                "Legacy Beef Trim registry identity remains compatible");
        helper.assertTrue(ModItems.GROUND_BEEF.get() == ModItems.GROUND_BEEF_TEST.get(),
                "Legacy Ground Beef registry identity remains compatible");
        helper.assertTrue(ModItems.PORK_TRIM.get() == ModItems.PORK_TRIM_TEST.get(),
                "Legacy Pork Trim registry identity remains compatible");
        helper.assertTrue(ModItems.GROUND_PORK.get() == ModItems.GROUND_PORK_TEST.get(),
                "Legacy Ground Pork registry identity remains compatible");
        helper.assertTrue(ModItems.BUFFALO_TRIM.get() == ModItems.BISON_TRIM_TEST.get(),
                "Legacy Bison Trim registry identity remains compatible for Buffalo presentation");
        helper.assertTrue(ModItems.GROUND_BUFFALO.get() == ModItems.GROUND_BISON_TEST.get(),
                "Legacy Ground Bison registry identity remains compatible for Ground Buffalo presentation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void placementCreatesIdleServerBlockEntity(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);

        helper.assertTrue(grinder.workstationState() == WorkstationState.IDLE,
                "Placed grinder starts in IDLE state");
        helper.assertTrue(grinder.inventory().input().isEmpty(), "Placed grinder input starts empty");
        helper.assertTrue(grinder.inventory().output().isEmpty(), "Placed grinder output starts empty");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void validOperationCompletesThroughLiveExecution(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(4, () -> {
            GrinderBlockEntity active = grinder(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    "Grinder begins processing through server block-entity tick");
            ExecutionOperationSnapshot operation = onlyNewOperation(helper, before);
            helper.assertTrue(operation.status() == ExecutionStatus.AUTHORIZED
                            || operation.status() == ExecutionStatus.READY,
                    "Execution operation is authorized before scheduler dispatch");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundBeef(helper, completed);
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void validSecondProcessCompletesThroughLiveExecution(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertPorkTrim(helper, grinder);

        helper.runAtTickTime(4, () -> {
            GrinderBlockEntity active = grinder(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    "Pork Trim begins processing through server block-entity tick");
            ExecutionOperationSnapshot operation = onlyNewOperation(helper, before);
            helper.assertTrue(operation.status() == ExecutionStatus.AUTHORIZED
                            || operation.status() == ExecutionStatus.READY,
                    "Pork Execution operation is authorized before scheduler dispatch");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundPork(helper, completed);
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void processIsolationPreventsCrossOutput(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertPorkTrim(helper, grinder);

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundPork(helper, completed);
            helper.assertTrue(completed.inventory().output().getItem() == ModItems.GROUND_PORK.get(),
                    "Pork process output uses Ground Pork item");
            helper.assertFalse(completed.inventory().output().getItem() == ModItems.GROUND_BEEF.get(),
                    "Pork process never creates Ground Beef item");
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void allPromotedProductsCoexistAndResolveDeterministically(GameTestHelper helper) {
        var loadResult = DefinitionRegistryView.fromRegistryAccess(helper.getLevel().registryAccess());
        helper.assertTrue(loadResult.allRegistriesAvailable(), "Definition registries are available");
        WorkstationOperationResolver resolver = new WorkstationOperationResolver();

        for (RecipeCase recipe : PROMOTED_RECIPES) {
            var resolution = resolver.resolve(
                    loadResult.view(),
                    GrinderWorkstation.capability(),
                    recipe.inputStack()
            );
            helper.assertTrue(resolution.succeeded(), recipe.label() + " recipe resolves");
            helper.assertTrue(resolution.operation().orElseThrow().operationId().equals(recipe.operationId()),
                    recipe.label() + " resolves to the expected operation");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void unsupportedInputIsRejectedVisibly(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        ItemStack remainder = grinder.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                new ItemStack(ModItems.DEVELOPMENT_TEST_ITEM.get()),
                false
        );

        helper.assertFalse(remainder.isEmpty(), "Unsupported non-product item is rejected by the input slot");
        helper.assertTrue(grinder.workstationState() == WorkstationState.IDLE,
                "Rejected unsupported item does not start processing");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void validChickenProcessCompletesThroughLiveExecution(GameTestHelper helper) {
        assertRecipeCompletesThroughLiveExecution(helper, recipe("Chicken"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void validBuffaloProcessCompletesThroughLiveExecution(GameTestHelper helper) {
        assertRecipeCompletesThroughLiveExecution(helper, recipe("Buffalo"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void validLambProcessCompletesThroughLiveExecution(GameTestHelper helper) {
        assertRecipeCompletesThroughLiveExecution(helper, recipe("Lamb"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void validVenisonProcessCompletesThroughLiveExecution(GameTestHelper helper) {
        assertRecipeCompletesThroughLiveExecution(helper, recipe("Venison"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void chickenDuplicateInteractionRemainsSafe(GameTestHelper helper) {
        assertRecipeDuplicateInteractionSafe(helper, recipe("Chicken"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void buffaloDuplicateInteractionRemainsSafe(GameTestHelper helper) {
        assertRecipeDuplicateInteractionSafe(helper, recipe("Buffalo"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void lambDuplicateInteractionRemainsSafe(GameTestHelper helper) {
        assertRecipeDuplicateInteractionSafe(helper, recipe("Lamb"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void venisonDuplicateInteractionRemainsSafe(GameTestHelper helper) {
        assertRecipeDuplicateInteractionSafe(helper, recipe("Venison"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void chickenSerializationResumesSafely(GameTestHelper helper) {
        assertRecipeSerializationResumesSafely(helper, recipe("Chicken"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void buffaloSerializationResumesSafely(GameTestHelper helper) {
        assertRecipeSerializationResumesSafely(helper, recipe("Buffalo"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void lambSerializationResumesSafely(GameTestHelper helper) {
        assertRecipeSerializationResumesSafely(helper, recipe("Lamb"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void venisonSerializationResumesSafely(GameTestHelper helper) {
        assertRecipeSerializationResumesSafely(helper, recipe("Venison"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void chickenBlockedOutputFailsBeforeCommit(GameTestHelper helper) {
        assertRecipeBlockedOutputFailsBeforeCommit(helper, recipe("Chicken"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void buffaloBlockedOutputFailsBeforeCommit(GameTestHelper helper) {
        assertRecipeBlockedOutputFailsBeforeCommit(helper, recipe("Buffalo"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void lambBlockedOutputFailsBeforeCommit(GameTestHelper helper) {
        assertRecipeBlockedOutputFailsBeforeCommit(helper, recipe("Lamb"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void venisonBlockedOutputFailsBeforeCommit(GameTestHelper helper) {
        assertRecipeBlockedOutputFailsBeforeCommit(helper, recipe("Venison"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void chickenProcessPreventsWrongOutput(GameTestHelper helper) {
        assertRecipePreventsWrongOutput(helper, recipe("Chicken"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void buffaloProcessPreventsWrongOutput(GameTestHelper helper) {
        assertRecipePreventsWrongOutput(helper, recipe("Buffalo"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void lambProcessPreventsWrongOutput(GameTestHelper helper) {
        assertRecipePreventsWrongOutput(helper, recipe("Lamb"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void venisonProcessPreventsWrongOutput(GameTestHelper helper) {
        assertRecipePreventsWrongOutput(helper, recipe("Venison"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void promotedMenuDataShowsProcessingProgress(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            GrinderBlockEntity active = grinder(helper);
            helper.assertTrue(active.menuData().get(0) == WorkstationState.PROCESSING.ordinal(),
                    "Server menu data exposes PROCESSING status");
            helper.assertTrue(active.menuData().get(1) > 0 && active.menuData().get(1) < 60,
                    "Server menu data exposes bounded processing progress");
            helper.assertTrue(active.menuData().get(2) == 60,
                    "Server menu data exposes the 60-tick grind duration");
            helper.assertTrue(active.menuData().get(3) == -1,
                    "Server menu data exposes no failure during normal processing");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void secondProcessDuplicateInteractionRemainsSafe(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertPorkTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            GrinderBlockEntity active = grinder(helper);
            ItemStack duplicateRemainder = active.inventory().insertItem(
                    WorkstationInventory.INPUT_SLOT,
                    porkTrim(),
                    false
            );
            helper.assertFalse(duplicateRemainder.isEmpty(), "Second Pork Trim insertion is rejected while slot is occupied");
            helper.useBlock(GRINDER_POS);
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Repeated use while Pork Trim is processing does not create another Execution operation");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundPork(helper, completed);
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Only one Execution operation exists for the repeated Pork Trim initiation attempt");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void repeatedInputAndUseWhileProcessingDoNotDuplicateExecution(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            GrinderBlockEntity active = grinder(helper);
            ItemStack duplicateRemainder = active.inventory().insertItem(
                    WorkstationInventory.INPUT_SLOT,
                    beefTrim(),
                    false
            );
            helper.assertFalse(duplicateRemainder.isEmpty(), "Second input insertion is rejected while slot is occupied");
            helper.useBlock(GRINDER_POS);
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Repeated use while processing does not create another Execution operation");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundBeef(helper, completed);
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Only one Execution operation exists for the repeated initiation attempt");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140)
    public static void breakingActiveSecondProcessDropsInputWithoutOutput(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertPorkTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            helper.assertTrue(grinder(helper).workstationState() == WorkstationState.PROCESSING,
                    "Grinder is actively processing Pork Trim before block break");
            helper.getLevel().destroyBlock(helper.absolutePos(GRINDER_POS), true);
        });

        helper.runAtTickTime(14, () -> {
            helper.assertBlockNotPresent(ModBlocks.GRINDER.get(), GRINDER_POS);
            helper.assertItemEntityPresent(ModItems.GRINDER.get(), GRINDER_POS, 3.0);
            helper.assertItemEntityPresent(ModItems.PORK_TRIM.get(), GRINDER_POS, 3.0);
            helper.assertItemEntityNotPresent(ModItems.GROUND_PORK.get(), GRINDER_POS, 3.0);
            helper.assertItemEntityNotPresent(ModItems.GROUND_BEEF.get(), GRINDER_POS, 3.0);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140)
    public static void breakingActiveGrinderDropsInputWithoutOutput(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            helper.assertTrue(grinder(helper).workstationState() == WorkstationState.PROCESSING,
                    "Grinder is actively processing before block break");
            helper.getLevel().destroyBlock(helper.absolutePos(GRINDER_POS), true);
        });

        helper.runAtTickTime(14, () -> {
            helper.assertBlockNotPresent(ModBlocks.GRINDER.get(), GRINDER_POS);
            helper.assertItemEntityPresent(ModItems.GRINDER.get(), GRINDER_POS, 3.0);
            helper.assertItemEntityPresent(ModItems.BEEF_TRIM.get(), GRINDER_POS, 3.0);
            helper.assertItemEntityNotPresent(ModItems.GROUND_BEEF.get(), GRINDER_POS, 3.0);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void continuedTicksAfterCompletionDoNotRedispatch(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundBeef(helper, completed);
            ExecutionOperationSnapshot operation = onlyNewOperation(helper, before);
            assertCompletedExecutionAndScheduler(helper, operation);
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Completed grinder does not create another Execution operation on later ticks");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void menuCloseDoesNotCancelProcessing(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            var player = helper.makeMockPlayer(GameType.CREATIVE);
            helper.useBlock(GRINDER_POS, player);
            player.closeContainer();
            helper.assertTrue(grinder(helper).workstationState() == WorkstationState.PROCESSING,
                    "Closing the menu does not cancel active processing");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            assertCompletedGroundBeef(helper, grinder(helper));
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void secondProcessSerializationResumesSafely(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertPorkTrim(helper, grinder);

        helper.runAtTickTime(16, () -> {
            GrinderBlockEntity active = grinder(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    "Pork Trim process is active before serialization");
            CompoundTag saved = active.saveWithFullMetadata(helper.getLevel().registryAccess());
            GrinderBlockEntity restored = replaceBlockEntity(helper, saved);
            helper.assertTrue(restored.workstationState() == WorkstationState.PROCESSING,
                    "Restored Pork Trim grinder preserves active processing state");
            helper.assertTrue(elapsedTicks(restored) > 0 && elapsedTicks(restored) < totalTicks(restored),
                    "Restored Pork Trim grinder preserves bounded progress before effect publication");
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundPork(helper, completed);
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void preEffectBlockEntitySaveLoadResumesThroughExecution(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(16, () -> {
            GrinderBlockEntity active = grinder(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    "Grinder is processing before serialization");
            CompoundTag saved = active.saveWithFullMetadata(helper.getLevel().registryAccess());
            GrinderBlockEntity restored = replaceBlockEntity(helper, saved);
            helper.assertTrue(restored.workstationState() == WorkstationState.PROCESSING,
                    "Restored grinder preserves active processing state");
            helper.assertTrue(elapsedTicks(restored) > 0 && elapsedTicks(restored) < totalTicks(restored),
                    "Restored grinder preserves bounded progress before effect publication");
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundBeef(helper, completed);
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260)
    public static void completedBlockEntitySaveLoadDoesNotDuplicateOutput(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedGroundBeef(helper, completed);
            replaceBlockEntity(helper, completed.saveWithFullMetadata(helper.getLevel().registryAccess()));
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            GrinderBlockEntity restored = grinder(helper);
            assertCompletedGroundBeef(helper, restored);
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void changedInputDuringProcessingBlocksVisibly(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> grinder(helper).inventory().setInputInternal(porkTrim()));

        helper.runAtTickTime(20, () -> {
            GrinderBlockEntity blocked = grinder(helper);
            assertBlockedWith(helper, blocked, WorkstationFailureCode.PRODUCT_DATA_MISMATCH);
            helper.assertTrue(blocked.inventory().output().isEmpty(), "Changed input does not produce output");
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Changed input does not create a replacement Execution operation");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void blockedOutputDuringProcessingFailsBeforeCommit(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> grinder(helper).inventory().setOutputInternal(groundBeef()));

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            GrinderBlockEntity blocked = grinder(helper);
            assertBlockedWith(helper, blocked, WorkstationFailureCode.OUTPUT_OCCUPIED);
            helper.assertFalse(blocked.inventory().input().isEmpty(), "Blocked completion preserves input");
            helper.assertFalse(blocked.inventory().output().isEmpty(), "Blocked completion preserves occupied output");
            ExecutionOperationSnapshot operation = onlyNewOperation(helper, before);
            helper.assertTrue(operation.status() == ExecutionStatus.FAILED,
                    "Execution operation fails visibly when owner commit is blocked");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void malformedRestoredStateStopsVisibly(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        grinder.inventory().setInputInternal(beefTrim());
        CompoundTag saved = grinder.saveWithFullMetadata(helper.getLevel().registryAccess());
        CompoundTag controller = saved.getCompound("Controller");
        controller.putString("State", WorkstationState.PROCESSING.name());
        controller.putInt("ElapsedTicks", 12);
        controller.putInt("TotalTicks", 60);
        controller.remove("SelectedOperation");
        saved.put("Controller", controller);

        GrinderBlockEntity restored = replaceBlockEntity(helper, saved);

        helper.assertTrue(restored.workstationState() == WorkstationState.ERROR,
                "Malformed active state is restored as ERROR");
        helper.assertTrue(restored.lastFailure().orElseThrow().code() == WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                "Malformed active state exposes invalid workstation failure");
        helper.assertFalse(restored.inventory().input().isEmpty(), "Malformed restore preserves input for recovery");
        helper.assertTrue(restored.inventory().output().isEmpty(), "Malformed restore does not publish output");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void uncertainCommittedRestoreStopsVisibly(GameTestHelper helper) {
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertBeefTrim(helper, grinder);

        helper.runAtTickTime(8, () -> {
            CompoundTag saved = grinder(helper).saveWithFullMetadata(helper.getLevel().registryAccess());
            CompoundTag controller = saved.getCompound("Controller");
            controller.putBoolean("CompletionCommitted", true);
            saved.put("Controller", controller);
            GrinderBlockEntity restored = replaceBlockEntity(helper, saved);
            helper.assertTrue(restored.workstationState() == WorkstationState.ERROR,
                    "Unresolved committed effect restores as ERROR");
            helper.assertTrue(restored.lastFailure().orElseThrow().code()
                            == WorkstationFailureCode.INVALID_WORKSTATION_STATE,
                    "Unresolved committed effect exposes invalid workstation failure");
            helper.assertFalse(restored.inventory().input().isEmpty(),
                    "Uncertain committed restore preserves input for recovery");
            helper.assertTrue(restored.inventory().output().isEmpty(),
                    "Uncertain committed restore does not publish partial output");
        });

        helper.runAtTickTime(20, () -> {
            GrinderBlockEntity restored = grinder(helper);
            helper.assertTrue(restored.workstationState() == WorkstationState.ERROR,
                    "Uncertain committed restore remains stopped");
            helper.succeed();
        });
    }

    private static void assertRecipeCompletesThroughLiveExecution(GameTestHelper helper, RecipeCase recipe) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertRecipeInput(helper, grinder, recipe);

        helper.runAtTickTime(4, () -> {
            GrinderBlockEntity active = grinder(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    recipe.label() + " begins processing through server block-entity tick");
            helper.assertTrue(active.productionSnapshot().selectedOperationId().orElseThrow().equals(recipe.operationId()),
                    recipe.label() + " freezes the selected operation identity");
            ExecutionOperationSnapshot operation = onlyNewOperation(helper, before);
            helper.assertTrue(operation.status() == ExecutionStatus.AUTHORIZED
                            || operation.status() == ExecutionStatus.READY,
                    recipe.label() + " Execution operation is authorized before scheduler dispatch");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedRecipe(helper, completed, recipe);
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    private static void assertRecipeDuplicateInteractionSafe(GameTestHelper helper, RecipeCase recipe) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertRecipeInput(helper, grinder, recipe);

        helper.runAtTickTime(8, () -> {
            GrinderBlockEntity active = grinder(helper);
            ItemStack duplicateRemainder = active.inventory().insertItem(
                    WorkstationInventory.INPUT_SLOT,
                    recipe.inputStack(),
                    false
            );
            helper.assertFalse(duplicateRemainder.isEmpty(),
                    "Second " + recipe.label() + " Trim insertion is rejected while slot is occupied");
            helper.useBlock(GRINDER_POS);
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Repeated use while " + recipe.label() + " is processing does not create another Execution operation");
        });

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedRecipe(helper, completed, recipe);
            helper.assertTrue(newOperations(helper, before).size() == 1,
                    "Only one Execution operation exists for the repeated " + recipe.label() + " initiation attempt");
            helper.succeed();
        });
    }

    private static void assertRecipeSerializationResumesSafely(GameTestHelper helper, RecipeCase recipe) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertRecipeInput(helper, grinder, recipe);

        helper.runAtTickTime(16, () -> {
            GrinderBlockEntity active = grinder(helper);
            helper.assertTrue(active.workstationState() == WorkstationState.PROCESSING,
                    recipe.label() + " process is active before serialization");
            CompoundTag saved = active.saveWithFullMetadata(helper.getLevel().registryAccess());
            GrinderBlockEntity restored = replaceBlockEntity(helper, saved);
            helper.assertTrue(restored.workstationState() == WorkstationState.PROCESSING,
                    "Restored " + recipe.label() + " grinder preserves active processing state");
            helper.assertTrue(restored.productionSnapshot().selectedOperationId().orElseThrow().equals(recipe.operationId()),
                    "Restored " + recipe.label() + " grinder preserves selected operation identity");
            helper.assertTrue(elapsedTicks(restored) > 0 && elapsedTicks(restored) < totalTicks(restored),
                    "Restored " + recipe.label() + " grinder preserves bounded progress before effect publication");
        });

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedRecipe(helper, completed, recipe);
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    private static void assertRecipeBlockedOutputFailsBeforeCommit(GameTestHelper helper, RecipeCase recipe) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertRecipeInput(helper, grinder, recipe);

        helper.runAtTickTime(8, () -> grinder(helper).inventory().setOutputInternal(recipe.outputStack()));

        helper.runAtTickTime(EXTENDED_ASSERTION_TICK, () -> {
            GrinderBlockEntity blocked = grinder(helper);
            assertBlockedWith(helper, blocked, WorkstationFailureCode.OUTPUT_OCCUPIED);
            helper.assertFalse(blocked.inventory().input().isEmpty(), "Blocked completion preserves input");
            helper.assertFalse(blocked.inventory().output().isEmpty(), "Blocked completion preserves occupied output");
            assertProductId(helper, blocked.inventory().input(), recipe.inputProductId(), "Blocked input remains " + recipe.label() + " Trim");
            assertProductId(helper, blocked.inventory().output(), recipe.outputProductId(), "Blocked output remains " + recipe.label() + " output");
            ExecutionOperationSnapshot operation = onlyNewOperation(helper, before);
            helper.assertTrue(operation.status() == ExecutionStatus.FAILED,
                    "Execution operation fails visibly when owner commit is blocked");
            helper.succeed();
        });
    }

    private static void assertRecipePreventsWrongOutput(GameTestHelper helper, RecipeCase recipe) {
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insertRecipeInput(helper, grinder, recipe);

        helper.runAtTickTime(COMPLETION_ASSERTION_TICK, () -> {
            GrinderBlockEntity completed = grinder(helper);
            assertCompletedRecipe(helper, completed, recipe);
            for (RecipeCase other : PROMOTED_RECIPES) {
                if (!other.outputProductId().equals(recipe.outputProductId())) {
                    helper.assertFalse(completed.inventory().output().getItem() == other.output().get(),
                            recipe.label() + " process never creates " + other.label() + " output");
                }
            }
            assertCompletedExecutionAndScheduler(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    private static GrinderBlockEntity placeGrinder(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        return grinder(helper);
    }

    private static GrinderBlockEntity grinder(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(GRINDER_POS));
        helper.assertTrue(blockEntity instanceof GrinderBlockEntity,
                "Expected grinder block entity at test position");
        return (GrinderBlockEntity) blockEntity;
    }

    private static void insertBeefTrim(GameTestHelper helper, GrinderBlockEntity grinder) {
        ItemStack remainder = grinder.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                beefTrim(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Beef Trim inserts into grinder input");
    }

    private static void insertPorkTrim(GameTestHelper helper, GrinderBlockEntity grinder) {
        ItemStack remainder = grinder.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                porkTrim(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Pork Trim inserts into grinder input");
    }

    private static void insertRecipeInput(GameTestHelper helper, GrinderBlockEntity grinder, RecipeCase recipe) {
        ItemStack remainder = grinder.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                recipe.inputStack(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), recipe.label() + " Trim inserts into grinder input");
    }

    private static ItemStack beefTrim() {
        return ModItems.BEEF_TRIM.get().getDefaultInstance();
    }

    private static ItemStack porkTrim() {
        return ModItems.PORK_TRIM.get().getDefaultInstance();
    }

    private static ItemStack groundBeef() {
        return ModItems.GROUND_BEEF.get().getDefaultInstance();
    }

    private static RecipeCase recipe(String label) {
        return PROMOTED_RECIPES.stream()
                .filter(recipe -> recipe.label().equals(label))
                .findFirst()
                .orElseThrow();
    }

    private static void assertCompletedGroundBeef(GameTestHelper helper, GrinderBlockEntity grinder) {
        helper.assertTrue(grinder.workstationState() == WorkstationState.COMPLETE,
                "Grinder reaches COMPLETE state");
        helper.assertTrue(grinder.inventory().input().isEmpty(), "Input is consumed exactly once");
        ItemStack output = grinder.inventory().output();
        helper.assertFalse(output.isEmpty(), "Output is present after completion");
        helper.assertTrue(output.getCount() == 1, "Output stack count remains one");
        ProductStackData data = ProductStackAdapter.readProductData(output).orThrow();
        helper.assertTrue(Objects.equals("butchercraft:ground_beef", data.productTypeId()),
                "Output product type is ground beef");
        helper.assertTrue(Objects.equals("butchercraft:beef", data.sourceCategoryId()),
                "Output source category remains beef");
        helper.assertTrue(data.quantityValue() == 900, "Output quantity is 900 grams");
        helper.assertTrue(data.qualityScore() == 695, "Output quality adjustment is deterministic");
    }

    private static void assertCompletedGroundPork(GameTestHelper helper, GrinderBlockEntity grinder) {
        helper.assertTrue(grinder.workstationState() == WorkstationState.COMPLETE,
                "Grinder reaches COMPLETE state");
        helper.assertTrue(grinder.inventory().input().isEmpty(), "Input is consumed exactly once");
        ItemStack output = grinder.inventory().output();
        helper.assertFalse(output.isEmpty(), "Output is present after completion");
        helper.assertTrue(output.getCount() == 1, "Output stack count remains one");
        helper.assertTrue(output.getItem() == ModItems.GROUND_PORK.get(),
                "Output item is Ground Pork");
        ProductStackData data = ProductStackAdapter.readProductData(output).orThrow();
        helper.assertTrue(Objects.equals("butchercraft:ground_pork", data.productTypeId()),
                "Output product type is ground pork");
        helper.assertTrue(Objects.equals("butchercraft:pork", data.sourceCategoryId()),
                "Output source category remains pork");
        helper.assertTrue(data.quantityValue() == 900, "Output quantity is 900 grams");
        helper.assertTrue(data.qualityScore() == 695, "Output quality adjustment is deterministic");
    }

    private static void assertCompletedRecipe(GameTestHelper helper, GrinderBlockEntity grinder, RecipeCase recipe) {
        helper.assertTrue(grinder.workstationState() == WorkstationState.COMPLETE,
                "Grinder reaches COMPLETE state");
        helper.assertTrue(grinder.inventory().input().isEmpty(), "Input is consumed exactly once");
        ItemStack output = grinder.inventory().output();
        helper.assertFalse(output.isEmpty(), "Output is present after completion");
        helper.assertTrue(output.getCount() == 1, "Output stack count remains one");
        helper.assertTrue(output.getItem() == recipe.output().get(),
                "Output item is " + recipe.label() + " ground product");
        ProductStackData data = ProductStackAdapter.readProductData(output).orThrow();
        helper.assertTrue(Objects.equals(recipe.outputProductId(), data.productTypeId()),
                "Output product type is " + recipe.outputProductId());
        helper.assertTrue(Objects.equals(recipe.sourceId(), data.sourceCategoryId()),
                "Output source category remains " + recipe.sourceId());
        helper.assertTrue(data.quantityValue() == 900, "Output quantity is 900 grams");
        helper.assertTrue(data.qualityScore() == 695, "Output quality adjustment is deterministic");
    }

    private static void assertProductId(GameTestHelper helper, ItemStack stack, String productId, String message) {
        ProductStackData data = ProductStackAdapter.readProductData(stack).orThrow();
        helper.assertTrue(Objects.equals(productId, data.productTypeId()), message);
    }

    private static void assertBlockedWith(
            GameTestHelper helper,
            GrinderBlockEntity grinder,
            WorkstationFailureCode failureCode
    ) {
        helper.assertTrue(grinder.workstationState() == WorkstationState.BLOCKED,
                "Grinder reaches BLOCKED state");
        helper.assertTrue(grinder.lastFailure().orElseThrow().code() == failureCode,
                "Grinder reports expected failure " + failureCode.reasonCode());
    }

    private static Set<ExecutionOperationId> operationIds(GameTestHelper helper) {
        Set<ExecutionOperationId> ids = new HashSet<>();
        for (ExecutionOperationSnapshot operation : operationsForGrinder(helper)) {
            ids.add(operation.operationId());
        }
        return Set.copyOf(ids);
    }

    private static List<ExecutionOperationSnapshot> operationsForGrinder(GameTestHelper helper) {
        String workstationIdentity = workstationIdentity(helper);
        return execution(helper).operations().stream()
                .filter(operation -> operation.authorizationEvidence()
                        .executableWorkReferenceId()
                        .equals(workstationIdentity))
                .toList();
    }

    private static List<ExecutionOperationSnapshot> newOperations(
            GameTestHelper helper,
            Set<ExecutionOperationId> before
    ) {
        return operationsForGrinder(helper).stream()
                .filter(operation -> !before.contains(operation.operationId()))
                .toList();
    }

    private static ExecutionOperationSnapshot onlyNewOperation(
            GameTestHelper helper,
            Set<ExecutionOperationId> before
    ) {
        List<ExecutionOperationSnapshot> operations = newOperations(helper, before);
        helper.assertTrue(operations.size() == 1,
                "Expected exactly one new Execution operation, found " + operations.size());
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

    private static GrinderBlockEntity replaceBlockEntity(GameTestHelper helper, CompoundTag tag) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(GRINDER_POS);
        BlockState state = level.getBlockState(absolutePos);
        BlockEntity loaded = BlockEntity.loadStatic(
                absolutePos,
                state,
                tag,
                level.registryAccess()
        );
        helper.assertTrue(loaded instanceof GrinderBlockEntity,
                "Serialized grinder state reloads as grinder block entity");
        GrinderBlockEntity restored = (GrinderBlockEntity) loaded;
        restored.setLevel(level);
        level.removeBlockEntity(absolutePos);
        level.setBlockEntity(restored);
        return restored;
    }

    private static int elapsedTicks(GrinderBlockEntity grinder) {
        return grinder.menuData().get(1);
    }

    private static int totalTicks(GrinderBlockEntity grinder) {
        return grinder.menuData().get(2);
    }

    private static ExecutionManager execution(GameTestHelper helper) {
        return ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer());
    }

    private static SimulationSchedulerManager scheduler(GameTestHelper helper) {
        return SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer());
    }

    private static String workstationIdentity(GameTestHelper helper) {
        return GrinderWorkstationReference.of(helper.getLevel(), helper.absolutePos(GRINDER_POS)).identity();
    }

    private static SimulationWorkId workIdFor(ExecutionOperationId operationId) {
        return SimulationWorkId.of(operationId.value() + "/work");
    }

    private record RecipeCase(
            String label,
            DeferredItem<? extends Item> input,
            DeferredItem<? extends Item> output,
            ResourceLocation operationId,
            String inputProductId,
            String outputProductId,
            String sourceId
    ) {
        private ItemStack inputStack() {
            return input.get().getDefaultInstance();
        }

        private ItemStack outputStack() {
            return output.get().getDefaultInstance();
        }
    }
}
