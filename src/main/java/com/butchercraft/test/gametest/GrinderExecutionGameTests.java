package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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

    private GrinderExecutionGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 160)
    public static void registrationSmokeDiscoversButchercraftGameTests(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getServer() != null, "GameTest server is available");
        helper.assertTrue(ModBlocks.GRINDER.get() != null, "Grinder block is registered");
        helper.assertTrue(ModItems.BEEF_TRIM_TEST.get() != null, "Beef trim fixture item is registered");
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
        helper.assertTrue(remainder.isEmpty(), "Beef trim fixture inserts into grinder input");
    }

    private static ItemStack beefTrim() {
        return ModItems.BEEF_TRIM_TEST.get().getDefaultInstance();
    }

    private static ItemStack porkTrim() {
        return ModItems.PORK_TRIM_TEST.get().getDefaultInstance();
    }

    private static ItemStack groundBeef() {
        return ModItems.GROUND_BEEF_TEST.get().getDefaultInstance();
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
}
