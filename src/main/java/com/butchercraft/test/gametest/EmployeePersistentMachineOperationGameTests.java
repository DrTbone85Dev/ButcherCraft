package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.integration.employee.EmployeePersistentMachineOperationService;
import com.butchercraft.integration.machine.grinder.GrinderContinuousRunService;
import com.butchercraft.machine.cuttingtable.CuttingTableBlock;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlock;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.endpoint.runtime.WorkstationEndpointService;
import com.butchercraft.workstation.operation.MachineOperatingState;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationRole;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.EmployeeMachineOperationAssignmentService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionMachineRunService;
import com.butchercraft.world.MachineOperatingStateService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.execution.MachineRunLifecycle;
import com.butchercraft.world.execution.MachineRunRecord;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignment;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationAssignmentState;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationFailure;
import com.butchercraft.world.workforce.machineoperation.EmployeeMachineOperationFailureCode;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeePersistentMachineOperationGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final String BATCH = "zzzzzzzz_employee_persistent_machine_operation_";
    private static final BlockPos MACHINE_POS = new BlockPos(2, 1, 2);

    private EmployeePersistentMachineOperationGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 350, batch = BATCH + "01_grinder_finite")
    public static void grinderFiniteAssignmentUsesOneRunAndStops(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 3));
        EmployeeRecord employee = employee(helper, "Grinder Operator");

        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 3);

        helper.succeedWhen(() -> assertCompleted(
                helper, assignment, grinder.inventory().input(), grinder.inventory().output(),
                ModItems.GROUND_BEEF.get().getDefaultInstance(), grinder.runStatus().operatingState(), 3, 0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 350, batch = BATCH + "02_patty_finite")
    public static void pattyFormerFiniteAssignmentUsesOneRunAndStops(GameTestHelper helper) {
        setup(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper, MACHINE_POS);
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 3));
        EmployeeRecord employee = employee(helper, "Patty Former Operator");

        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 3);

        helper.succeedWhen(() -> assertCompleted(
                helper, assignment, pattyFormer.inventory().input(), pattyFormer.inventory().output(),
                ModItems.BEEF_PATTIES.get().getDefaultInstance(), pattyFormer.runStatus().operatingState(), 3, 0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 750, batch = BATCH + "03_exact_target")
    public static void targetTenLeavesExcessInputAndAdmitsNoChildEleven(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 64));
        EmployeeRecord employee = employee(helper, "Finite Grinder Operator");
        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 10);

        helper.succeedWhen(() -> assertCompleted(
                helper, assignment, grinder.inventory().input(), grinder.inventory().output(),
                ModItems.GROUND_BEEF.get().getDefaultInstance(), grinder.runStatus().operatingState(), 10, 54));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 4300, batch = BATCH + "04_grinder_64")
    public static void grinderSixtyFourCycleAssignmentIsBounded(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 64));
        EmployeeRecord employee = employee(helper, "Grinder Sixty Four");
        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 64);

        helper.succeedWhen(() -> assertCompleted(
                helper, assignment, grinder.inventory().input(), grinder.inventory().output(),
                ModItems.GROUND_BEEF.get().getDefaultInstance(), grinder.runStatus().operatingState(), 64, 0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 4300, batch = BATCH + "05_patty_64")
    public static void pattyFormerSixtyFourCycleAssignmentIsBounded(GameTestHelper helper) {
        setup(helper);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper, MACHINE_POS);
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 64));
        EmployeeRecord employee = employee(helper, "Patty Sixty Four");
        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 64);

        helper.succeedWhen(() -> assertCompleted(
                helper, assignment, pattyFormer.inventory().input(), pattyFormer.inventory().output(),
                ModItems.BEEF_PATTIES.get().getDefaultInstance(), pattyFormer.runStatus().operatingState(), 64, 0));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "06_cutting_table")
    public static void cuttingTableIsNotAPersistentPoweredMachine(GameTestHelper helper) {
        setup(helper);
        helper.setBlock(MACHINE_POS, ModBlocks.CUTTING_TABLE.get().defaultBlockState()
                .setValue(CuttingTableBlock.FACING, Direction.EAST));
        EmployeeRecord employee = employee(helper, "Cutting Table Operator");

        var result = EmployeePersistentMachineOperationService.INSTANCE.request(
                helper.getLevel(), employee.employeeId(), helper.absolutePos(MACHINE_POS), 1);

        helper.assertTrue(result.status()
                        == EmployeePersistentMachineOperationService.AssignmentStatus.UNSUPPORTED_MACHINE,
                "Manual Cutting Table rejects persistent powered-machine operation");
        helper.assertTrue(EmployeeMachineOperationAssignmentService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .activeFor(employee.employeeId()).isEmpty(),
                "Rejected Cutting Table request creates no assignment");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "07_waiting_input")
    public static void assignmentWithoutInputWaitsWithoutStarting(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord employee = employee(helper, "Waiting Operator");
        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 2);

        helper.succeedWhen(() -> {
            EmployeeMachineOperationAssignment current = assignment(helper, assignment);
            helper.assertTrue(current.state() == EmployeeMachineOperationAssignmentState.WAITING_FOR_INPUT,
                    "Finite assignment waits when neither input nor pending supply exists");
            helper.assertTrue(current.runIdentity().isEmpty(), "Material absence alone creates no Machine Run");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "08_cancel_before_start")
    public static void cancellationBeforeStartReleasesExactOperator(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord employee = employee(helper, "Cancelled Operator");
        EmployeeMachineOperationAssignment assignment = request(helper, employee, MACHINE_POS, 2);

        var cancelled = EmployeePersistentMachineOperationService.INSTANCE.cancel(
                helper.getLevel(), employee.employeeId(), "GameTest cancellation");
        EmployeeMachineOperationAssignment current = assignment(helper, assignment);

        helper.assertTrue(cancelled.accepted(), "Cancellation request is accepted");
        helper.assertTrue(current.state() == EmployeeMachineOperationAssignmentState.CANCELLED,
                "Pre-START assignment reaches CANCELLED");
        helper.assertTrue(current.runIdentity().isEmpty(), "Pre-START cancellation creates no Run");
        helper.assertTrue(activeReservation(helper, employee).isEmpty(),
                "Pre-START cancellation releases the exact operator reservation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 350, batch = BATCH + "09_output_blocked")
    public static void outputBlockedRetainsSameRunAndContinuesWhenCleared(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        grinder.inventory().setInputInternal(ModItems.BEEF_TRIM.get().getDefaultInstance());
        grinder.inventory().setOutputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 64));
        EmployeeRecord employee = employee(helper, "Blocked Grinder Operator");
        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 1);
        String[] runIdentity = {null};
        boolean[] cleared = {false};

        helper.succeedWhen(() -> {
            EmployeeMachineOperationAssignment current = assignment(helper, assignment);
            if (!cleared[0]) {
                helper.assertTrue(current.state() == EmployeeMachineOperationAssignmentState.OUTPUT_BLOCKED,
                        "Assignment exposes OUTPUT_BLOCKED without consuming input");
                runIdentity[0] = current.runIdentity().orElseThrow();
                helper.assertTrue(grinder.inventory().input().getCount() == 1,
                        "OUTPUT_BLOCKED preserves input");
                grinder.inventory().setOutputInternal(ItemStack.EMPTY);
                cleared[0] = true;
                return;
            }
            if (current.state() != EmployeeMachineOperationAssignmentState.COMPLETED) {
                helper.assertTrue(false, "Waiting for assignment completion after clearing output");
            }
            helper.assertTrue(current.runIdentity().orElseThrow().equals(runIdentity[0]),
                    "Output recovery retains the exact same Machine Run");
            helper.assertTrue(grinder.inventory().output().getCount() == 1,
                    "Cleared output permits exactly one bounded child");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "10_operator_conflict")
    public static void secondEmployeeWaitsForExistingOperator(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord first = employee(helper, "First Operator");
        EmployeeRecord second = employee(helper, "Second Operator");

        request(helper, first, MACHINE_POS, 1);
        EmployeeMachineOperationAssignment waiting = request(helper, second, MACHINE_POS, 1);

        helper.assertTrue(waiting.state() == EmployeeMachineOperationAssignmentState.WAITING_FOR_RESERVATION,
                "Conflicting employee assignment waits without preemption");
        long operators = WorkstationReservationService.INSTANCE.reservationsForWorkstation(
                        helper.getLevel(), helper.absolutePos(MACHINE_POS)).stream()
                .filter(WorkstationReservationRecord::active)
                .filter(value -> value.role() == WorkstationReservationRole.MACHINE_OPERATOR)
                .count();
        helper.assertTrue(operators == 1L, "Exactly one MACHINE_OPERATOR is active");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "11_command")
    public static void synchronizedCommandCreatesExactFiniteAssignment(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord employee = employee(helper, "Command Operator");
        BlockPos target = helper.absolutePos(MACHINE_POS);
        String prefix = "butchercraft employee operate ";

        helper.assertTrue(suggestions(helper, prefix).contains("#1"),
                "Friendly employee reference is suggested by the synchronized command");
        helper.assertTrue(execute(helper, prefix + "#1 " + target.getX() + " " + target.getY() + " "
                        + target.getZ() + " 7") == 1,
                "Command accepts exact workstation coordinates and positive finite target");
        EmployeeMachineOperationAssignment assignment = EmployeeMachineOperationAssignmentService.INSTANCE
                .managerFor(helper.getLevel().getServer()).activeFor(employee.employeeId()).orElseThrow();
        helper.assertTrue(assignment.targetQuantity() == 7, "Command persists the requested finite target");
        helper.assertTrue(assignment.employeeId().equals(employee.employeeId()),
                "Command binds the exact resolved employee");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 350, batch = BATCH + "12_player_stop")
    public static void playerStopInterruptsEmployeeAssignmentWithoutRestart(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 3));
        EmployeeRecord employee = employee(helper, "Interrupted Operator");
        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 3);
        boolean[] stopRequested = {false};

        helper.succeedWhen(() -> {
            EmployeeMachineOperationAssignment current = assignment(helper, assignment);
            if (!stopRequested[0]) {
                helper.assertTrue(current.runIdentity().isPresent(), "Waiting for employee Machine Run START");
                helper.assertTrue(GrinderContinuousRunService.INSTANCE.stop(helper.getLevel(), grinder).accepted(),
                        "Player STOP is accepted through the canonical Run boundary");
                stopRequested[0] = true;
                return;
            }
            helper.assertTrue(current.state() == EmployeeMachineOperationAssignmentState.INTERRUPTED,
                    "Player STOP makes incomplete employee assignment terminal and interrupted");
            helper.assertTrue(current.completedQuantity() < current.targetQuantity(),
                    "Interrupted assignment does not fabricate completion");
            helper.assertTrue(run(helper, current).lifecycle() == MachineRunLifecycle.STOPPED,
                    "Player STOP terminates the exact employee Run safely");
            helper.assertTrue(activeReservation(helper, employee).isEmpty(),
                    "Player interruption releases only the exact operator reservation");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 350, batch = BATCH + "13_different_machines")
    public static void differentEmployeesOperateDifferentMachinesConcurrently(GameTestHelper helper) {
        setup(helper);
        BlockPos grinderPos = new BlockPos(1, 1, 2);
        BlockPos pattyPos = new BlockPos(3, 1, 2);
        GrinderBlockEntity grinder = placeGrinder(helper, grinderPos);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper, pattyPos);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 2));
        pattyFormer.inventory().setInputInternal(count(ModItems.GROUND_BEEF.get().getDefaultInstance(), 2));
        EmployeeRecord grinderEmployee = employee(helper, "Concurrent Grinder Operator");
        EmployeeRecord pattyEmployee = employee(helper, "Concurrent Patty Operator");
        EmployeeMachineOperationAssignment grinderAssignment = requestAndArrive(
                helper, grinderEmployee, grinderPos, 2);
        EmployeeMachineOperationAssignment pattyAssignment = requestAndArrive(
                helper, pattyEmployee, pattyPos, 2);

        helper.succeedWhen(() -> {
            assertCompleted(helper, grinderAssignment, grinder.inventory().input(), grinder.inventory().output(),
                    ModItems.GROUND_BEEF.get().getDefaultInstance(), grinder.runStatus().operatingState(), 2, 0);
            assertCompleted(helper, pattyAssignment, pattyFormer.inventory().input(),
                    pattyFormer.inventory().output(), ModItems.BEEF_PATTIES.get().getDefaultInstance(),
                    pattyFormer.runStatus().operatingState(), 2, 0);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = BATCH + "14_exact_orphan_reservation")
    public static void consequenceFreeReplacementCancellationReleasesOnlyExactOldReservation(
            GameTestHelper helper
    ) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        grinder.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 2));
        EmployeeRecord employee = employee(helper, "Orphaned Operator");
        EmployeeMachineOperationAssignment assignment = request(helper, employee, MACHINE_POS, 2);
        WorkstationReservationRecord reservation = activeReservation(helper, employee).orElseThrow();
        helper.assertTrue(WorkstationEndpointService.INSTANCE.retireEndpoint(
                        helper.getLevel(), helper.absolutePos(MACHINE_POS)),
                "Workstation owner proves exact retirement before reservation cleanup");
        publishReplacementRecovery(helper, assignment);

        var cancelled = EmployeePersistentMachineOperationService.INSTANCE.cancel(
                helper.getLevel(), employee.employeeId(), "GameTest replaced Workstation cancellation");
        EmployeeMachineOperationAssignment terminal = assignment(helper, assignment);
        WorkstationReservationRecord released = WorkstationReservationService.INSTANCE
                .managerFor(helper.getLevel().getServer()).findById(reservation.reservationId()).orElseThrow();

        helper.assertTrue(cancelled.status()
                        == EmployeePersistentMachineOperationService.AssignmentStatus.CANCELLED,
                "Exact consequence-free replacement cancellation reaches CANCELLED");
        helper.assertTrue(terminal.state() == EmployeeMachineOperationAssignmentState.CANCELLED,
                "Historical assignment is terminal");
        helper.assertTrue(!released.active()
                        && released.assignmentReference().filter(assignment.assignmentId().value()::equals).isPresent(),
                "Only the exact old assignment reservation is released");
        helper.assertTrue(grinder.runStatus().operatingState() == MachineOperatingState.OFF
                        && grinder.runStatus().runIdentity().isEmpty(),
                "Cancellation does not START or STOP the Workstation");
        helper.assertTrue(grinder.inventory().input().getCount() == 2 && grinder.inventory().output().isEmpty(),
                "Cancellation does not mutate Workstation inventory");
        helper.setBlock(MACHINE_POS, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = BATCH + "15_replacement_terminalization")
    public static void replacedPreStartAssignmentTerminalizesWithoutTouchingReplacement(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord orphanedEmployee = employee(helper, "Replaced Operator");
        EmployeeMachineOperationAssignment orphaned = request(helper, orphanedEmployee, MACHINE_POS, 5);
        WorkstationReservationRecord historicalReservation = activeReservation(helper, orphanedEmployee).orElseThrow();

        helper.setBlock(MACHINE_POS, Blocks.AIR);
        GrinderBlockEntity replacement = placeGrinder(helper, MACHINE_POS);
        replacement.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 2));
        EmployeeRecord replacementEmployee = employee(helper, "Replacement Operator");
        EmployeeMachineOperationAssignment replacementAssignment = requestAndArrive(
                helper, replacementEmployee, MACHINE_POS, 5);
        WorkstationReservationRecord replacementReservation = activeReservation(
                helper, replacementEmployee).orElseThrow();

        helper.assertTrue(!replacementAssignment.workstation().instanceId().equals(orphaned.workstation().instanceId()),
                "Replacement receives a distinct Workstation Instance Identity");
        helper.succeedWhen(() -> {
            EmployeeMachineOperationAssignment recovery = assignment(helper, orphaned);
            helper.assertTrue(recovery.state() == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED,
                    "Waiting for exact replacement mismatch detection: " + recovery.state());
            helper.assertTrue(recovery.failure().filter(value -> value.code()
                            == EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED).isPresent(),
                    "Replacement mismatch remains explicit recovery evidence");
            EmployeeMachineOperationAssignment runningReplacement = assignment(helper, replacementAssignment);
            helper.assertTrue(runningReplacement.runIdentity().isPresent(), "Waiting for replacement Run START");
            MachineRunRecord replacementRun = run(helper, runningReplacement);
            helper.assertTrue(!replacementRun.lifecycle().terminal(), "Replacement has its own active Run");
            ItemStack inputBefore = replacement.inventory().input().copy();
            ItemStack outputBefore = replacement.inventory().output().copy();
            MachineOperatingState stateBefore = replacement.runStatus().operatingState();

            var first = EmployeePersistentMachineOperationService.INSTANCE.cancel(
                    helper.getLevel(), orphanedEmployee.employeeId(), "GameTest operator cancellation");
            var duplicate = EmployeePersistentMachineOperationService.INSTANCE.cancel(
                    helper.getLevel(), orphanedEmployee.employeeId(), "GameTest duplicate operator cancellation");
            EmployeeMachineOperationAssignment terminal = assignment(helper, orphaned);
            WorkstationReservationRecord oldRecord = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findById(historicalReservation.reservationId()).orElseThrow();
            WorkstationReservationRecord replacementRecord = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findById(replacementReservation.reservationId()).orElseThrow();

            helper.assertTrue(first.status()
                            == EmployeePersistentMachineOperationService.AssignmentStatus.CANCELLED
                            && duplicate.status()
                            == EmployeePersistentMachineOperationService.AssignmentStatus.CANCELLED,
                    "Repeated cancellation observes the same terminal result");
            helper.assertTrue(terminal.state() == EmployeeMachineOperationAssignmentState.CANCELLED
                            && terminal.failure().equals(recovery.failure()),
                    "Terminal historical evidence retains the replacement failure");
            helper.assertTrue(!oldRecord.active(), "Historical reservation is already terminal or safely released");
            helper.assertTrue(replacementRecord.active(), "Replacement assignment reservation remains active");
            helper.assertTrue(replacement.runStatus().operatingState() == stateBefore
                            && run(helper, runningReplacement).equals(replacementRun),
                    "Replacement machine and active Run remain unchanged and receive no STOP");
            helper.assertTrue(ItemStack.matches(replacement.inventory().input(), inputBefore)
                            && ItemStack.matches(replacement.inventory().output(), outputBefore),
                    "Replacement inventory remains untouched");

            var next = EmployeePersistentMachineOperationService.INSTANCE.request(
                    helper.getLevel(), orphanedEmployee.employeeId(), helper.absolutePos(MACHINE_POS), 1);
            helper.assertTrue(next.accepted(), "Terminal orphan no longer blocks a new explicit assignment");
            EmployeeMachineOperationAssignment created = EmployeeMachineOperationAssignmentService.INSTANCE
                    .managerFor(helper.getLevel().getServer()).activeFor(orphanedEmployee.employeeId()).orElseThrow();
            helper.assertTrue(!created.assignmentId().equals(orphaned.assignmentId()),
                    "New request creates a distinct assignment while preserving history");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 240, batch = BATCH + "16_replacement_with_run")
    public static void replacementWithHistoricalRunRemainsRecoveryRequired(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity original = placeGrinder(helper, MACHINE_POS);
        original.inventory().setInputInternal(count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 3));
        EmployeeRecord employee = employee(helper, "Protected Historical Operator");
        EmployeeMachineOperationAssignment assignment = requestAndArrive(helper, employee, MACHINE_POS, 3);
        String[] historicalRun = {null};
        GrinderBlockEntity[] replacement = {null};
        WorkstationReservationRecord[] replacementReservation = {null};
        boolean[] replaced = {false};

        helper.succeedWhen(() -> {
            EmployeeMachineOperationAssignment current = assignment(helper, assignment);
            if (!replaced[0]) {
                helper.assertTrue(current.runIdentity().isPresent(), "Waiting for historical Machine Run START");
                MachineRunRecord run = run(helper, current);
                helper.assertTrue(run.currentChild().isPresent(), "Waiting for a nonterminal historical child");
                historicalRun[0] = run.runIdentity().value();
                helper.setBlock(MACHINE_POS, Blocks.AIR);
                replacement[0] = placeGrinder(helper, MACHINE_POS);
                replacement[0].inventory().setInputInternal(
                        count(ModItems.BEEF_TRIM.get().getDefaultInstance(), 2));
                EmployeeRecord replacementEmployee = employee(helper, "Protected Replacement Operator");
                request(helper, replacementEmployee, MACHINE_POS, 1);
                replacementReservation[0] = activeReservation(helper, replacementEmployee).orElseThrow();
                replaced[0] = true;
                helper.assertTrue(false, "Waiting for replacement reconciliation on the next tick");
            }

            helper.assertTrue(current.state() == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED,
                    "Waiting for replaced active Run to enter RECOVERY_REQUIRED: " + current.state());
            var cancelled = EmployeePersistentMachineOperationService.INSTANCE.cancel(
                    helper.getLevel(), employee.employeeId(), "Unsafe GameTest cancellation");
            EmployeeMachineOperationAssignment protectedAssignment = assignment(helper, assignment);
            WorkstationReservationRecord replacementRecord = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findById(replacementReservation[0].reservationId()).orElseThrow();

            helper.assertTrue(cancelled.status()
                            == EmployeePersistentMachineOperationService.AssignmentStatus.RECOVERY_REQUIRED,
                    "Historical Run evidence keeps cancellation fail-closed");
            helper.assertTrue(protectedAssignment.active()
                            && protectedAssignment.state()
                            == EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED,
                    "Consequential assignment remains active recovery evidence");
            helper.assertTrue(protectedAssignment.runIdentity().filter(historicalRun[0]::equals).isPresent(),
                    "Exact historical Run binding is retained");
            helper.assertTrue(ExecutionMachineRunService.INSTANCE.find(
                            helper.getLevel().getServer(),
                            new com.butchercraft.world.execution.MachineRunIdentity(historicalRun[0])).isPresent(),
                    "Historical Run is not deleted or reinterpreted");
            helper.assertTrue(replacementRecord.active(), "Replacement reservation is not released");
            helper.assertTrue(replacement[0].runStatus().operatingState() == MachineOperatingState.OFF
                            && replacement[0].runStatus().runIdentity().isEmpty(),
                    "Replacement machine is not STARTed or STOPped");
            helper.assertTrue(replacement[0].inventory().input().getCount() == 2
                            && replacement[0].inventory().output().isEmpty(),
                    "Replacement inventory remains untouched");
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "17_unproven_replacement")
    public static void replacementLabelWithoutRetirementCannotClearAssignment(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord employee = employee(helper, "Unproven Replacement");
        EmployeeMachineOperationAssignment original = request(helper, employee, MACHINE_POS, 5);
        WorkstationReservationRecord reservation = activeReservation(helper, employee).orElseThrow();
        publishReplacementRecovery(helper, original);
        assertRecoveryCancellationBlocked(helper, original);
        helper.assertTrue(activeReservation(helper, employee).orElseThrow().equals(reservation),
                "Unproven retirement cannot release the reservation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "18_no_reservation_orphan")
    public static void replacedAssignmentWithoutReservationCancelsThroughCommand(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord blocker = employee(helper, "Original Operator");
        request(helper, blocker, MACHINE_POS, 1);
        EmployeeRecord employee = employee(helper, "Waiting Operator");
        EmployeeMachineOperationAssignment original = request(helper, employee, MACHINE_POS, 5);
        helper.assertTrue(original.reservationId().isEmpty() && original.runIdentity().isEmpty(),
                "Fixture assignment never acquired a reservation or Run");
        helper.setBlock(MACHINE_POS, Blocks.AIR);
        GrinderBlockEntity replacement = placeGrinder(helper, MACHINE_POS);
        publishReplacementRecovery(helper, original);
        String command = "butchercraft employee operate-cancel \"Waiting Operator\"";
        helper.assertTrue(execute(helper, command) > 0, "Operator cancellation command succeeds");
        EmployeeMachineOperationAssignment terminal = assignment(helper, original);
        helper.assertTrue(terminal.state() == EmployeeMachineOperationAssignmentState.CANCELLED,
                "No-reservation orphan reaches CANCELLED");
        helper.assertTrue(execute(helper, command) > 0 && assignment(helper, original).equals(terminal),
                "Duplicate command observes the identical terminal record");
        helper.assertTrue(replacement.runStatus().operatingState() == MachineOperatingState.OFF
                        && replacement.inventory().input().isEmpty() && replacement.inventory().output().isEmpty(),
                "Command leaves replacement machine untouched");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "19_unobserved_start")
    public static void canonicalStartNotYetObservedByAssignmentBlocksCancellation(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        EmployeeRecord employee = employee(helper, "Unobserved Start");
        EmployeeMachineOperationAssignment original = requestAndArrive(helper, employee, MACHINE_POS, 5);
        var started = GrinderContinuousRunService.INSTANCE.startForEmployee(
                helper.getLevel(), grinder, EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER,
                EmployeeMachineOperationAssignmentService.startRequestIdentity(original.assignmentId().value()),
                original.reservationId().orElseThrow().value());
        helper.assertTrue(started.accepted() && started.runIdentity().isPresent(),
                "Execution accepts START before Workforce observes the result");
        helper.assertTrue(assignment(helper, original).runIdentity().isEmpty(),
                "Assignment does not yet contain its canonical Run reference");
        helper.setBlock(MACHINE_POS, Blocks.AIR);
        placeGrinder(helper, MACHINE_POS);
        publishReplacementRecovery(helper, original);
        assertRecoveryCancellationBlocked(helper, original);
        helper.assertTrue(ExecutionMachineRunService.INSTANCE.find(
                        helper.getLevel().getServer(), started.runIdentity().orElseThrow()).isPresent(),
                "Unobserved START evidence is preserved");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "20_unresolved_stop")
    public static void replacementDuringUnfinishedStopRetainsRecovery(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper, MACHINE_POS);
        EmployeeRecord employee = employee(helper, "Unfinished Stop");
        EmployeeMachineOperationAssignment original = requestAndArrive(helper, employee, MACHINE_POS, 5);
        var started = GrinderContinuousRunService.INSTANCE.startForEmployee(
                helper.getLevel(), grinder, EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER,
                EmployeeMachineOperationAssignmentService.startRequestIdentity(original.assignmentId().value()),
                original.reservationId().orElseThrow().value());
        helper.assertTrue(started.accepted(), "Canonical owner path accepts START");
        var server = helper.getLevel().getServer();
        MachineRunRecord running = ExecutionMachineRunService.INSTANCE.find(
                server, started.runIdentity().orElseThrow()).orElseThrow();
        var operating = MachineOperatingStateService.INSTANCE.find(
                server, running.workstationInstanceIdentity()).orElseThrow();
        long tick = running.lastUpdatedSimulationTick();
        var authorized = MachineOperatingStateService.INSTANCE.authorizeStop(
                server, running.runIdentity(), running.revision(), operating.revision(),
                EmployeeMachineOperationAssignmentService.RUN_SOURCE_OWNER,
                EmployeeMachineOperationAssignmentService.stopRequestIdentity(original.assignmentId().value()),
                running.configurationIdentity(), tick);
        helper.assertTrue(authorized.accepted(), "Workstation authorizes exact STOP");
        // Model interruption after Execution acceptance but before Workstation STOP publication.
        var stopped = ExecutionMachineRunService.INSTANCE.acceptStop(
                server, authorized.stopEvidence().orElseThrow(), tick);
        helper.assertTrue(stopped.accepted()
                        && stopped.run().orElseThrow().lifecycle() == MachineRunLifecycle.STOP_REQUESTED,
                "Accepted STOP is unresolved at the owner publication boundary");
        helper.setBlock(MACHINE_POS, Blocks.AIR);
        placeGrinder(helper, MACHINE_POS);
        publishReplacementRecovery(helper, original);
        assertRecoveryCancellationBlocked(helper, original);
        helper.assertTrue(ExecutionMachineRunService.INSTANCE.find(server, running.runIdentity()).orElseThrow()
                        .stopEvidence().equals(stopped.run().orElseThrow().stopEvidence()),
                "Cancellation preserves exact unfinished STOP evidence");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "21_ordinary_recovery")
    public static void nonReplacementRecoveryCannotBeCancelledAsOrphan(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper, MACHINE_POS);
        EmployeeRecord employee = employee(helper, "Ordinary Recovery");
        EmployeeMachineOperationAssignment original = request(helper, employee, MACHINE_POS, 5);
        EmployeeMachineOperationAssignmentService.INSTANCE.managerFor(helper.getLevel().getServer()).publish(
                original.assignmentId(), EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED, 0,
                Optional.empty(), original.reservationId(), Optional.empty(), 0L, 0L,
                Optional.of(new EmployeeMachineOperationFailure(EmployeeMachineOperationFailureCode.RECOVERY_REQUIRED,
                        "unresolved owner result")), helper.getLevel().getGameTime());
        assertRecoveryCancellationBlocked(helper, original);
        helper.succeed();
    }

    private static void assertRecoveryCancellationBlocked(
            GameTestHelper helper,
            EmployeeMachineOperationAssignment original
    ) {
        EmployeeMachineOperationAssignment before = assignment(helper, original);
        var result = EmployeePersistentMachineOperationService.INSTANCE.cancel(
                helper.getLevel(), original.employeeId(), "GameTest fail-closed cancellation");
        helper.assertTrue(result.status() == EmployeePersistentMachineOperationService.AssignmentStatus.RECOVERY_REQUIRED,
                "Consequential or unproven recovery cannot be terminalized: " + result.detail());
        helper.assertTrue(assignment(helper, original).equals(before),
                "Blocked cancellation preserves exact assignment evidence");
    }

    private static void assertCompleted(
            GameTestHelper helper,
            EmployeeMachineOperationAssignment original,
            ItemStack input,
            ItemStack output,
            ItemStack expectedOutput,
            MachineOperatingState operatingState,
            int target,
            int expectedInput
    ) {
        EmployeeMachineOperationAssignment current = assignment(helper, original);
        helper.assertTrue(current.state() == EmployeeMachineOperationAssignmentState.COMPLETED,
                "Waiting for finite assignment completion: " + current.state());
        MachineRunRecord run = run(helper, current);
        helper.assertTrue(current.completedQuantity() == target, "Completed quantity equals finite target");
        helper.assertTrue(run.terminalChildren().size() == target, "Exactly target bounded children are terminal");
        helper.assertTrue(run.nextChildSequence() == target + 1L, "No child beyond target is admitted");
        helper.assertTrue(run.lifecycle() == MachineRunLifecycle.STOPPED, "Exact Machine Run reaches STOPPED");
        helper.assertTrue(operatingState == MachineOperatingState.OFF, "Workstation reaches OFF after safe STOP");
        helper.assertTrue(input.getCount() == expectedInput, "Expected finite input remainder is preserved");
        helper.assertTrue(output.is(expectedOutput.getItem()) && output.getCount() == target,
                "Expected exact output quantity is produced");
        helper.assertTrue(activeReservation(helper, current.employeeId()).isEmpty(),
                "Exact operator reservation releases only after terminal Run");
    }

    private static EmployeeMachineOperationAssignment requestAndArrive(
            GameTestHelper helper,
            EmployeeRecord employee,
            BlockPos position,
            int target
    ) {
        EmployeeMachineOperationAssignment assignment = request(helper, employee, position, target);
        WorkstationReservationRecord reservation = activeReservation(helper, employee).orElseThrow();
        EmployeeEntity entity = entity(helper, employee);
        entity.moveTo(reservation.operatingX() + 0.5D, reservation.operatingY(),
                reservation.operatingZ() + 0.5D, 0.0F, 0.0F);
        EmployeeService.INSTANCE.synchronizeEntity(entity);
        helper.assertTrue(activeReservation(helper, employee).orElseThrow().state()
                        == WorkstationReservationState.EMPLOYEE_ARRIVED,
                "Employee reaches exact operator position");
        return assignment;
    }

    private static EmployeeMachineOperationAssignment request(
            GameTestHelper helper,
            EmployeeRecord employee,
            BlockPos position,
            int target
    ) {
        var result = EmployeePersistentMachineOperationService.INSTANCE.request(
                helper.getLevel(), employee.employeeId(), helper.absolutePos(position), target);
        helper.assertTrue(result.accepted(), "Persistent machine-operation assignment is accepted: " + result.detail());
        return EmployeeMachineOperationAssignmentService.INSTANCE.managerFor(helper.getLevel().getServer())
                .activeFor(employee.employeeId()).orElseThrow();
    }

    private static EmployeeMachineOperationAssignment assignment(
            GameTestHelper helper,
            EmployeeMachineOperationAssignment original
    ) {
        return EmployeeMachineOperationAssignmentService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(original.assignmentId()).orElseThrow();
    }

    private static void publishReplacementRecovery(
            GameTestHelper helper,
            EmployeeMachineOperationAssignment assignment
    ) {
        EmployeeMachineOperationAssignmentService.INSTANCE.managerFor(helper.getLevel().getServer()).publish(
                assignment.assignmentId(), EmployeeMachineOperationAssignmentState.RECOVERY_REQUIRED,
                assignment.completedQuantity(), assignment.runIdentity(), assignment.reservationId(),
                assignment.pendingSupplyTransferIdentity(), assignment.observedRunRevision(),
                assignment.observedChildSequence(), Optional.of(new EmployeeMachineOperationFailure(
                        EmployeeMachineOperationFailureCode.WORKSTATION_REPLACED,
                        "exact assigned Workstation Instance was replaced")),
                Math.max(assignment.lastUpdatedTick(), helper.getLevel().getGameTime()));
    }

    private static MachineRunRecord run(GameTestHelper helper, EmployeeMachineOperationAssignment assignment) {
        return ExecutionMachineRunService.INSTANCE.find(
                helper.getLevel().getServer(),
                new com.butchercraft.world.execution.MachineRunIdentity(assignment.runIdentity().orElseThrow())
        ).orElseThrow();
    }

    private static Optional<WorkstationReservationRecord> activeReservation(
            GameTestHelper helper,
            EmployeeRecord employee
    ) {
        return activeReservation(helper, employee.employeeId());
    }

    private static Optional<WorkstationReservationRecord> activeReservation(
            GameTestHelper helper,
            com.butchercraft.world.workforce.employee.EmployeeId employeeId
    ) {
        return WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                .findByEmployee(employeeId.value()).filter(WorkstationReservationRecord::active);
    }

    private static EmployeeRecord employee(GameTestHelper helper, String name) {
        EmployeeRecord created = EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(), Optional.of(name), Optional.of(helper.absolutePos(new BlockPos(0, 1, 0))), true
        ).orThrow();
        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(), created.employeeId(), DepartmentSchema.PROCESSING.value()).orThrow();
        EmployeeService.INSTANCE.setPresence(
                helper.getLevel().getServer(), created.employeeId(), EmployeePresenceState.PRESENT).orThrow();
        return EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(created.employeeId()).orElseThrow();
    }

    private static EmployeeEntity entity(GameTestHelper helper, EmployeeRecord employee) {
        Entity entity = helper.getLevel().getEntity(employee.entityLink().orElseThrow().entityUuid());
        helper.assertTrue(entity instanceof EmployeeEntity, "Employee record links exact live Employee entity");
        return (EmployeeEntity) entity;
    }

    private static GrinderBlockEntity placeGrinder(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.GRINDER.get().defaultBlockState().setValue(GrinderBlock.FACING, Direction.EAST));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(blockEntity instanceof GrinderBlockEntity, "Expected Grinder block entity");
        return (GrinderBlockEntity) blockEntity;
    }

    private static PattyFormerBlockEntity placePattyFormer(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.PATTY_FORMER.get().defaultBlockState()
                .setValue(PattyFormerBlock.FACING, Direction.EAST));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(blockEntity instanceof PattyFormerBlockEntity, "Expected Patty Former block entity");
        return (PattyFormerBlockEntity) blockEntity;
    }

    private static void setup(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
    }

    private static ItemStack count(ItemStack stack, int count) {
        stack.setCount(count);
        return stack;
    }

    private static CommandSourceStack commandSource(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack().withPermission(4).withSuppressedOutput();
    }

    private static List<String> suggestions(GameTestHelper helper, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, commandSource(helper))).join().getList()
                .stream().map(Suggestion::getText).toList();
    }

    private static int execute(GameTestHelper helper, String command) {
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher()
                    .execute(command, commandSource(helper));
        } catch (CommandSyntaxException exception) {
            helper.assertTrue(false, "Command should execute: " + command + " | " + exception.getMessage());
            return 0;
        }
    }
}
