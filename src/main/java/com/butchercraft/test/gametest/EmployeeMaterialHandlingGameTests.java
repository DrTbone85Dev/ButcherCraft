package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.machine.cuttingtable.CuttingTableBlock;
import com.butchercraft.machine.cuttingtable.CuttingTableBlockEntity;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.grinder.execution.GrinderWorkstationReference;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.EmployeeMaterialHandlingService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.InventoryService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferRecord;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignment;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentState;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingFailureCode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeeMaterialHandlingGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final String BATCH = "zzzzzzzz_employee_material_handling";
    private static final BlockPos CUTTING_TABLE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos CUTTING_TABLE_OPERATING_POS = CUTTING_TABLE_POS.relative(Direction.EAST);
    private static final BlockPos GRINDER_POS = new BlockPos(1, 1, 4);
    private static final BlockPos GRINDER_OPERATING_POS = GRINDER_POS.relative(Direction.NORTH);
    private static final BlockPos EMPLOYEE_POS = new BlockPos(4, 1, 0);
    private static final BlockPos SECOND_EMPLOYEE_POS = new BlockPos(4, 1, 4);

    private EmployeeMaterialHandlingGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = BATCH + "_00_output_preload")
    public static void developmentCommandPreloadsOnlyTheCuttingTableOutput(GameTestHelper helper) {
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState());
        CuttingTableBlockEntity cuttingTable = blockEntity(
                helper,
                CUTTING_TABLE_POS,
                CuttingTableBlockEntity.class
        );
        BlockPos absolute = helper.absolutePos(CUTTING_TABLE_POS);
        String command = "butchercraft workstation preload-cutting-table-output "
                + absolute.getX() + " " + absolute.getY() + " " + absolute.getZ();

        helper.assertTrue(execute(helper, commandSource(helper), command) == 1,
                "Development preload command succeeds for a Cutting Table");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "Development preload does not populate the reserved fabrication input");
        helper.assertTrue(trimStack(cuttingTable).is(ModItems.BEEF_TRIM.get())
                        && trimStack(cuttingTable).getCount() == 1,
                "Development preload places exactly one Beef Trim in the output");
        helper.assertTrue(execute(helper, commandSource(helper), command) == 1,
                "Identical development preload is observationally safe");
        helper.assertTrue(trimStack(cuttingTable).getCount() == 1,
                "Duplicate preload cannot duplicate the output");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "_01_explicit_transfer")
    public static void explicitCommandTransfersOneExactBeefTrimWithoutRuntimeSideEffects(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Transfer Command");
        Counts before = counts(helper);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        int playerBeefTrim = player.getInventory().countItem(ModItems.BEEF_TRIM.get());

        helper.assertTrue(execute(helper, player.createCommandSourceStack().withPermission(4),
                        transferCommand(helper, "#1")) == 1,
                "Explicit employee transfer command is accepted");
        assertSourceReservation(helper, fixture.record());
        arriveAtSource(helper, fixture);
        assertCustodyAndDestinationReservation(helper, fixture);
        arriveAtDestination(helper, fixture);

        helper.assertTrue(trimStack(fixture.cuttingTable()).isEmpty(),
                "Cutting Table output withdrawal occurs exactly once");
        helper.assertTrue(fixture.cuttingTable().inventory().input().isEmpty(),
                "Employee transfer leaves the reserved fabrication input unchanged");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        fixture.exactStack(), fixture.grinder().inventory().input()),
                "Grinder receives the exact one-item stack");
        helper.assertTrue(fixture.employee().getMainHandItem().isEmpty(),
                "Carry display clears after proven deposit");
        helper.assertTrue(assignment(helper, fixture.record()).state()
                        == EmployeeMaterialHandlingAssignmentState.COMPLETED,
                "Workforce assignment observes completion");
        WorkstationReservationRecord destination = reservation(helper, fixture.record());
        helper.assertTrue(destination.workstationType().equals("grinder")
                        && destination.state() == WorkstationReservationState.EMPLOYEE_ARRIVED,
                "Destination reservation remains active after deposit");
        helper.assertTrue(fixture.grinder().workstationState() != WorkstationState.PROCESSING,
                "Transport does not start Grinder processing");
        helper.assertTrue(counts(helper).equals(before),
                "Transport creates no Production, Scheduler, Execution, or economic Inventory mutation");
        helper.assertTrue(player.getInventory().countItem(ModItems.BEEF_TRIM.get()) == playerBeefTrim,
                "Player inventory remains unchanged");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "_02_physical_source")
    public static void withdrawalWaitsForPhysicalSourceArrival(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Physical Source Arrival");

        request(helper, fixture.record(), "#1");
        for (int tick = 0; tick < 5; tick++) {
            EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());
        }

        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        fixture.exactStack(), trimStack(fixture.cuttingTable())),
                "No distant source withdrawal occurs");
        helper.assertTrue(fixture.employee().getMainHandItem().isEmpty(),
                "Carry display remains empty before custody");
        helper.assertTrue(assignment(helper, fixture.record()).state()
                        == EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE,
                "Assignment remains walking to source");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH + "_03_physical_route")
    public static void employeePhysicallyWalksSourceToDestinationWithVisibleCustody(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Physical Transfer Route");
        boolean[] sawSourceTravel = {false};
        boolean[] sawCarry = {false};
        boolean[] sawDestinationTravel = {false};

        request(helper, fixture.record(), "#1");

        helper.succeedWhen(() -> {
            EmployeeService.INSTANCE.synchronizeEntity(fixture.employee());
            EmployeeMaterialHandlingAssignment current = assignment(helper, fixture.record());
            if (current.state() == EmployeeMaterialHandlingAssignmentState.WALKING_TO_SOURCE) {
                sawSourceTravel[0] = true;
            }
            if (!fixture.employee().getMainHandItem().isEmpty()) {
                sawCarry[0] = true;
            }
            if (current.state() == EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION) {
                sawDestinationTravel[0] = true;
            }
            helper.assertTrue(current.state() == EmployeeMaterialHandlingAssignmentState.COMPLETED,
                    physicalTransferDiagnostics(helper, fixture, current));
            helper.assertTrue(sawSourceTravel[0], "Source travel was observed");
            helper.assertTrue(sawCarry[0], "Visible Beef Trim custody was observed during travel");
            helper.assertTrue(sawDestinationTravel[0], "Destination travel was observed");
            helper.assertTrue(fixture.grinder().inventory().input().is(ModItems.BEEF_TRIM.get()),
                    "Physical route deposits Beef Trim at the Grinder");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "_04_duplicate")
    public static void duplicateAssignmentObservesOneTransferAndOneWithdrawal(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Duplicate Assignment");

        helper.assertTrue(request(helper, fixture.record(), "#1") == 1, "First assignment is accepted");
        helper.assertTrue(request(helper, fixture.record(), fixture.record().employeeId().value()) == 1,
                "Identical assignment is observed through canonical employee identity");

        helper.assertTrue(EmployeeMaterialHandlingService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .assignments().size() == 1,
                "Duplicate request retains one Workforce assignment");
        helper.assertTrue(trimStack(fixture.cuttingTable()).getCount() == 1,
                "Duplicate request does not withdraw before arrival");
        arriveAtSource(helper, fixture);
        helper.assertTrue(trimStack(fixture.cuttingTable()).isEmpty(),
                "Observed duplicate still performs exactly one withdrawal");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "_05_source_conflict")
    public static void sourceReservationConflictLeavesSourceUntouched(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Source Reservation Conflict");
        EmployeeRecord competitor = createEmployee(helper, "Source Holder", SECOND_EMPLOYEE_POS);
        helper.assertTrue(WorkstationReservationService.INSTANCE.assign(
                        helper.getLevel(), competitor.employeeId(), helper.absolutePos(CUTTING_TABLE_POS)).succeeded(),
                "Competing employee reserves the source");

        helper.assertTrue(request(helper, fixture.record(), "#1") == 0,
                "Source reservation conflict rejects transfer assignment");

        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        fixture.exactStack(), trimStack(fixture.cuttingTable())),
                "Rejected source reservation leaves source inventory unchanged");
        helper.assertTrue(assignment(helper, fixture.record()).state()
                        == EmployeeMaterialHandlingAssignmentState.FAILED,
                "Reservation conflict publishes a typed terminal Workforce failure");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "_06_destination_conflict")
    public static void destinationReservationConflictRetainsCustodyAndDisplay(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Destination Reservation Conflict");
        EmployeeRecord competitor = createEmployee(helper, "Destination Holder", SECOND_EMPLOYEE_POS);
        helper.assertTrue(WorkstationReservationService.INSTANCE.assign(
                        helper.getLevel(), competitor.employeeId(), helper.absolutePos(GRINDER_POS)).succeeded(),
                "Competing employee reserves the destination");

        request(helper, fixture.record(), "#1");
        arriveAtSource(helper, fixture);

        EmployeeMaterialHandlingAssignment current = assignment(helper, fixture.record());
        helper.assertTrue(current.state() == EmployeeMaterialHandlingAssignmentState.WAITING_FOR_DESTINATION_RESERVATION,
                "Employee waits visibly for the bound destination reservation");
        assertProvenCustody(helper, current);
        helper.assertTrue(fixture.employee().getMainHandItem().is(ModItems.BEEF_TRIM.get()),
                "Beef Trim remains visible while destination is occupied");
        helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .findByEmployee(fixture.record().employeeId().value()).isEmpty(),
                "Employee holds no source reservation while waiting and never overlaps reservations");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "_07_cancel_before_custody")
    public static void cancellationBeforeCustodyLeavesInventoryAndReleasesReservation(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Cancel Before Custody");
        request(helper, fixture.record(), "#1");

        helper.assertTrue(execute(helper, commandSource(helper),
                        "butchercraft employee transfer-cancel #1") == 1,
                "Pre-custody cancellation succeeds");

        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        fixture.exactStack(), trimStack(fixture.cuttingTable())),
                "Pre-custody cancellation leaves source inventory unchanged");
        helper.assertTrue(assignment(helper, fixture.record()).state()
                        == EmployeeMaterialHandlingAssignmentState.CANCELLED,
                "Workforce assignment publishes cancellation");
        helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .findByEmployee(fixture.record().employeeId().value()).isEmpty(),
                "Source reservation releases after cancellation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140, batch = BATCH + "_08_cancel_with_custody")
    public static void cancellationWhileCarryingReturnsExactStackOnce(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Cancel While Carrying");
        request(helper, fixture.record(), "#1");
        arriveAtSource(helper, fixture);

        helper.assertTrue(execute(helper, commandSource(helper),
                        "butchercraft employee transfer-cancel #1") == 1,
                "Cancellation while carrying is requested");
        helper.assertTrue(fixture.employee().getMainHandItem().is(ModItems.BEEF_TRIM.get()),
                "Display remains while Material Handling retains custody");
        moveAndSynchronize(helper, fixture.employee(), CUTTING_TABLE_OPERATING_POS);
        EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        fixture.exactStack(), trimStack(fixture.cuttingTable())),
                "Cancellation returns the exact stack through the source owner");
        helper.assertTrue(trimStack(fixture.cuttingTable()).getCount() == 1,
                "Source return occurs exactly once");
        helper.assertTrue(fixture.employee().getMainHandItem().isEmpty(),
                "Display clears only after proven source return");
        helper.assertTrue(execute(helper, commandSource(helper),
                        "butchercraft employee transfer-cancel #1") == 1,
                "Duplicate cancellation observes the terminal result");
        helper.assertTrue(trimStack(fixture.cuttingTable()).getCount() == 1,
                "Duplicate cancellation cannot return twice");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "_09_destination_replaced")
    public static void destinationReplacementPreservesCustodyAndRequiresRecovery(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Destination Replacement");
        request(helper, fixture.record(), "#1");
        arriveAtSource(helper, fixture);

        helper.setBlock(GRINDER_POS, Blocks.AIR);
        EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

        EmployeeMaterialHandlingAssignment current = assignment(helper, fixture.record());
        helper.assertTrue(current.state() == EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED,
                "Destination replacement is fail-visible");
        helper.assertTrue(current.failure().orElseThrow().code()
                        == EmployeeMaterialHandlingFailureCode.DESTINATION_ENDPOINT_REPLACED,
                "Destination replacement has a typed Workforce failure");
        assertProvenCustody(helper, current);
        helper.assertTrue(fixture.employee().getMainHandItem().is(ModItems.BEEF_TRIM.get()),
                "Display remains while custody is proven after destination replacement");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "_10_employee_removed")
    public static void employeeRemovalPreservesAuthoritativeCustody(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Employee Removal");
        request(helper, fixture.record(), "#1");
        arriveAtSource(helper, fixture);

        fixture.employee().discard();

        EmployeeMaterialHandlingAssignment current = assignment(helper, fixture.record());
        helper.assertTrue(current.state() == EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED,
                "Employee removal pauses the Workforce assignment for recovery");
        assertProvenCustody(helper, current);
        helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .findByEmployee(fixture.record().employeeId().value()).isEmpty(),
                "Employee removal invalidates the live reservation safely");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "_11_reload")
    public static void assignmentReloadReconstructsCarryFromMaterialHandling(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Reload While Carrying");
        request(helper, fixture.record(), "#1");
        arriveAtSource(helper, fixture);
        String transferIdentity = assignment(helper, fixture.record()).transferId().value();
        fixture.employee().resetGameTestCarryObservation();
        helper.assertTrue(fixture.employee().getMainHandItem().isEmpty(),
                "Test removes only the transient carry projection");

        EmployeeMaterialHandlingService.INSTANCE.reloadGameTestAssignments(helper.getLevel().getServer());
        EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

        EmployeeMaterialHandlingAssignment restored = assignment(helper, fixture.record());
        helper.assertTrue(restored.transferId().value().equals(transferIdentity),
                "Reload retains the persisted transfer binding");
        helper.assertTrue(restored.state() == EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION,
                "Reload restores destination intent from reconciled custody");
        helper.assertTrue(fixture.employee().getMainHandItem().is(ModItems.BEEF_TRIM.get())
                        && fixture.employee().getMainHandItem().getCount() == 1,
                "Carry display reconstructs from Material Handling custody");
        long revision = fixture.employee().carryObservationRevision();
        helper.assertTrue(!fixture.employee().clearCarryObservation(revision - 1L),
                "A stale observation cannot clear proven custody display");
        helper.assertTrue(!fixture.employee().applyCarryObservation(
                        transferIdentity,
                        ModItems.GROUND_BEEF.get().getDefaultInstance(),
                        "in_transit",
                        revision
                ),
                "A conflicting observation cannot reuse the current revision");
        helper.assertTrue(!fixture.employee().clearCarryObservation(revision),
                "A conflicting clear cannot reuse the current revision");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        fixture.exactStack(), fixture.employee().getMainHandItem()),
                "Stale and conflicting observations leave the exact carry display unchanged");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "_12_commands")
    public static void transferCommandsAcceptFriendlyReferencesAndSynchronize(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Casey 1");
        List<String> suggestions = suggestions(helper, "butchercraft employee transfer ");

        helper.assertTrue(suggestions.contains("#1"), "Transfer suggestions expose #1");
        helper.assertTrue(suggestions.contains("\"Casey 1\""),
                "Transfer suggestions quote display names containing spaces");
        helper.assertTrue(suggestions.contains(fixture.record().employeeId().value()),
                "Transfer suggestions expose canonical employee identity");
        helper.assertTrue(request(helper, fixture.record(), "\"Casey 1\"") == 1,
                "Quoted employee display name executes unchanged");
        helper.assertTrue(execute(helper, commandSource(helper),
                        "butchercraft employee transfer-status " + fixture.record().employeeId().value()) == 1,
                "Canonical employee identity executes in status command");
        assertSynchronizedArgumentTypes(helper,
                helper.getLevel().getServer().getCommands().getDispatcher().getRoot());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 260, batch = BATCH + "_13_operation_handoff")
    public static void completedTransferPreservesExistingEmployeeGrinderOperation(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Transfer Then Operate");
        Counts before = counts(helper);
        Set<ExecutionOperationId> grinderOperationsBefore = grinderOperationIds(helper);
        request(helper, fixture.record(), "#1");
        arriveAtSource(helper, fixture);
        arriveAtDestination(helper, fixture);
        helper.assertTrue(counts(helper).equals(before), "Transport itself creates no operation");

        helper.assertTrue(execute(helper, commandSource(helper),
                        "butchercraft employee operate #1") == 1,
                "Existing explicit employee Grinder operation remains available after transfer");

        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.grinder().inventory().output().is(ModItems.GROUND_BEEF.get())
                            && fixture.grinder().inventory().output().getCount() == 1,
                    "Existing Execution-backed Grinder operation produces exactly one Ground Beef");
            List<ExecutionOperationSnapshot> newOperations = grinderOperations(helper).stream()
                    .filter(operation -> !grinderOperationsBefore.contains(operation.operationId()))
                    .toList();
            helper.assertTrue(newOperations.size() == 1,
                    "Explicit operation creates exactly one Execution operation for the reserved Grinder: "
                            + newOperations.size());
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220, batch = BATCH + "_15_recipe_source")
    public static void recipeProducedTrimTransfersWhilePrimaryOutputRemains(GameTestHelper helper) {
        ItemStack[] expectedPrimary = {ItemStack.EMPTY};
        ItemStack[] expectedTrim = {ItemStack.EMPTY};
        Counts[] beforeTransfer = {null};
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState()
                .setValue(CuttingTableBlock.FACING, Direction.EAST));
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState()
                .setValue(GrinderBlock.FACING, Direction.NORTH));
        CuttingTableBlockEntity cuttingTable = blockEntity(
                helper,
                CUTTING_TABLE_POS,
                CuttingTableBlockEntity.class
        );
        GrinderBlockEntity grinder = blockEntity(helper, GRINDER_POS, GrinderBlockEntity.class);
        EmployeeRecord record = createEmployee(helper, "Recipe Source Transfer", EMPLOYEE_POS);
        EmployeeEntity employee = entity(helper, record);
        ItemStack remainder = cuttingTable.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                ModItems.BEEF_SHORT_LOIN.get().getDefaultInstance(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Beef Short Loin enters the Cutting Table input");

        helper.runAtTickTime(100, () -> {
            helper.assertTrue(cuttingTable.workstationState() == WorkstationState.COMPLETE,
                    "Normal Cutting Table processing completes before employee transfer");
            ItemStack primary = cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot()).copy();
            ItemStack trim = trimStack(cuttingTable).copy();
            helper.assertTrue(primary.is(ModItems.T_BONE_STEAK.get()) && primary.getCount() == 1,
                    "Recipe produces one T-Bone Steak in primary output");
            helper.assertTrue(trim.is(ModItems.BEEF_TRIM.get()) && trim.getCount() == 1,
                    "Recipe produces one Beef Trim in trim output");
            expectedPrimary[0] = primary.copy();
            expectedTrim[0] = trim.copy();
            beforeTransfer[0] = counts(helper);
            Fixture fixture = new Fixture(cuttingTable, grinder, record, employee, trim);

            helper.assertTrue(request(helper, record, "#1") == 1,
                    "Employee transfer accepts recipe-produced Beef Trim");
            arriveAtSource(helper, fixture);
            helper.assertTrue(ItemStack.isSameItemSameComponents(primary,
                            cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot())),
                    "Employee withdrawal leaves the T-Bone Steak untouched");
            helper.assertTrue(ItemStack.isSameItemSameComponents(trim, employee.getMainHandItem()),
                    "Employee visibly carries the exact recipe-produced Beef Trim");
            helper.assertTrue(grinder.workstationState() == WorkstationState.IDLE,
                    "Grinder remains idle before deposit");

            arriveAtDestination(helper, fixture);
            helper.assertTrue(assignment(helper, record).state()
                            == EmployeeMaterialHandlingAssignmentState.COMPLETED,
                    "Recipe-sourced employee transfer completes");
            helper.assertTrue(ItemStack.isSameItemSameComponents(trim, grinder.inventory().input()),
                    "Grinder receives the exact recipe-produced Beef Trim");
            helper.assertTrue(ItemStack.isSameItemSameComponents(primary,
                            cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot())),
                    "T-Bone Steak remains after destination deposit");
            helper.assertTrue(employee.getMainHandItem().isEmpty(),
                    "Carry display clears after proven Grinder deposit");
            helper.assertTrue(grinder.workstationState() != WorkstationState.PROCESSING,
                    "Employee transfer leaves the reserved Grinder quiescent");
            helper.assertTrue(counts(helper).equals(beforeTransfer[0]),
                    "Material transfer creates no Production, Scheduler, Execution, or Inventory mutation");
        });

        helper.runAtTickTime(125, () -> {
            helper.assertTrue(grinder.workstationState() != WorkstationState.PROCESSING,
                    "Reserved Grinder does not begin processing without the employee operation command");
            helper.assertTrue(ItemStack.isSameItemSameComponents(expectedTrim[0], grinder.inventory().input()),
                    "Recipe-produced Beef Trim remains in the idle Grinder input");
            helper.assertTrue(ItemStack.isSameItemSameComponents(expectedPrimary[0],
                            cuttingTable.inventory().getStackInSlot(cuttingTable.primaryOutputSlot())),
                    "Primary T-Bone Steak remains untouched while the Grinder waits");
            helper.assertTrue(counts(helper).equals(beforeTransfer[0]),
                    "Waiting for the operation command creates no runtime work");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "_14_wrong_endpoints")
    public static void wrongExplicitEndpointsAreRejectedWithoutAutomaticSearch(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Wrong Endpoints");
        BlockPos source = helper.absolutePos(CUTTING_TABLE_POS);
        BlockPos destination = helper.absolutePos(GRINDER_POS);
        String wrong = "butchercraft employee transfer #1 "
                + destination.getX() + " " + destination.getY() + " " + destination.getZ() + " "
                + source.getX() + " " + source.getY() + " " + source.getZ();

        helper.assertTrue(execute(helper, commandSource(helper), wrong) == 0,
                "Wrong explicit source and destination types are rejected");
        helper.assertTrue(EmployeeMaterialHandlingService.INSTANCE.activeFor(
                        helper.getLevel().getServer(), fixture.record().employeeId()).isEmpty(),
                "Rejected endpoints create no assignment and do not search for alternatives");
        helper.assertTrue(trimStack(fixture.cuttingTable()).getCount() == 1,
                "Rejected endpoints leave source untouched");
        helper.succeed();
    }

    private static Fixture setup(GameTestHelper helper, String displayName) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        helper.setBlock(CUTTING_TABLE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState()
                .setValue(CuttingTableBlock.FACING, Direction.EAST));
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState()
                .setValue(GrinderBlock.FACING, Direction.NORTH));
        CuttingTableBlockEntity cuttingTable = blockEntity(helper, CUTTING_TABLE_POS, CuttingTableBlockEntity.class);
        GrinderBlockEntity grinder = blockEntity(helper, GRINDER_POS, GrinderBlockEntity.class);
        ItemStack exact = ModItems.BEEF_TRIM.get().getDefaultInstance();
        exact.set(DataComponents.CUSTOM_NAME, Component.literal("IM-028B Exact Employee Custody"));
        helper.assertTrue(cuttingTable.preloadOutputForDevelopment(exact.copy())
                        == CuttingTableBlockEntity.DevelopmentOutputPreloadStatus.PRELOADED,
                "Development preload places exact Beef Trim in the Cutting Table output");
        helper.assertTrue(cuttingTable.inventory().input().isEmpty(),
                "The reserved fabrication input starts empty");
        EmployeeRecord record = createEmployee(helper, displayName, EMPLOYEE_POS);
        return new Fixture(cuttingTable, grinder, record, entity(helper, record), exact);
    }

    private static EmployeeRecord createEmployee(GameTestHelper helper, String name, BlockPos relativePosition) {
        EmployeeRecord created = EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(), Optional.of(name), Optional.of(helper.absolutePos(relativePosition)), true
        ).orThrow();
        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(), created.employeeId(), DepartmentSchema.PROCESSING.value()
        ).orThrow();
        EmployeeService.INSTANCE.setPresence(
                helper.getLevel().getServer(), created.employeeId(), EmployeePresenceState.PRESENT
        ).orThrow();
        return EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(created.employeeId()).orElseThrow();
    }

    private static int request(GameTestHelper helper, EmployeeRecord record, String employeeReference) {
        return execute(helper, commandSource(helper), transferCommand(helper, employeeReference));
    }

    private static String transferCommand(GameTestHelper helper, String employeeReference) {
        BlockPos source = helper.absolutePos(CUTTING_TABLE_POS);
        BlockPos destination = helper.absolutePos(GRINDER_POS);
        return "butchercraft employee transfer " + employeeReference + " "
                + source.getX() + " " + source.getY() + " " + source.getZ() + " "
                + destination.getX() + " " + destination.getY() + " " + destination.getZ();
    }

    private static void arriveAtSource(GameTestHelper helper, Fixture fixture) {
        moveAndSynchronize(helper, fixture.employee(), CUTTING_TABLE_OPERATING_POS);
        helper.assertTrue(reservation(helper, fixture.record()).state()
                        == WorkstationReservationState.EMPLOYEE_ARRIVED,
                "Employee physically arrives at Cutting Table");
        EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());
    }

    private static void arriveAtDestination(GameTestHelper helper, Fixture fixture) {
        EmployeeService.INSTANCE.synchronizeEntity(fixture.employee());
        moveAndSynchronize(helper, fixture.employee(), GRINDER_OPERATING_POS);
        helper.assertTrue(reservation(helper, fixture.record()).state()
                        == WorkstationReservationState.EMPLOYEE_ARRIVED,
                "Employee physically arrives at Grinder");
        EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());
    }

    private static void moveAndSynchronize(GameTestHelper helper, EmployeeEntity employee, BlockPos relativePosition) {
        BlockPos absolute = helper.absolutePos(relativePosition);
        employee.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(EmployeeService.INSTANCE.synchronizeEntity(employee),
                "Employee entity synchronizes at the physical destination");
    }

    private static void assertSourceReservation(GameTestHelper helper, EmployeeRecord record) {
        WorkstationReservationRecord reservation = reservation(helper, record);
        helper.assertTrue(reservation.workstationType().equals("cutting_table"),
                "Cutting Table reservation is acquired first");
        helper.assertTrue(reservation.state() == WorkstationReservationState.EMPLOYEE_EN_ROUTE,
                "Source reservation starts en route");
    }

    private static void assertCustodyAndDestinationReservation(GameTestHelper helper, Fixture fixture) {
        EmployeeMaterialHandlingAssignment assignment = assignment(helper, fixture.record());
        assertProvenCustody(helper, assignment);
        helper.assertTrue(fixture.employee().getMainHandItem().is(ModItems.BEEF_TRIM.get())
                        && fixture.employee().getMainHandItem().getCount() == 1,
                "Visible carry projection is exactly one Beef Trim after proven custody");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        fixture.exactStack(), fixture.employee().getMainHandItem()),
                "Carry projection preserves the exact ItemStack components");
        WorkstationReservationRecord reservation = reservation(helper, fixture.record());
        helper.assertTrue(reservation.workstationType().equals("grinder"),
                "Destination reservation is acquired only after source release");
        helper.assertTrue(WorkstationReservationService.INSTANCE.activeReservations(
                        helper.getLevel().getServer()).stream()
                        .filter(value -> value.employeeIdentity().equals(fixture.record().employeeId().value()))
                        .count() == 1L,
                "Employee never holds overlapping source and destination reservations");
    }

    private static void assertProvenCustody(
            GameTestHelper helper,
            EmployeeMaterialHandlingAssignment assignment
    ) {
        MaterialTransferRecord transfer = MaterialHandlingService.INSTANCE.findTransfer(
                helper.getLevel().getServer(), assignment.transferId()
        ).orElseThrow();
        helper.assertTrue(transfer.hasProvenMaterialHandlingCustody(),
                "Material Handling retains proven exact custody");
        helper.assertTrue(transfer.lifecycle() == MaterialTransferLifecycle.IN_TRANSIT
                        || transfer.lifecycle() == MaterialTransferLifecycle.RECOVERY_REQUIRED,
                "Custody lifecycle remains in transit or explicitly recoverable");
    }

    private static EmployeeMaterialHandlingAssignment assignment(GameTestHelper helper, EmployeeRecord record) {
        return EmployeeMaterialHandlingService.INSTANCE.latestFor(
                helper.getLevel().getServer(), record.employeeId()
        ).orElseThrow();
    }

    private static WorkstationReservationRecord reservation(GameTestHelper helper, EmployeeRecord record) {
        return WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                .findByEmployee(record.employeeId().value()).orElseThrow();
    }

    private static String physicalTransferDiagnostics(
            GameTestHelper helper,
            Fixture fixture,
            EmployeeMaterialHandlingAssignment assignment
    ) {
        WorkstationReservationRecord reservation = reservation(helper, fixture.record());
        MaterialTransferRecord transfer = MaterialHandlingService.INSTANCE.findTransfer(
                helper.getLevel().getServer(), assignment.transferId()
        ).orElseThrow();
        boolean withinTolerance = WorkstationReservationService.INSTANCE.isWithinOperatingTolerance(
                helper.getLevel(), reservation, fixture.employee().position()
        );
        return "Employee completes physical source-to-destination travel: assignment="
                + assignment.state()
                + ", reservation=" + reservation.state()
                + ", transfer=" + transfer.lifecycle()
                + ", withinTolerance=" + withinTolerance
                + ", navigation=" + fixture.employee().navigationDiagnostics();
    }

    private static EmployeeEntity entity(GameTestHelper helper, EmployeeRecord record) {
        Entity entity = helper.getLevel().getEntity(record.entityLink().orElseThrow().entityUuid());
        helper.assertTrue(entity instanceof EmployeeEntity, "Employee record binds a live Employee entity");
        return (EmployeeEntity) entity;
    }

    private static CommandSourceStack commandSource(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
    }

    private static int execute(GameTestHelper helper, CommandSourceStack source, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException exception) {
            helper.assertTrue(false, "Command should execute: " + command + " | " + exception.getMessage());
            return 0;
        }
    }

    private static List<String> suggestions(GameTestHelper helper, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = commandSource(helper);
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, source)).join().getList().stream()
                .map(Suggestion::getText)
                .toList();
    }

    private static void assertSynchronizedArgumentTypes(
            GameTestHelper helper,
            CommandNode<CommandSourceStack> node
    ) {
        if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argumentNode) {
            ArgumentType<?> type = argumentNode.getType();
            try {
                ArgumentTypeInfos.byClass(type);
            } catch (IllegalArgumentException exception) {
                helper.assertTrue(false, "Command argument type must synchronize: " + type.getClass().getName());
            }
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            assertSynchronizedArgumentTypes(helper, child);
        }
    }

    private static Counts counts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        return new Counts(
                ProductionService.INSTANCE.managerFor(server).runs().size(),
                SimulationSchedulerService.INSTANCE.managerFor(server).registry().size(),
                ExecutionService.INSTANCE.managerFor(server).operations().size(),
                InventoryService.INSTANCE.managerFor(server).registry().size()
        );
    }

    private static Set<ExecutionOperationId> grinderOperationIds(GameTestHelper helper) {
        Set<ExecutionOperationId> result = new HashSet<>();
        grinderOperations(helper).forEach(operation -> result.add(operation.operationId()));
        return Set.copyOf(result);
    }

    private static List<ExecutionOperationSnapshot> grinderOperations(GameTestHelper helper) {
        String identity = GrinderWorkstationReference.of(
                helper.getLevel(),
                helper.absolutePos(GRINDER_POS)
        ).identity();
        return ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer()).operations().stream()
                .filter(operation -> operation.authorizationEvidence().executableWorkReferenceId().equals(identity))
                .toList();
    }

    private static <T extends BlockEntity> T blockEntity(
            GameTestHelper helper,
            BlockPos relativePosition,
            Class<T> type
    ) {
        BlockEntity blockEntity = helper.getBlockEntity(relativePosition);
        helper.assertTrue(type.isInstance(blockEntity), "Expected " + type.getSimpleName());
        return type.cast(blockEntity);
    }

    private static ItemStack trimStack(CuttingTableBlockEntity cuttingTable) {
        return cuttingTable.inventory().getStackInSlot(cuttingTable.trimOutputSlot());
    }

    private record Fixture(
            CuttingTableBlockEntity cuttingTable,
            GrinderBlockEntity grinder,
            EmployeeRecord record,
            EmployeeEntity employee,
            ItemStack exactStack
    ) {
        private Fixture {
            exactStack = exactStack.copy();
        }
    }

    private record Counts(int productionRuns, int schedulerWork, int executionOperations, int inventoryEntries) {
    }
}
