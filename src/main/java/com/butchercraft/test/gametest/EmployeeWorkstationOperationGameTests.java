package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.integration.employee.EmployeeWorkstationOperationService;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlock;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.registration.ModItems;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationTickContext;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.execution.ExecutionOperationId;
import com.butchercraft.world.execution.ExecutionOperationSnapshot;
import com.butchercraft.world.execution.ExecutionStatus;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import com.butchercraft.world.workforce.employee.EmployeeWorkstationOperationState;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeeWorkstationOperationGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final String BATCH = "zzzzzzz_employee_workstation_operation_";
    private static final BlockPos GRINDER_POS = new BlockPos(2, 1, 2);
    private static final BlockPos OPERATING_POS = GRINDER_POS.relative(Direction.EAST);

    private EmployeeWorkstationOperationGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = BATCH + "01_lifecycle")
    public static void employeeStartsGrinderWaitsAndObservesOwnerCompletion(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Success");
        EmployeeEntity employee = assign(helper, record);
        boolean[] sawProcessing = {false};
        boolean[] sawWaiting = {false};

        insert(helper, grinder, beefTrim());
        GrinderBlockEntity.serverTick(
                helper.getLevel(),
                helper.absolutePos(GRINDER_POS),
                grinder.getBlockState(),
                grinder
        );
        helper.assertTrue(grinder.workstationState() != WorkstationState.PROCESSING,
                "Reserved Grinder does not process before the employee request");
        helper.assertTrue(grinder.inventory().input().is(ModItems.BEEF_TRIM.get()),
                "Reserved Grinder preserves preloaded input for the employee request");
        helper.assertTrue(newOperations(helper, before).isEmpty(),
                "Reserved Grinder creates no Execution operation before the employee request");
        arrive(helper, record, employee);
        EmployeeWorkstationOperationService.INSTANCE.tick(employee);
        helper.assertTrue(employee.workstationOperationState() == EmployeeWorkstationOperationState.IDLE,
                "Arrival alone does not initiate employee operation");
        helper.assertTrue(employeeNumberReference(record).equals("#1"),
                "First reset GameTest employee is available as #1");
        helper.assertTrue(suggestions(helper, "butchercraft employee operate ").contains("#1"),
                "Employee operation suggestions expose the executable #1 reference");
        helper.assertTrue(operate(helper, employeeNumberReference(record)) == 1,
                "Operator command accepts the #1 employee reference");

        helper.succeedWhen(() -> {
            if (grinder.workstationState() == WorkstationState.PROCESSING) {
                sawProcessing[0] = true;
            }
            if (employee.workstationOperationState()
                    == EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION) {
                sawWaiting[0] = true;
            }
            EmployeeEntity.EmployeeOperationDiagnostics diagnostics = employee.workstationOperationDiagnostics();
            helper.assertTrue(employee.workstationOperationState()
                            == EmployeeWorkstationOperationState.OPERATION_COMPLETE,
                    "Employee observes complete owner and Execution results: " + diagnostics);
            helper.assertTrue(sawProcessing[0],
                    "Command employee request was observed processing through the Grinder controller");
            helper.assertTrue(sawWaiting[0],
                    "Employee was observed waiting for completion");
            helper.assertTrue(grinder.workstationState() == WorkstationState.COMPLETE,
                    "Grinder publishes complete workstation state");
            helper.assertTrue(grinder.inventory().input().isEmpty(), "Grinder owner consumes Beef Trim");
            helper.assertTrue(grinder.inventory().output().is(ModItems.GROUND_BEEF.get()),
                    "Grinder owner produces Ground Beef");
            helper.assertTrue(grinder.inventory().output().getCount() == 1,
                    "Exactly one Ground Beef remains in the Grinder");
            helper.assertTrue(employee.getMainHandItem().isEmpty() && employee.getOffhandItem().isEmpty(),
                    "Employee neither carries nor collects product");

            ExecutionOperationSnapshot execution = onlyNewOperation(helper, before);
            helper.assertTrue(execution.status() == ExecutionStatus.SUCCEEDED,
                    "Execution reaches succeeded state");
            helper.assertTrue(execution.ownerResultEvidence().isPresent(),
                    "Execution observes Grinder owner result evidence");
            helper.assertTrue(execution.resultEvidence().isPresent(),
                    "Execution publishes terminal result evidence");
            helper.assertTrue(diagnostics.executionId().equals(execution.operationId().value()),
                    "Employee diagnostics bind the observed Execution identity");
            helper.assertTrue(diagnostics.recipe().equals("butchercraft:grind_beef"),
                    "Employee diagnostics bind the one authorized recipe");
            helper.assertTrue(activeReservation(helper, record.employeeId()).state()
                            == WorkstationReservationState.EMPLOYEE_ARRIVED,
                    "Reservation remains correctly arrived while employee waits");
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "02_missing_input")
    public static void missingBeefTrimFailsOnceWithoutExecution(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Missing Input");
        EmployeeEntity employee = assignAndArrive(helper, record);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Missing Beef Trim command request is rejected");

        assertFailure(helper, employee, "missing_beef_trim");
        helper.assertTrue(grinder.workstationState() == WorkstationState.IDLE,
                "Missing input leaves Grinder idle");
        helper.assertTrue(newOperations(helper, before).isEmpty(),
                "Missing input issues no Execution operation");
        repeatFailureTickDoesNotRetry(helper, employee, before);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "03_invalid_recipe")
    public static void invalidRecipeFailsWithoutStartingGrinder(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, porkTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Invalid Recipe");
        EmployeeEntity employee = assignAndArrive(helper, record);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Invalid recipe command request is rejected");

        assertFailure(helper, employee, "invalid_recipe");
        helper.assertTrue(grinder.workstationState() == WorkstationState.READY,
                "Unsupported employee recipe remains unprocessed in Grinder-owned input");
        helper.assertTrue(grinder.inventory().input().is(ModItems.PORK_TRIM.get()),
                "Employee does not remove invalid recipe input");
        helper.assertTrue(newOperations(helper, before).isEmpty(),
                "Invalid employee recipe issues no Execution operation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "04_blocked_output")
    public static void blockedOutputFailsWithoutMutatingSlots(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        grinder.inventory().setOutputInternal(groundBeef());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Blocked Output");
        EmployeeEntity employee = assignAndArrive(helper, record);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Blocked output command request is rejected");

        assertFailure(helper, employee, "blocked_output");
        helper.assertTrue(grinder.inventory().input().is(ModItems.BEEF_TRIM.get()),
                "Blocked output preserves workstation-owned input");
        helper.assertTrue(grinder.inventory().output().is(ModItems.GROUND_BEEF.get()),
                "Blocked output preserves existing workstation output");
        helper.assertTrue(newOperations(helper, before).isEmpty(),
                "Blocked output issues no Execution operation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "05_occupied")
    public static void existingPlayerOperationRejectsEmployeeAsOccupied(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        var playerRequest = grinder.requestEmployeeProcessing(new WorkstationTickContext(
                helper.getLevel(),
                helper.absolutePos(GRINDER_POS)
        ));
        helper.assertTrue(playerRequest.accepted(), "Existing normal Grinder request starts processing");
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Occupied");
        EmployeeEntity employee = assignAndArrive(helper, record);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Occupied Grinder command request is rejected");

        assertFailure(helper, employee, "occupied_workstation");
        helper.assertTrue(newOperations(helper, before).size() == 1,
                "Player and employee requests cannot create simultaneous operations");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "06_duplicate")
    public static void duplicateEmployeeRequestsKeepOneExecution(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Duplicate");
        EmployeeEntity employee = assignAndArrive(helper, record);

        startEmployeeOperation(helper, employee);
        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Duplicate command request is rejected");
        for (int index = 0; index < 8; index++) {
            EmployeeWorkstationOperationService.INSTANCE.tick(employee);
        }

        helper.assertTrue(newOperations(helper, before).size() == 1,
                "Repeated employee observations retain one Execution operation");
        helper.assertTrue(grinder.inventory().input().getCount() == 1,
                "Repeated employee observations do not mutate reserved input early");
        helper.assertTrue(employee.workstationOperationRequestConsumed(),
                "Reservation attempt records one consumed employee request");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "07_reservation_lost")
    public static void lostReservationFailsActiveEmployeeOperation(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Reservation Lost");
        EmployeeEntity employee = assignAndArrive(helper, record);
        startEmployeeOperation(helper, employee);

        WorkstationReservationService.INSTANCE.invalidateByEmployee(
                helper.getLevel().getServer(),
                record.employeeId(),
                "GameTest reservation loss"
        );
        EmployeeWorkstationOperationService.INSTANCE.tick(employee);

        assertFailure(helper, employee, "reservation_lost");
        helper.assertTrue(newOperations(helper, before).size() == 1,
                "Reservation loss does not create a replacement operation");
        helper.assertTrue(grinder.inventory().input().is(ModItems.BEEF_TRIM.get()),
                "Reservation loss before effect preserves Grinder input");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "08_workstation_removed")
    public static void removedWorkstationFailsActiveEmployeeOperation(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Removed");
        EmployeeEntity employee = assignAndArrive(helper, record);
        startEmployeeOperation(helper, employee);

        helper.setBlock(GRINDER_POS, Blocks.AIR);
        EmployeeWorkstationOperationService.INSTANCE.tick(employee);

        assertFailure(helper, employee, "workstation_removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "09_execution_cancelled")
    public static void cancelledExecutionReportsFailureWithoutRetry(GameTestHelper helper) {
        setup(helper);
        Set<ExecutionOperationId> before = operationIds(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Execution Failure");
        EmployeeEntity employee = assignAndArrive(helper, record);
        startEmployeeOperation(helper, employee);
        ExecutionOperationSnapshot execution = onlyNewOperation(helper, before);

        var cancelled = ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer()).cancelBeforeStart(
                execution.operationId(),
                execution.lastUpdatedSimulationTick() + 1L,
                "GameTest cancellation"
        );
        helper.assertTrue(cancelled.accepted(), "Execution cancellation fixture is accepted before dispatch");
        EmployeeWorkstationOperationService.INSTANCE.tick(employee);

        assertFailure(helper, employee, "execution_failed");
        repeatFailureTickDoesNotRetry(helper, employee, before);
        helper.assertTrue(grinder.inventory().input().is(ModItems.BEEF_TRIM.get()),
                "Failed pre-effect Execution preserves Grinder input");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 140, batch = BATCH + "10_output_retained")
    public static void completedOutputRemainsUntilExplicitPlayerStyleExtraction(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Grinder Output Waits");
        EmployeeEntity employee = assignAndArrive(helper, record);
        startEmployeeOperation(helper, employee);

        helper.runAtTickTime(100, () -> {
            helper.assertTrue(grinder.inventory().output().is(ModItems.GROUND_BEEF.get()),
                    "Ground Beef remains in Grinder after employee completion");
            ItemStack extracted = grinder.inventory().extractItem(WorkstationInventory.OUTPUT_SLOT, 1, false);
            helper.assertTrue(extracted.is(ModItems.GROUND_BEEF.get()) && extracted.getCount() == 1,
                    "Explicit workstation output extraction returns Ground Beef");
            helper.assertTrue(grinder.inventory().output().isEmpty(),
                    "Only explicit extraction removes completed output");
            helper.assertTrue(employee.getMainHandItem().isEmpty() && employee.getOffhandItem().isEmpty(),
                    "Employee never acquires the extracted output");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "11_plain_name")
    public static void plainNameSuggestionExecutesUnchanged(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Tom");
        assignAndArrive(helper, record);

        String prefix = "butchercraft employee operate ";
        helper.assertTrue(suggestions(helper, prefix).contains("Tom"),
                "Plain employee display name is suggested as executable text");
        helper.assertTrue(execute(helper, prefix + "Tom") == 1,
                "Plain employee display name executes unchanged");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "12_spaced_name")
    public static void quotedSpacedNameSuggestionExecutesUnchanged(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Casey 1");
        assignAndArrive(helper, record);

        String prefix = "butchercraft employee operate ";
        String quotedReference = "\"Casey 1\"";
        helper.assertTrue(suggestions(helper, prefix).contains(quotedReference),
                "Spaced employee display name is suggested as quoted executable text");
        helper.assertTrue(execute(helper, prefix + quotedReference) == 1,
                "Quoted employee display name executes unchanged");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "13_canonical_id")
    public static void canonicalEmployeeIdentityExecutes(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Canonical Employee");
        assignAndArrive(helper, record);

        helper.assertTrue(suggestions(helper, "butchercraft employee operate ")
                        .contains(record.employeeId().value()),
                "Employee operation suggestions expose the canonical Employee Identity");
        helper.assertTrue(operate(helper, record.employeeId().value()) == 1,
                "Canonical Employee Identity executes through the operator command");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "14_missing_reservation")
    public static void commandRejectsMissingReservation(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Without Reservation");
        Set<ExecutionOperationId> before = operationIds(helper);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Employee operation requires an exclusive reservation");
        helper.assertTrue(newOperations(helper, before).isEmpty(),
                "Missing reservation creates no Execution operation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "15_not_arrived")
    public static void commandRejectsReservationBeforeArrival(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee En Route");
        assign(helper, record);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Employee operation requires employee_arrived reservation state");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "16_not_at_workstation")
    public static void commandRejectsEmployeeOutsideOperatingTolerance(GameTestHelper helper) {
        setup(helper);
        GrinderBlockEntity grinder = placeGrinder(helper);
        insert(helper, grinder, beefTrim());
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Away From Grinder");
        EmployeeEntity employee = assignAndArrive(helper, record);
        BlockPos away = helper.absolutePos(new BlockPos(0, 1, 0));
        employee.moveTo(away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 0.0F, 0.0F);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Employee operation validates physical operating tolerance at request time");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "17_wrong_workstation")
    public static void commandRejectsUnsupportedReservedWorkstation(GameTestHelper helper) {
        setup(helper);
        helper.setBlock(GRINDER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState()
                .setValue(PattyFormerBlock.FACING, Direction.EAST));
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Employee Patty Former");
        EmployeeEntity employee = assign(helper, record);
        arrive(helper, record, employee);

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "IM-027 command rejects a reserved Patty Former");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "18_not_present")
    public static void commandRejectsEmployeeNotPresent(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Absent Employee");
        EmployeeService.INSTANCE.setPresence(
                helper.getLevel().getServer(),
                record.employeeId(),
                EmployeePresenceState.ABSENT
        ).orThrow();

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Employee operation requires present Workforce observation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH + "19_inactive")
    public static void commandRejectsInactiveEmployee(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord record = createPresentProcessingEmployee(helper, "Inactive Employee");
        EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .transitionStatus(record.employeeId(), EmployeeStatus.INACTIVE)
                .orThrow();

        helper.assertTrue(operate(helper, record.employeeId().value()) == 0,
                "Employee operation requires active employment status");
        helper.succeed();
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

    private static EmployeeRecord createPresentProcessingEmployee(GameTestHelper helper, String displayName) {
        EmployeeRecord record = EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(),
                Optional.of(displayName),
                Optional.of(helper.absolutePos(OPERATING_POS)),
                true
        ).orThrow();
        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                DepartmentSchema.PROCESSING.value()
        ).orThrow();
        EmployeeService.INSTANCE.setPresence(
                helper.getLevel().getServer(),
                record.employeeId(),
                EmployeePresenceState.PRESENT
        ).orThrow();
        return EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(record.employeeId())
                .orElseThrow();
    }

    private static GrinderBlockEntity placeGrinder(GameTestHelper helper) {
        helper.setBlock(GRINDER_POS, ModBlocks.GRINDER.get().defaultBlockState()
                .setValue(GrinderBlock.FACING, Direction.EAST));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(GRINDER_POS));
        helper.assertTrue(blockEntity instanceof GrinderBlockEntity, "Expected Grinder block entity");
        return (GrinderBlockEntity) blockEntity;
    }

    private static EmployeeEntity assignAndArrive(GameTestHelper helper, EmployeeRecord record) {
        EmployeeEntity employee = assign(helper, record);
        arrive(helper, record, employee);
        return employee;
    }

    private static EmployeeEntity assign(GameTestHelper helper, EmployeeRecord record) {
        var assigned = WorkstationReservationService.INSTANCE.assign(
                helper.getLevel(),
                record.employeeId(),
                helper.absolutePos(GRINDER_POS)
        );
        helper.assertTrue(assigned.succeeded(), "Employee Grinder reservation succeeds");
        return entity(helper, record);
    }

    private static void arrive(GameTestHelper helper, EmployeeRecord record, EmployeeEntity employee) {
        BlockPos operating = helper.absolutePos(OPERATING_POS);
        employee.moveTo(operating.getX() + 0.5D, operating.getY(), operating.getZ() + 0.5D, 0.0F, 0.0F);
        EmployeeService.INSTANCE.synchronizeEntity(employee);
        helper.assertTrue(activeReservation(helper, record.employeeId()).state()
                        == WorkstationReservationState.EMPLOYEE_ARRIVED,
                "Employee arrives at reserved Grinder");
    }

    private static void startEmployeeOperation(GameTestHelper helper, EmployeeEntity employee) {
        helper.assertTrue(operate(helper, employee.employeeIdValue()) == 1,
                "Operator command accepts employee operation request");
        helper.assertTrue(employee.workstationOperationState() == EmployeeWorkstationOperationState.OPERATING,
                "Employee enters operating state after accepted request: "
                        + employee.workstationOperationDiagnostics());
        EmployeeWorkstationOperationService.INSTANCE.tick(employee);
        helper.assertTrue(employee.workstationOperationState()
                        == EmployeeWorkstationOperationState.WAITING_FOR_COMPLETION,
                "Employee enters waiting-for-completion state");
    }

    private static String employeeNumberReference(EmployeeRecord record) {
        return "#" + Math.addExact(record.sequence(), 1L);
    }

    private static CommandSourceStack commandSource(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
    }

    private static List<String> suggestions(GameTestHelper helper, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack source = commandSource(helper);
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, source))
                .join()
                .getList()
                .stream()
                .map(Suggestion::getText)
                .toList();
    }

    private static int operate(GameTestHelper helper, String employeeReference) {
        return execute(helper, "butchercraft employee operate " + employeeReference);
    }

    private static int execute(GameTestHelper helper, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        try {
            return dispatcher.execute(command, commandSource(helper));
        } catch (CommandSyntaxException exception) {
            helper.assertTrue(false, "Command should execute: " + command + " | " + exception.getMessage());
            return 0;
        }
    }

    private static void insert(GameTestHelper helper, GrinderBlockEntity grinder, ItemStack stack) {
        ItemStack remainder = grinder.inventory().insertItem(WorkstationInventory.INPUT_SLOT, stack, false);
        helper.assertTrue(remainder.isEmpty(), "Test input is accepted by Grinder-owned inventory");
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

    private static void assertFailure(GameTestHelper helper, EmployeeEntity employee, String failure) {
        helper.assertTrue(employee.workstationOperationState() == EmployeeWorkstationOperationState.FAILURE,
                "Employee operation reaches explicit failure: " + employee.workstationOperationDiagnostics());
        helper.assertTrue(employee.workstationOperationDiagnostics().failure().equals(failure),
                "Employee operation reports " + failure + ": " + employee.workstationOperationDiagnostics());
    }

    private static void repeatFailureTickDoesNotRetry(
            GameTestHelper helper,
            EmployeeEntity employee,
            Set<ExecutionOperationId> before
    ) {
        int operationCount = newOperations(helper, before).size();
        for (int index = 0; index < 5; index++) {
            EmployeeWorkstationOperationService.INSTANCE.tick(employee);
        }
        helper.assertTrue(newOperations(helper, before).size() == operationCount,
                "Terminal employee failure does not retry Execution");
    }

    private static WorkstationReservationRecord activeReservation(GameTestHelper helper, EmployeeId employeeId) {
        return WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                .findByEmployee(employeeId.value())
                .orElseThrow();
    }

    private static EmployeeEntity entity(GameTestHelper helper, EmployeeRecord record) {
        ServerLevel level = helper.getLevel();
        Entity entity = level.getEntity(record.entityLink().orElseThrow().entityUuid());
        helper.assertTrue(entity instanceof EmployeeEntity, "Linked entity is an EmployeeEntity");
        return (EmployeeEntity) entity;
    }

    private static Set<ExecutionOperationId> operationIds(GameTestHelper helper) {
        Set<ExecutionOperationId> ids = new HashSet<>();
        ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer()).operations()
                .forEach(operation -> ids.add(operation.operationId()));
        return ids;
    }

    private static List<ExecutionOperationSnapshot> newOperations(
            GameTestHelper helper,
            Set<ExecutionOperationId> before
    ) {
        return ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer()).operations().stream()
                .filter(operation -> !before.contains(operation.operationId()))
                .toList();
    }

    private static ExecutionOperationSnapshot onlyNewOperation(
            GameTestHelper helper,
            Set<ExecutionOperationId> before
    ) {
        List<ExecutionOperationSnapshot> operations = newOperations(helper, before);
        helper.assertTrue(operations.size() == 1,
                "Expected one new Execution operation, found " + operations.size());
        return operations.getFirst();
    }
}
