package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.cuttingtable.execution.CuttingTableWorkstationReference;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.product.component.ProductStackData;
import com.butchercraft.product.integration.ProductStackAdapter;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CuttingTableExecutionGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos CUTTING_TABLE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos GRINDER_POS = new BlockPos(3, 1, 2);
    private static final int COMPLETION_TICK = 100;

    private CuttingTableExecutionGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void playerRecipeCompletesThroughExecutionAndScheduler(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        insertShortLoin(helper, cuttingTable);

        helper.runAtTickTime(4, () -> {
            helper.assertTrue(cuttingTable(helper).workstationState() == WorkstationState.PROCESSING,
                    "Cutting Table begins the accepted recipe through its server tick");
            helper.assertTrue(onlyNewOperation(helper, before).status() == ExecutionStatus.AUTHORIZED
                            || onlyNewOperation(helper, before).status() == ExecutionStatus.READY,
                    "Cutting Table publishes one authorized Execution operation");
        });

        helper.runAtTickTime(COMPLETION_TICK, () -> {
            CuttingTableBlockEntity completed = cuttingTable(helper);
            assertCompletedRecipe(helper, completed);
            assertExecutionCompleted(helper, onlyNewOperation(helper, before));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void primaryOutputBlockPreventsInputLoss(GameTestHelper helper) {
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        cuttingTable.inventory().setOutputInternal(0, ModItems.T_BONE_STEAK.get().getDefaultInstance());
        insertShortLoin(helper, cuttingTable);

        helper.runAtTickTime(12, () -> {
            assertBlockedWithoutInputLoss(helper, cuttingTable(helper));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void trimOutputBlockPreventsInputLoss(GameTestHelper helper) {
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        cuttingTable.inventory().setOutputInternal(1, ModItems.BEEF_TRIM.get().getDefaultInstance());
        insertShortLoin(helper, cuttingTable);

        helper.runAtTickTime(12, () -> {
            assertBlockedWithoutInputLoss(helper, cuttingTable(helper));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180)
    public static void bothOutputBlocksPreventInputLoss(GameTestHelper helper) {
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        cuttingTable.inventory().setOutputInternal(0, ModItems.T_BONE_STEAK.get().getDefaultInstance());
        cuttingTable.inventory().setOutputInternal(1, ModItems.BEEF_TRIM.get().getDefaultInstance());
        insertShortLoin(helper, cuttingTable);

        helper.runAtTickTime(12, () -> {
            CuttingTableBlockEntity blocked = cuttingTable(helper);
            assertBlockedWithoutInputLoss(helper, blocked);
            helper.assertTrue(blocked.inventory().getStackInSlot(blocked.primaryOutputSlot())
                            .is(ModItems.T_BONE_STEAK.get()),
                    "Blocked primary output remains unchanged");
            helper.assertTrue(blocked.inventory().getStackInSlot(blocked.trimOutputSlot())
                            .is(ModItems.BEEF_TRIM.get()),
                    "Blocked trim output remains unchanged");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void completedSaveReloadPreservesThreeSlotsWithoutDuplication(GameTestHelper helper) {
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        insertShortLoin(helper, cuttingTable);

        helper.runAtTickTime(COMPLETION_TICK, () -> {
            CuttingTableBlockEntity completed = cuttingTable(helper);
            assertCompletedRecipe(helper, completed);
            CompoundTag saved = completed.saveWithFullMetadata(helper.getLevel().registryAccess());
            CuttingTableBlockEntity restored = replaceCuttingTableBlockEntity(helper, saved);
            assertCompletedRecipe(helper, restored);
        });

        helper.runAtTickTime(COMPLETION_TICK + 40, () -> {
            assertCompletedRecipe(helper, cuttingTable(helper));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void duplicateScheduledCompletionObservesOwnerResultWithoutDuplication(GameTestHelper helper) {
        Set<ExecutionOperationId> before = operationIds(helper);
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        insertShortLoin(helper, cuttingTable);

        helper.runAtTickTime(COMPLETION_TICK, () -> {
            CuttingTableBlockEntity completed = cuttingTable(helper);
            ExecutionOperationSnapshot operation = onlyNewOperation(helper, before);
            assertCompletedRecipe(helper, completed);
            var duplicate = completed.completeScheduledExecution(
                    operation.operationId(),
                    operation.domainEffectIdentity(),
                    operation.lastUpdatedSimulationTick()
            );
            helper.assertTrue(duplicate.accepted(),
                    "Duplicate scheduled completion observes the committed owner result");
            helper.assertTrue(duplicate.ownerResultEvidence().orElseThrow().ownerResultIdentity()
                            .equals(operation.ownerResultEvidence().orElseThrow().ownerResultIdentity()),
                    "Duplicate completion returns the same owner result identity");
            assertCompletedRecipe(helper, completed);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220)
    public static void materialHandlingWithdrawsOnlyRecipeTrimOutput(GameTestHelper helper) {
        CuttingTableBlockEntity cuttingTable = placeCuttingTable(helper);
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        insertShortLoin(helper, cuttingTable);

        helper.runAtTickTime(COMPLETION_TICK, () -> {
            CuttingTableBlockEntity completed = cuttingTable(helper);
            assertCompletedRecipe(helper, completed);
            var transfer = MaterialHandlingService.INSTANCE.requestExplicitTransfer(
                    helper.getLevel(),
                    helper.absolutePos(CUTTING_TABLE_POS),
                    helper.absolutePos(GRINDER_POS)
            );
            helper.assertTrue(transfer.succeeded(), "Material Handling accepts recipe-produced Beef Trim");
            helper.assertTrue(completed.inventory().getStackInSlot(completed.primaryOutputSlot())
                            .is(ModItems.T_BONE_STEAK.get()),
                    "Primary T-Bone Steak remains in the Cutting Table");
            helper.assertTrue(completed.inventory().getStackInSlot(completed.trimOutputSlot()).isEmpty(),
                    "Only the Beef Trim output is withdrawn");
            GrinderBlockEntity grinder = (GrinderBlockEntity) helper.getBlockEntity(GRINDER_POS);
            helper.assertTrue(grinder.inventory().input().is(ModItems.BEEF_TRIM.get())
                            && grinder.inventory().input().getCount() == 1,
                    "Grinder receives exactly one Beef Trim");
            helper.succeed();
        });
    }

    private static CuttingTableBlockEntity placeCuttingTable(GameTestHelper helper) {
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        return cuttingTable(helper);
    }

    private static CuttingTableBlockEntity cuttingTable(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(CUTTING_TABLE_POS);
        helper.assertTrue(blockEntity instanceof CuttingTableBlockEntity,
                "Expected Cutting Table block entity at test position");
        return (CuttingTableBlockEntity) blockEntity;
    }

    private static CuttingTableBlockEntity replaceCuttingTableBlockEntity(
            GameTestHelper helper,
            CompoundTag tag
    ) {
        ServerLevel level = helper.getLevel();
        BlockPos absolutePos = helper.absolutePos(CUTTING_TABLE_POS);
        BlockState state = level.getBlockState(absolutePos);
        BlockEntity loaded = BlockEntity.loadStatic(absolutePos, state, tag, level.registryAccess());
        helper.assertTrue(loaded instanceof CuttingTableBlockEntity,
                "Serialized Cutting Table state reloads as a Cutting Table block entity");
        CuttingTableBlockEntity restored = (CuttingTableBlockEntity) loaded;
        restored.setLevel(level);
        level.removeBlockEntity(absolutePos);
        level.setBlockEntity(restored);
        return restored;
    }

    private static void insertShortLoin(GameTestHelper helper, CuttingTableBlockEntity cuttingTable) {
        ItemStack remainder = cuttingTable.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Beef Short Loin inserts into Cutting Table input");
    }

    private static void assertCompletedRecipe(GameTestHelper helper, CuttingTableBlockEntity cuttingTable) {
        helper.assertTrue(cuttingTable.workstationState() == WorkstationState.COMPLETE,
                "Cutting Table reaches COMPLETE");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(), "Input is consumed exactly once");
        ItemStack primary = cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot());
        ItemStack trim = cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot());
        helper.assertTrue(primary.is(ModItems.T_BONE_STEAK.get()) && primary.getCount() == 1,
                "Primary output contains exactly one T-Bone Steak");
        helper.assertTrue(trim.is(ModItems.BEEF_TRIM.get()) && trim.getCount() == 1,
                "Trim output contains exactly one Beef Trim");
        ProductStackData primaryData = ProductStackAdapter.readProductData(primary).orThrow();
        ProductStackData trimData = ProductStackAdapter.readProductData(trim).orThrow();
        helper.assertTrue(primaryData.productTypeId().equals("butchercraft:t_bone_steak"),
                "Primary output keeps the canonical product identity");
        helper.assertTrue(trimData.productTypeId().equals("butchercraft:beef_trim"),
                "Byproduct keeps the canonical Beef Trim identity");
    }

    private static void assertBlockedWithoutInputLoss(
            GameTestHelper helper,
            CuttingTableBlockEntity cuttingTable
    ) {
        helper.assertTrue(cuttingTable.workstationState() == WorkstationState.BLOCKED,
                "Occupied output blocks the recipe");
        helper.assertTrue(cuttingTable.lastFailure().orElseThrow().code() == WorkstationFailureCode.OUTPUT_OCCUPIED,
                "Blocked recipe reports output occupied");
        helper.assertTrue(cuttingTable.inventory().input().is(ModItems.BEEF_SHORT_LOIN.get())
                        && cuttingTable.inventory().input().getCount() == 1,
                "Blocked output never consumes the input");
    }

    private static Set<ExecutionOperationId> operationIds(GameTestHelper helper) {
        Set<ExecutionOperationId> result = new HashSet<>();
        operations(helper).forEach(operation -> result.add(operation.operationId()));
        return Set.copyOf(result);
    }

    private static List<ExecutionOperationSnapshot> operations(GameTestHelper helper) {
        String workstationIdentity = CuttingTableWorkstationReference.of(
                helper.getLevel(),
                helper.absolutePos(CUTTING_TABLE_POS)
        ).identity();
        return ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer()).operations().stream()
                .filter(operation -> operation.authorizationEvidence().executableWorkReferenceId()
                        .equals(workstationIdentity))
                .toList();
    }

    private static ExecutionOperationSnapshot onlyNewOperation(
            GameTestHelper helper,
            Set<ExecutionOperationId> before
    ) {
        List<ExecutionOperationSnapshot> operations = operations(helper).stream()
                .filter(operation -> !before.contains(operation.operationId()))
                .toList();
        helper.assertTrue(operations.size() == 1,
                "Expected exactly one new Cutting Table Execution operation, found " + operations.size());
        return operations.getFirst();
    }

    private static void assertExecutionCompleted(
            GameTestHelper helper,
            ExecutionOperationSnapshot operation
    ) {
        ExecutionOperationSnapshot current = ExecutionService.INSTANCE
                .managerFor(helper.getLevel().getServer())
                .find(operation.operationId())
                .orElseThrow();
        helper.assertTrue(current.status() == ExecutionStatus.SUCCEEDED,
                "Cutting Table Execution succeeds after owner result publication");
        helper.assertTrue(current.ownerResultEvidence().isPresent() && current.resultEvidence().isPresent(),
                "Cutting Table Execution records owner and result evidence");
        var runtime = SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer())
                .runtimeFor(SimulationWorkId.of(current.operationId().value() + "/work"))
                .orElseThrow();
        helper.assertTrue(runtime.status() == SimulationWorkStatus.COMPLETED,
                "Scheduler completes after observing the Cutting Table owner result");
    }
}
