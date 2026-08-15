package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialCustodyLocation;
import com.butchercraft.world.materialhandling.MaterialTransferRecordV2;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingTransferResult;
import com.butchercraft.workstation.endpoint.WorkstationEndpointEffectKind;
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
        exactStack.setCount(64);
        helper.assertTrue(cuttingTable.preloadOutputForDevelopment(exactStack.copy())
                        == CuttingTableBlockEntity.DevelopmentOutputPreloadStatus.PRELOADED,
                "Development preload places 64 exact Beef Trim in the Cutting Table output");
        cuttingTable.inventory().setOutputInternal(0, ModItems.T_BONE_STEAK.get().getDefaultInstance());
        ItemStack existingDestination = exactStack.copy();
        existingDestination.setCount(20);
        grinder.inventory().setInputInternal(existingDestination);
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "The reserved fabrication input remains empty before transfer");

        MaterialHandlingTransferResult result = MaterialHandlingService.INSTANCE.requestExplicitTransfer(
                helper.getLevel(),
                helper.absolutePos(CUTTING_TABLE_POS),
                helper.absolutePos(GRINDER_POS)
        );

        helper.assertTrue(result.succeeded(), "Explicit Material Handling transfer completes: " + result.detail());
        MaterialTransferRecordV2 transfer = schema2(result);
        helper.assertTrue(transfer.lifecycle() == MaterialTransferLifecycle.COMPLETED,
                "Material Transfer publishes COMPLETED");
        helper.assertTrue(transfer.exactInTransitCustody().isEmpty(),
                "Material Handling clears custody only after destination commit");
        helper.assertTrue(transfer.custodyLocation().orElseThrow()
                        == MaterialCustodyLocation.DESTINATION_WORKSTATION,
                "Completed transfer identifies the destination as authoritative custody");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).getCount() == 63,
                "Cutting Table retains the exact 63-item source remainder");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot())
                        .is(ModItems.T_BONE_STEAK.get()),
                "Material Handling leaves the primary output untouched");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "Material Handling does not mutate the reserved fabrication input");
        ItemStack deposited = grinder.inventory().input();
        helper.assertTrue(deposited.getItem() == ModItems.BEEF_TRIM.get(),
                "Grinder receives Beef Trim through its Workstation-owned endpoint");
        helper.assertTrue(deposited.getCount() == 21, "Grinder merges exactly one delivered item");
        helper.assertTrue(ItemStack.isSameItemSameComponents(exactStack, deposited),
                "All ItemStack data components survive persisted custody exactly");
        helper.assertTrue(transfer.exactTransferStack().count() == 1,
                "Material Handling owns exactly one item during the assignment");
        helper.assertTrue(transfer.endpointOwnerResults().stream()
                        .anyMatch(resultValue -> resultValue.preparation().observation().effectKind()
                                == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL),
                "Completed transfer retains the source owner evidence");
        helper.assertTrue(transfer.endpointOwnerResults().stream()
                        .anyMatch(resultValue -> resultValue.preparation().observation().effectKind()
                                == WorkstationEndpointEffectKind.DESTINATION_DEPOSIT),
                "Completed transfer retains the destination owner evidence");
        MaterialHandlingTransferResult duplicate = MaterialHandlingService.INSTANCE.resume(
                helper.getLevel(),
                transfer.transferReference()
        );
        helper.assertTrue(duplicate.succeeded(), "Duplicate observation returns the completed authoritative result");
        helper.assertTrue(grinder.inventory().input().getCount() == 21,
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
        exactStack.setCount(64);
        helper.assertTrue(cuttingTable.preloadOutputForDevelopment(exactStack.copy())
                        == CuttingTableBlockEntity.DevelopmentOutputPreloadStatus.PRELOADED,
                "Development preload places Beef Trim in the Cutting Table output");
        cuttingTable.inventory().setOutputInternal(0, ModItems.T_BONE_STEAK.get().getDefaultInstance());
        ItemStack fullDestination = ModItems.BEEF_TRIM.get().getDefaultInstance();
        fullDestination.setCount(64);
        grinder.inventory().setInputInternal(fullDestination);

        MaterialHandlingTransferResult result = MaterialHandlingService.INSTANCE.requestExplicitTransfer(
                helper.getLevel(),
                helper.absolutePos(CUTTING_TABLE_POS),
                helper.absolutePos(GRINDER_POS)
        );

        helper.assertFalse(result.succeeded(), "Occupied destination does not complete the transfer");
        MaterialTransferRecordV2 transfer = schema2(result);
        helper.assertTrue(transfer.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED,
                "Proven but unresolved custody becomes RECOVERY_REQUIRED");
        helper.assertTrue(transfer.exactInTransitCustody().isPresent(),
                "Exact ItemStack remains in Material Handling custody");
        helper.assertTrue(transfer.custodyLocation().orElseThrow()
                        == MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME,
                "Recovery Required identifies Material Handling as proven custody authority");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).getCount() == 63,
                "Committed partial withdrawal retains the exact source remainder");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "Committed source withdrawal leaves the reserved fabrication input unchanged");
        helper.assertTrue(transfer.endpointObservations().stream()
                        .filter(value -> value.effectKind() == WorkstationEndpointEffectKind.SOURCE_WITHDRAWAL)
                        .anyMatch(value -> value.slotIndex() == cuttingTable.trimOutputSlot()),
                "Endpoint freshness binds the dedicated Beef Trim output slot");
        helper.assertTrue(grinder.inventory().input().getCount() == 64,
                "Blocked Grinder input is not overwritten");

        MaterialHandlingTransferResult cancelled = MaterialHandlingService.INSTANCE.cancel(
                helper.getLevel(),
                transfer.transferReference(),
                "GameTest requested cancellation"
        );
        MaterialTransferRecordV2 cancelledTransfer = schema2(cancelled);
        helper.assertTrue(cancelled.succeeded(), "Explicit cancellation returns proven custody to the source");
        helper.assertTrue(cancelledTransfer.lifecycle() == MaterialTransferLifecycle.CANCELLED,
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
        helper.assertTrue(cancelledTransfer.exactInTransitCustody().isEmpty(),
                "Cancelled transfer collapses the in-transit custody payload");
        helper.assertTrue(cancelledTransfer.endpointOwnerResults().stream()
                        .anyMatch(value -> value.preparation().observation().effectKind()
                                == WorkstationEndpointEffectKind.SOURCE_RETURN),
                "Cancelled transfer retains the immutable source-return owner evidence");

        MaterialHandlingTransferResult duplicate = MaterialHandlingService.INSTANCE.cancel(
                helper.getLevel(),
                transfer.transferReference(),
                "duplicate cancellation"
        );
        helper.assertTrue(duplicate.succeeded(), "Duplicate cancellation observes the authoritative result");
        helper.assertTrue(cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot()).getCount() == 64,
                "Duplicate cancellation does not repeat the source return");
        helper.assertTrue(grinder.inventory().input().getCount() == 64,
                "Cancellation never mutates the blocked destination");
        helper.succeed();
    }

    private static MaterialTransferRecordV2 schema2(MaterialHandlingTransferResult result) {
        return (MaterialTransferRecordV2) result.transfer().orElseThrow();
    }

    private static CuttingTableBlockEntity requireCuttingTable(GameTestHelper helper) {
        return (CuttingTableBlockEntity) helper.getBlockEntity(CUTTING_TABLE_POS);
    }

    private static GrinderBlockEntity requireGrinder(GameTestHelper helper) {
        return (GrinderBlockEntity) helper.getBlockEntity(GRINDER_POS);
    }
}
