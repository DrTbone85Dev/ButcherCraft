package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialCustodyLocation;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingTransferResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MaterialHandlingGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos CUTTING_TABLE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos GRINDER_POS = new BlockPos(3, 1, 2);

    private MaterialHandlingGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void cuttingTableTransfersExactBeefTrimToGrinder(GameTestHelper helper) {
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        CuttingTableBlockEntity cuttingTable = requireCuttingTable(helper);
        GrinderBlockEntity grinder = requireGrinder(helper);
        ItemStack exactStack = ModItems.BEEF_TRIM.get().getDefaultInstance();
        exactStack.set(DataComponents.CUSTOM_NAME, Component.literal("IM-028A Exact Custody"));
        helper.assertTrue(cuttingTable.preloadOutputForDevelopment(exactStack.copy())
                        == CuttingTableBlockEntity.DevelopmentOutputPreloadStatus.PRELOADED,
                "Development preload places exactly one Beef Trim in the Cutting Table output");
        cuttingTable.inventory().setOutputInternal(0, ModItems.T_BONE_STEAK.get().getDefaultInstance());
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "The reserved fabrication input remains empty before transfer");

        MaterialHandlingTransferResult result = MaterialHandlingService.INSTANCE.requestExplicitTransfer(
                helper.getLevel(),
                helper.absolutePos(CUTTING_TABLE_POS),
                helper.absolutePos(GRINDER_POS)
        );

        helper.assertTrue(result.succeeded(), "Explicit Material Handling transfer completes: " + result.detail());
        helper.assertTrue(result.transfer().orElseThrow().lifecycle() == MaterialTransferLifecycle.COMPLETED,
                "Material Transfer publishes COMPLETED");
        helper.assertTrue(result.transfer().orElseThrow().inTransitCustody().isEmpty(),
                "Material Handling clears custody only after destination commit");
        helper.assertTrue(result.transfer().orElseThrow().custodyLocation().orElseThrow()
                        == MaterialCustodyLocation.DESTINATION_WORKSTATION,
                "Completed transfer identifies the destination as authoritative custody");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).isEmpty(),
                "Cutting Table Beef Trim output is empty after committed withdrawal");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot())
                        .is(ModItems.T_BONE_STEAK.get()),
                "Material Handling leaves the primary output untouched");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "Material Handling does not mutate the reserved fabrication input");
        ItemStack deposited = grinder.inventory().input();
        helper.assertTrue(deposited.getItem() == ModItems.BEEF_TRIM.get(),
                "Grinder receives Beef Trim through its Workstation-owned endpoint");
        helper.assertTrue(deposited.getCount() == 1, "Grinder receives exactly one item");
        helper.assertTrue(ItemStack.isSameItemSameComponents(exactStack, deposited),
                "All ItemStack data components survive persisted custody exactly");
        helper.assertTrue(result.transfer().orElseThrow().sourceResult().isEmpty(),
                "Completed transfer collapses the full source owner payload");
        helper.assertTrue(result.transfer().orElseThrow().destinationResult().isEmpty(),
                "Completed transfer collapses the full destination owner payload");
        helper.assertTrue(result.transfer().orElseThrow().terminalEvidence().orElseThrow().sourceResult().isPresent(),
                "Completed transfer retains the source owner evidence identity and digest");
        helper.assertTrue(result.transfer().orElseThrow().terminalEvidence().orElseThrow()
                        .destinationResult().isPresent(),
                "Completed transfer retains the destination owner evidence identity and digest");
        MaterialHandlingTransferResult duplicate = MaterialHandlingService.INSTANCE.resume(
                helper.getLevel(),
                result.transfer().orElseThrow().transferId()
        );
        helper.assertTrue(duplicate.succeeded(), "Duplicate observation returns the completed authoritative result");
        helper.assertTrue(grinder.inventory().input().getCount() == 1,
                "Duplicate observation does not repeat destination insertion");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void occupiedGrinderSupportsExplicitCustodyCancellation(GameTestHelper helper) {
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState());
        CuttingTableBlockEntity cuttingTable = requireCuttingTable(helper);
        GrinderBlockEntity grinder = requireGrinder(helper);
        ItemStack exactStack = ModItems.BEEF_TRIM.get().getDefaultInstance();
        exactStack.set(DataComponents.CUSTOM_NAME, Component.literal("IM-028A Cancellation Custody"));
        helper.assertTrue(cuttingTable.preloadOutputForDevelopment(exactStack.copy())
                        == CuttingTableBlockEntity.DevelopmentOutputPreloadStatus.PRELOADED,
                "Development preload places Beef Trim in the Cutting Table output");
        cuttingTable.inventory().setOutputInternal(0, ModItems.T_BONE_STEAK.get().getDefaultInstance());
        grinder.inventory().insertItem(0, ModItems.BEEF_TRIM.get().getDefaultInstance(), false);

        MaterialHandlingTransferResult result = MaterialHandlingService.INSTANCE.requestExplicitTransfer(
                helper.getLevel(),
                helper.absolutePos(CUTTING_TABLE_POS),
                helper.absolutePos(GRINDER_POS)
        );

        helper.assertFalse(result.succeeded(), "Occupied destination does not complete the transfer");
        helper.assertTrue(result.transfer().orElseThrow().lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED,
                "Proven but unresolved custody becomes RECOVERY_REQUIRED");
        helper.assertTrue(result.transfer().orElseThrow().inTransitCustody().isPresent(),
                "Exact ItemStack remains in Material Handling custody");
        helper.assertTrue(result.transfer().orElseThrow().custodyLocation().orElseThrow()
                        == MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME,
                "Recovery Required identifies Material Handling as proven custody authority");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).isEmpty(),
                "Committed source withdrawal is not silently reversed");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "Committed source withdrawal leaves the reserved fabrication input unchanged");
        helper.assertTrue(result.transfer().orElseThrow().sourceObservation().orElseThrow().slotIndex()
                        == cuttingTable.trimOutputSlot(),
                "Endpoint freshness binds the dedicated Beef Trim output slot");
        helper.assertTrue(grinder.inventory().input().getCount() == 1,
                "Blocked Grinder input is not overwritten");

        MaterialHandlingTransferResult cancelled = MaterialHandlingService.INSTANCE.cancel(
                helper.getLevel(),
                result.transfer().orElseThrow().transferId(),
                "GameTest requested cancellation"
        );
        helper.assertTrue(cancelled.succeeded(), "Explicit cancellation returns proven custody to the source");
        helper.assertTrue(cancelled.transfer().orElseThrow().lifecycle() == MaterialTransferLifecycle.CANCELLED,
                "Returned custody publishes CANCELLED");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        exactStack,
                        cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot())),
                "Source return preserves the exact ItemStack and data components in the Beef Trim output");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot())
                        .is(ModItems.T_BONE_STEAK.get()),
                "Cancellation leaves the primary output untouched");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "Source return does not write the reserved fabrication input");
        helper.assertTrue(cancelled.transfer().orElseThrow().inTransitCustody().isEmpty(),
                "Cancelled transfer collapses the in-transit custody payload");
        helper.assertTrue(cancelled.transfer().orElseThrow().returnResult().isEmpty(),
                "Cancelled transfer collapses the full return owner-result payload");
        helper.assertTrue(cancelled.transfer().orElseThrow().terminalEvidence().orElseThrow().returnResult().isPresent(),
                "Cancelled transfer retains the immutable source-return evidence identity and digest");

        MaterialHandlingTransferResult duplicate = MaterialHandlingService.INSTANCE.cancel(
                helper.getLevel(),
                result.transfer().orElseThrow().transferId(),
                "duplicate cancellation"
        );
        helper.assertTrue(duplicate.succeeded(), "Duplicate cancellation observes the authoritative result");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).getCount() == 1,
                "Duplicate cancellation does not repeat the source return");
        helper.assertTrue(grinder.inventory().input().getCount() == 1,
                "Cancellation never mutates the blocked destination");
        helper.succeed();
    }

    private static CuttingTableBlockEntity requireCuttingTable(GameTestHelper helper) {
        return (CuttingTableBlockEntity) helper.getBlockEntity(CUTTING_TABLE_POS);
    }

    private static GrinderBlockEntity requireGrinder(GameTestHelper helper) {
        return (GrinderBlockEntity) helper.getBlockEntity(GRINDER_POS);
    }
}
