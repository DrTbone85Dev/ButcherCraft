package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlock;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.EmployeeMaterialHandlingService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.materialhandling.MaterialCustodyLocation;
import com.butchercraft.world.materialhandling.MaterialTransferLifecycle;
import com.butchercraft.world.materialhandling.MaterialTransferView;
import com.butchercraft.world.materialhandling.runtime.MaterialHandlingService;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignment;
import com.butchercraft.world.workforce.materialhandling.EmployeeMaterialHandlingAssignmentState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeeGroundBeefMaterialHandlingGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final String BATCH = "zzzzzzzzz_employee_ground_beef_transfer_";
    private static final BlockPos GRINDER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos GRINDER_OPERATING_POS = GRINDER_POS.relative(Direction.SOUTH);
    private static final BlockPos PATTY_FORMER_POS = new BlockPos(3, 1, 4);
    private static final BlockPos PATTY_FORMER_OPERATING_POS = PATTY_FORMER_POS.relative(Direction.NORTH);
    private static final BlockPos EMPLOYEE_POS = new BlockPos(4, 1, 0);

    private EmployeeGroundBeefMaterialHandlingGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 220, batch = BATCH + "01_success")
    public static void employeeCarriesGroundBeefFromGrinderToIdlePattyFormer(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Ground Beef Courier");

        helper.runAtTickTime(100, () -> {
            assertLegitimateGroundBeefOutput(helper, fixture.grinder());
            ItemStack sourceStack = fixture.grinder().inventory().output().copy();
            sourceStack.setCount(64);
            fixture.grinder().inventory().setOutputInternal(sourceStack);
            ItemStack destinationStack = sourceStack.copy();
            destinationStack.setCount(20);
            fixture.pattyFormer().inventory().setInputInternal(destinationStack);
            Counts beforeTransfer = counts(helper);
            ItemStack exactOutput = sourceStack.copy();
            exactOutput.setCount(1);

            WorkstationReservationService.INSTANCE.assign(
                    helper.getLevel(), fixture.record().employeeId(), helper.absolutePos(GRINDER_POS)
            ).orThrow();
            moveAndSynchronize(helper, fixture.employee(), GRINDER_OPERATING_POS);
            helper.assertTrue(reservation(helper, fixture.record()).state() == WorkstationReservationState.EMPLOYEE_ARRIVED,
                    "Completed employee operation leaves a reusable arrived Grinder source reservation");

            helper.assertTrue(executeTransferCommand(helper, fixture.record()) == 1,
                    "Existing generic employee transfer command accepts Grinder to Patty Former");
            EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

            EmployeeMaterialHandlingAssignment carrying = assignment(helper, fixture.record());
            MaterialTransferView transfer = transfer(helper, carrying);
            helper.assertTrue(carrying.state() == EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION,
                    "Workforce observes proven custody before destination travel");
            helper.assertTrue(transfer.lifecycle() == MaterialTransferLifecycle.IN_TRANSIT,
                    "Material Handling owns the in-transit lifecycle");
            helper.assertTrue(transfer.custodyLocation().orElseThrow() == MaterialCustodyLocation.MATERIAL_HANDLING_RUNTIME,
                    "Material Handling is the singular proven custody location");
            helper.assertTrue(fixture.grinder().inventory().output().getCount() == 63,
                    "Committed schema-2 withdrawal leaves the exact Grinder output remainder");
            helper.assertTrue(ItemStack.isSameItemSameComponents(exactOutput, fixture.employee().getMainHandItem()),
                    "Employee visibly carries the exact one-unit Ground Beef custody projection");
            helper.assertTrue(reservation(helper, fixture.record()).workstationType().equals("patty_former"),
                    "Source reservation releases before the Patty Former reservation is acquired");
            assertOneReservation(helper, fixture.record());

            moveAndSynchronize(helper, fixture.employee(), PATTY_FORMER_OPERATING_POS);
            EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

            EmployeeMaterialHandlingAssignment completed = assignment(helper, fixture.record());
            helper.assertTrue(completed.state() == EmployeeMaterialHandlingAssignmentState.COMPLETED,
                    "Workforce observes completed Material Handling transfer");
            helper.assertTrue(transfer(helper, completed).lifecycle() == MaterialTransferLifecycle.COMPLETED,
                    "Material Handling publishes completed transfer evidence");
            helper.assertTrue(ItemStack.isSameItemSameComponents(
                            exactOutput, fixture.pattyFormer().inventory().input()),
                    "Patty Former receives the exact Ground Beef stack through its owner endpoint");
            helper.assertTrue(fixture.pattyFormer().inventory().input().getCount() == 21,
                    "Patty Former merges exactly one delivered Ground Beef");
            helper.assertTrue(fixture.pattyFormer().workstationState() == WorkstationState.READY,
                    "Ground Beef deposit leaves Patty Former READY");
            helper.assertTrue(fixture.employee().getMainHandItem().isEmpty(),
                    "Carry projection clears immediately after proven deposit");
            helper.assertTrue(reservation(helper, fixture.record()).workstationType().equals("patty_former"),
                    "Employee remains reserved at the Patty Former after delivery");
            assertOneReservation(helper, fixture.record());
            assertCounts(helper, beforeTransfer);

            helper.runAtTickTime(180, () -> {
                helper.assertTrue(fixture.pattyFormer().workstationState() == WorkstationState.READY,
                        "Patty Former remains READY without automatic operation");
                helper.assertTrue(fixture.pattyFormer().inventory().output().isEmpty(),
                        "Transport alone produces no Beef Patties");
                assertCounts(helper, beforeTransfer);
                helper.succeed();
            });
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 150, batch = BATCH + "02_cancellation")
    public static void cancellationReturnsGroundBeefToGrinderOutputExactlyOnce(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Ground Beef Return");

        helper.runAtTickTime(100, () -> {
            assertLegitimateGroundBeefOutput(helper, fixture.grinder());
            ItemStack sourceStack = fixture.grinder().inventory().output().copy();
            sourceStack.setCount(64);
            fixture.grinder().inventory().setOutputInternal(sourceStack);
            ItemStack exactOutput = sourceStack.copy();
            exactOutput.setCount(1);
            requestAndWithdraw(helper, fixture, 63);
            helper.assertTrue(fixture.grinder().inventory().output().getCount() == 63,
                    "Ground Beef partial withdrawal retains the exact source remainder");

            EmployeeMaterialHandlingService.AssignmentResult requested = EmployeeMaterialHandlingService.INSTANCE.cancel(
                    helper.getLevel(), fixture.record().employeeId(), "GameTest cancellation during custody"
            );
            helper.assertTrue(requested.status()
                            == EmployeeMaterialHandlingService.AssignmentStatus.CANCELLATION_REQUESTED,
                    "Cancellation enters the existing custody-return lifecycle");
            EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

            EmployeeMaterialHandlingAssignment cancelled = assignment(helper, fixture.record());
            helper.assertTrue(cancelled.state() == EmployeeMaterialHandlingAssignmentState.CANCELLED,
                    "Workforce observes terminal cancellation");
            helper.assertTrue(transfer(helper, cancelled).lifecycle() == MaterialTransferLifecycle.CANCELLED,
                    "Material Handling publishes terminal cancellation evidence");
            helper.assertTrue(ItemStack.isSameItemSameComponents(exactOutput, fixture.grinder().inventory().output()),
                    "Source return restores exact Ground Beef to Grinder output");
            helper.assertTrue(fixture.grinder().inventory().output().getCount() == 64,
                    "Cancellation merges one returned Ground Beef into the source remainder");
            helper.assertTrue(fixture.grinder().inventory().input().isEmpty(),
                    "Source return never targets Grinder input");
            helper.assertTrue(fixture.pattyFormer().inventory().input().isEmpty(),
                    "Cancelled custody never reaches the destination");
            helper.assertTrue(fixture.employee().getMainHandItem().isEmpty(),
                    "Carry projection clears after proven return");

            EmployeeMaterialHandlingService.AssignmentResult duplicate = EmployeeMaterialHandlingService.INSTANCE.cancel(
                    helper.getLevel(), fixture.record().employeeId(), "Duplicate cancellation"
            );
            helper.assertTrue(duplicate.status() == EmployeeMaterialHandlingService.AssignmentStatus.CANCELLED,
                    "Duplicate cancellation observes the existing result");
            helper.assertTrue(fixture.grinder().inventory().output().getCount() == 64,
                    "Duplicate cancellation does not duplicate Ground Beef");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = BATCH + "03_reload")
    public static void assignmentReloadRestoresExactGroundBeefCarryFromCustody(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Ground Beef Reload");

        helper.runAtTickTime(100, () -> {
            assertLegitimateGroundBeefOutput(helper, fixture.grinder());
            ItemStack exactOutput = fixture.grinder().inventory().output().copy();
            requestAndWithdraw(helper, fixture, 0);
            EmployeeMaterialHandlingAssignment before = assignment(helper, fixture.record());

            fixture.employee().resetGameTestCarryObservation();
            EmployeeMaterialHandlingService.INSTANCE.reloadGameTestAssignments(helper.getLevel().getServer());
            EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

            EmployeeMaterialHandlingAssignment restored = assignment(helper, fixture.record());
            helper.assertTrue(restored.assignmentId().equals(before.assignmentId()),
                    "Reload preserves deterministic Workforce assignment identity");
            helper.assertTrue(restored.state() == EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION,
                    "Reload reconstructs movement state from proven Material Handling custody");
            helper.assertTrue(ItemStack.isSameItemSameComponents(exactOutput, fixture.employee().getMainHandItem()),
                    "Reload reconstructs exact Ground Beef carry projection without Workforce custody");

            moveAndSynchronize(helper, fixture.employee(), PATTY_FORMER_OPERATING_POS);
            EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());
            helper.assertTrue(assignment(helper, fixture.record()).state()
                            == EmployeeMaterialHandlingAssignmentState.COMPLETED,
                    "Reloaded assignment completes through the original destination endpoint");
            helper.assertTrue(ItemStack.isSameItemSameComponents(exactOutput, fixture.pattyFormer().inventory().input()),
                    "Exact Ground Beef survives custody and assignment reload");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 150, batch = BATCH + "04_replacement")
    public static void replacedPattyFormerPreservesCustodyAndFailsVisibly(GameTestHelper helper) {
        Fixture fixture = setup(helper, "Ground Beef Replacement");

        helper.runAtTickTime(100, () -> {
            assertLegitimateGroundBeefOutput(helper, fixture.grinder());
            requestAndWithdraw(helper, fixture, 0);
            EmployeeMaterialHandlingAssignment carrying = assignment(helper, fixture.record());
            ItemStack exactCustody = fixture.employee().getMainHandItem().copy();

            helper.setBlock(PATTY_FORMER_POS, Blocks.AIR);
            helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState()
                    .setValue(PattyFormerBlock.FACING, Direction.NORTH));
            EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());

            EmployeeMaterialHandlingAssignment blocked = assignment(helper, fixture.record());
            MaterialTransferView transfer = transfer(helper, carrying);
            helper.assertTrue(blocked.state() == EmployeeMaterialHandlingAssignmentState.RECOVERY_REQUIRED,
                    "Replacement destination enters explicit Workforce recovery");
            helper.assertTrue(transfer.hasProvenMaterialHandlingCustody(),
                    "Replacement never discards proven Material Handling custody");
            helper.assertTrue(ItemStack.isSameItemSameComponents(exactCustody, fixture.employee().getMainHandItem()),
                    "Carry projection remains synchronized with proven custody during recovery");
            helper.assertTrue(((PattyFormerBlockEntity) helper.getBlockEntity(PATTY_FORMER_POS))
                            .inventory().input().isEmpty(),
                    "Replacement Patty Former does not inherit the stale destination effect");
            helper.succeed();
        });
    }

    private static Fixture setup(GameTestHelper helper, String employeeName) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        WorkstationReservationService.INSTANCE.resetGameTestReservations(helper.getLevel().getServer());
        EmployeeMaterialHandlingService.INSTANCE.resetGameTestAssignments(helper.getLevel().getServer());
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState()
                .setValue(GrinderBlock.FACING, Direction.SOUTH));
        helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState()
                .setValue(PattyFormerBlock.FACING, Direction.NORTH));
        GrinderBlockEntity grinder = blockEntity(helper, GRINDER_POS, GrinderBlockEntity.class);
        PattyFormerBlockEntity pattyFormer = blockEntity(helper, PATTY_FORMER_POS, PattyFormerBlockEntity.class);
        ItemStack remainder = grinder.inventory().insertItem(
                WorkstationInventory.INPUT_SLOT,
                ModItems.BEEF_TRIM.get().getDefaultInstance(),
                false
        );
        helper.assertTrue(remainder.isEmpty(), "Grinder accepts one Beef Trim for authoritative setup operation");
        helper.assertTrue(grinder.requestEmployeeProcessing(new WorkstationTickContext(
                helper.getLevel(), helper.absolutePos(GRINDER_POS)
        )).accepted(), "Grinder starts the authoritative setup operation");
        EmployeeRecord record = createEmployee(helper, employeeName);
        return new Fixture(grinder, pattyFormer, record, entity(helper, record));
    }

    private static EmployeeRecord createEmployee(GameTestHelper helper, String name) {
        EmployeeRecord created = EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(), Optional.of(name), Optional.of(helper.absolutePos(EMPLOYEE_POS)), true
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

    private static void requestAndWithdraw(GameTestHelper helper, Fixture fixture, int expectedSourceRemainderCount) {
        EmployeeMaterialHandlingService.AssignmentResult requested = EmployeeMaterialHandlingService.INSTANCE.request(
                helper.getLevel(),
                fixture.record().employeeId(),
                helper.absolutePos(GRINDER_POS),
                helper.absolutePos(PATTY_FORMER_POS)
        );
        helper.assertTrue(requested.accepted(), "Ground Beef transfer assignment is accepted: " + requested.detail());
        moveAndSynchronize(helper, fixture.employee(), GRINDER_OPERATING_POS);
        EmployeeMaterialHandlingService.INSTANCE.tick(fixture.employee());
        helper.assertTrue(assignment(helper, fixture.record()).state()
                        == EmployeeMaterialHandlingAssignmentState.CARRYING_TO_DESTINATION,
                "Employee withdraws only after physical Grinder arrival");
        helper.assertTrue(fixture.grinder().inventory().output().getCount() == expectedSourceRemainderCount,
                "Exactly one Ground Beef leaves Grinder output");
        helper.assertTrue(fixture.employee().getMainHandItem().is(ModItems.GROUND_BEEF.get())
                        && fixture.employee().getMainHandItem().getCount() == 1,
                "Employee visibly carries exactly one Ground Beef");
        helper.assertTrue(reservation(helper, fixture.record()).workstationType().equals("patty_former"),
                "Destination reservation follows proven source release");
        assertOneReservation(helper, fixture.record());
    }

    private static int executeTransferCommand(GameTestHelper helper, EmployeeRecord record) {
        BlockPos source = helper.absolutePos(GRINDER_POS);
        BlockPos destination = helper.absolutePos(PATTY_FORMER_POS);
        String command = "butchercraft employee transfer " + record.employeeId().value() + " "
                + source.getX() + " " + source.getY() + " " + source.getZ() + " "
                + destination.getX() + " " + destination.getY() + " " + destination.getZ();
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            return dispatcher.execute(command, helper.getLevel().getServer().createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput());
        } catch (CommandSyntaxException exception) {
            helper.assertTrue(false, "Transfer command should execute: " + exception.getMessage());
            return 0;
        }
    }

    private static void assertLegitimateGroundBeefOutput(GameTestHelper helper, GrinderBlockEntity grinder) {
        helper.assertTrue(grinder.workstationState() == WorkstationState.COMPLETE,
                "Authoritative Grinder operation completes before transfer");
        helper.assertTrue(grinder.inventory().input().isEmpty(), "Grinder operation consumes Beef Trim input");
        helper.assertTrue(grinder.inventory().output().is(ModItems.GROUND_BEEF.get())
                        && grinder.inventory().output().getCount() == 1,
                "Grinder owner publishes exactly one Ground Beef output");
    }

    private static void moveAndSynchronize(GameTestHelper helper, EmployeeEntity employee, BlockPos relativePosition) {
        BlockPos absolute = helper.absolutePos(relativePosition);
        employee.moveTo(absolute.getX() + 0.5D, absolute.getY(), absolute.getZ() + 0.5D, 0.0F, 0.0F);
        helper.assertTrue(EmployeeService.INSTANCE.synchronizeEntity(employee),
                "Employee synchronizes at the physical workstation position");
    }

    private static EmployeeMaterialHandlingAssignment assignment(GameTestHelper helper, EmployeeRecord record) {
        return EmployeeMaterialHandlingService.INSTANCE.latestFor(
                helper.getLevel().getServer(), record.employeeId()
        ).orElseThrow();
    }

    private static MaterialTransferView transfer(
            GameTestHelper helper,
            EmployeeMaterialHandlingAssignment assignment
    ) {
        return MaterialHandlingService.INSTANCE.findTransfer(
                helper.getLevel().getServer(), assignment.transferId()
        ).orElseThrow();
    }

    private static WorkstationReservationRecord reservation(GameTestHelper helper, EmployeeRecord record) {
        return WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                .findByEmployee(record.employeeId().value()).orElseThrow();
    }

    private static void assertOneReservation(GameTestHelper helper, EmployeeRecord record) {
        helper.assertTrue(WorkstationReservationService.INSTANCE.activeReservations(
                        helper.getLevel().getServer()).stream()
                        .filter(value -> value.employeeIdentity().equals(record.employeeId().value()))
                        .count() == 1L,
                "Employee never holds source and destination reservations simultaneously");
    }

    private static Counts counts(GameTestHelper helper) {
        return new Counts(
                ProductionService.INSTANCE.managerFor(helper.getLevel().getServer()).runs().size(),
                SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer()).registry().size(),
                ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer()).operations().size()
        );
    }

    private static void assertCounts(GameTestHelper helper, Counts expected) {
        helper.assertTrue(counts(helper).equals(expected),
                "Ground Beef transport creates no Production, Scheduler, or Execution work");
    }

    private static EmployeeEntity entity(GameTestHelper helper, EmployeeRecord record) {
        Entity entity = helper.getLevel().getEntity(record.entityLink().orElseThrow().entityUuid());
        helper.assertTrue(entity instanceof EmployeeEntity, "Employee record binds a live Employee entity");
        return (EmployeeEntity) entity;
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

    private record Fixture(
            GrinderBlockEntity grinder,
            PattyFormerBlockEntity pattyFormer,
            EmployeeRecord record,
            EmployeeEntity employee
    ) {
    }

    private record Counts(int productionRuns, int schedulerWork, int executionOperations) {
    }
}
