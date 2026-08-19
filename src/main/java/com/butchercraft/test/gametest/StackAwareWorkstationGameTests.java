package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerMenu;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.operation.MachineOperatingState;
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

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StackAwareWorkstationGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos GRINDER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PATTY_FORMER_POS = new BlockPos(3, 1, 1);
    private static final BlockPos CUTTING_TABLE_POS = new BlockPos(2, 1, 3);

    private StackAwareWorkstationGameTests() {
    }

    @GameTest(template = TEMPLATE)
    public static void approvedProductsStackTo64AndUnapprovedProductRemainsOne(GameTestHelper helper) {
        helper.assertTrue(ModItems.BEEF_TRIM.get().getDefaultInstance().getMaxStackSize() == 64,
                "Beef Trim stacks to 64");
        helper.assertTrue(ModItems.GROUND_BEEF.get().getDefaultInstance().getMaxStackSize() == 64,
                "Ground Beef stacks to 64");
        helper.assertTrue(ModItems.BEEF_PATTIES.get().getDefaultInstance().getMaxStackSize() == 64,
                "Beef Patties stack to 64");
        helper.assertTrue(ModItems.T_BONE_STEAK.get().getDefaultInstance().getMaxStackSize() == 1,
                "T-Bone Steak remains outside selective stack activation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 760)
    public static void grinderProcessesStackAsBoundedCyclesUnderOneContinuousRun(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        GrinderBlockEntity grinder = require(helper, GRINDER_POS, GrinderBlockEntity.class);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 10));
        requestPlayerOperation(helper, GRINDER_POS);
        var runIdentity = grinder.runStatus().runIdentity().orElseThrow();

        helper.runAtTickTime(700, () -> {
            helper.assertTrue(grinder.inventory().input().isEmpty()
                            && grinder.inventory().output().getCount() == 10,
                    "One continuous Grinder Run completes ten separately bounded cycles");
            helper.assertTrue(grinder.runStatus().runIdentity().orElseThrow().equals(runIdentity),
                    "All ten bounded cycles retain one Machine Run identity");
            helper.assertTrue(grinder.runStatus().completedChildren() == 10,
                    "The Machine Run records ten terminal child operations");
            helper.assertTrue(grinder.runStatus().operatingState() == MachineOperatingState.RUNNING_EMPTY,
                    "Input exhaustion leaves the Grinder powered and RUNNING_EMPTY");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void fullGrinderOutputBlocksAtomically(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        GrinderBlockEntity grinder = require(helper, GRINDER_POS, GrinderBlockEntity.class);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 10));
        grinder.inventory().setOutputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 64));

        requestPlayerOperation(helper, GRINDER_POS);

        helper.assertTrue(grinder.workstationState() == WorkstationState.BLOCKED,
                "Full Grinder output blocks the operation before scheduling");
        helper.assertTrue(grinder.lastFailure().orElseThrow().code() == WorkstationFailureCode.OUTPUT_OCCUPIED,
                "Grinder reports typed output blocking");
        helper.assertTrue(grinder.inventory().input().getCount() == 10
                        && grinder.inventory().output().getCount() == 64,
                "Blocked Grinder operation mutates neither input nor output");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 280)
    public static void pattyFormerConsumesAndMergesOneOnlyAfterExplicitUse(GameTestHelper helper) {
        helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        PattyFormerBlockEntity pattyFormer = require(helper, PATTY_FORMER_POS, PattyFormerBlockEntity.class);
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 10));

        helper.runAtTickTime(30, () -> {
            helper.assertTrue(pattyFormer.workstationState() == WorkstationState.READY,
                    "Multiple Ground Beef wait without automatic Patty Former processing");
            requestPlayerOperation(helper, PATTY_FORMER_POS);
        });
        helper.runAtTickTime(130, () -> {
            helper.assertTrue(pattyFormer.inventory().input().getCount() == 9
                            && pattyFormer.inventory().output().getCount() == 1,
                    "One explicit Patty Former operation consumes and produces one");
            var player = new MenuTrackingTestPlayer(helper.getLevel(), helper.absolutePos(PATTY_FORMER_POS));
            helper.useBlock(PATTY_FORMER_POS, player);
            helper.assertTrue(player.containerMenu instanceof PattyFormerMenu,
                    "Normal use reopens the Patty Former menu with remaining valid input");
            player.closeContainer();
            helper.assertTrue(pattyFormer.inventory().input().getCount() == 9
                            && pattyFormer.inventory().output().getCount() == 1,
                    "Reopening the Patty Former menu does not authorize another operation");
            requestPlayerOperation(helper, PATTY_FORMER_POS);
        });
        helper.runAtTickTime(240, () -> {
            helper.assertTrue(pattyFormer.inventory().input().getCount() == 8
                            && pattyFormer.inventory().output().getCount() == 2,
                    "Second explicit operation merges one patty and does not batch-process input");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240)
    public static void cuttingTableTrimAccumulatesWithoutWeakeningPrimaryOutputAtomicity(GameTestHelper helper) {
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        CuttingTableBlockEntity cuttingTable = require(helper, CUTTING_TABLE_POS, CuttingTableBlockEntity.class);
        cuttingTable.inventory().setInputInternal(ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance());

        helper.runAtTickTime(100, () -> {
            helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot()).getCount() == 1
                            && cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).getCount() == 1,
                    "First Cutting Table operation publishes both outputs atomically");
            cuttingTable.inventory().setOutputInternal(0, ItemStack.EMPTY);
            cuttingTable.inventory().setInputInternal(ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance());
        });
        helper.runAtTickTime(210, () -> {
            helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot()).getCount() == 1,
                    "Second operation republishes the primary output");
            helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).getCount() == 2,
                    "Compatible Beef Trim accumulates in the stack-aware output");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void workstationProjectionRoundTripPreservesActivatedCounts(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState());
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        GrinderBlockEntity grinder = require(helper, GRINDER_POS, GrinderBlockEntity.class);
        PattyFormerBlockEntity pattyFormer = require(helper, PATTY_FORMER_POS, PattyFormerBlockEntity.class);
        CuttingTableBlockEntity cuttingTable = require(helper, CUTTING_TABLE_POS, CuttingTableBlockEntity.class);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 32));
        grinder.inventory().setOutputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 16));
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 21));
        pattyFormer.inventory().setOutputInternal(count(ModItems.BEEF_PATTIES.get().getDefaultInstance(), 7));
        cuttingTable.inventory().setOutputInternal(1, count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 63));

        GrinderBlockEntity restoredGrinder = reload(helper, GRINDER_POS, GrinderBlockEntity.class);
        PattyFormerBlockEntity restoredPattyFormer = reload(helper, PATTY_FORMER_POS, PattyFormerBlockEntity.class);
        CuttingTableBlockEntity restoredCuttingTable = reload(helper, CUTTING_TABLE_POS, CuttingTableBlockEntity.class);

        helper.assertTrue(restoredGrinder.inventory().input().getCount() == 32
                        && restoredGrinder.inventory().output().getCount() == 16,
                "Grinder reload preserves exact stack counts");
        helper.assertTrue(restoredPattyFormer.inventory().input().getCount() == 21
                        && restoredPattyFormer.inventory().output().getCount() == 7,
                "Patty Former reload preserves exact stack counts");
        helper.assertTrue(restoredCuttingTable.inventory()
                        .getStackInSlot(restoredCuttingTable.trimOutputSlot()).getCount() == 63,
                "Cutting Table reload preserves exact trim output count");
        helper.succeed();
    }

    private static ItemStack count(ItemStack stack, int count) {
        stack.setCount(count);
        return stack;
    }

    private static void requestPlayerOperation(GameTestHelper helper, BlockPos position) {
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setShiftKeyDown(true);
        helper.useBlock(position, player);
    }

    private static <T extends BlockEntity> T require(GameTestHelper helper, BlockPos position, Class<T> type) {
        BlockEntity blockEntity = helper.getBlockEntity(position);
        helper.assertTrue(type.isInstance(blockEntity), "Expected " + type.getSimpleName());
        return type.cast(blockEntity);
    }

    private static <T extends BlockEntity> T reload(GameTestHelper helper, BlockPos relative, Class<T> type) {
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(relative);
        T current = require(helper, relative, type);
        CompoundTag saved = current.saveWithFullMetadata(level.registryAccess());
        BlockState state = level.getBlockState(absolute);
        BlockEntity loaded = BlockEntity.loadStatic(absolute, state, saved, level.registryAccess());
        helper.assertTrue(type.isInstance(loaded), "Saved workstation reloads with its original type");
        T restored = type.cast(loaded);
        restored.setLevel(level);
        level.removeBlockEntity(absolute);
        level.setBlockEntity(restored);
        return restored;
    }
}
