package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.machine.grinder.GrinderBlock;
import com.butchercraft.machine.grinder.GrinderBlockEntity;
import com.butchercraft.machine.pattyformer.PattyFormerBlock;
import com.butchercraft.machine.pattyformer.PattyFormerBlockEntity;
import com.butchercraft.registration.ModBlocks;
import com.butchercraft.workstation.WorkstationInventory;
import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.reservation.WorkstationReservationDirectory;
import com.butchercraft.workstation.reservation.WorkstationReservationFailureCode;
import com.butchercraft.workstation.reservation.WorkstationReservationManager;
import com.butchercraft.workstation.reservation.WorkstationReservationRecord;
import com.butchercraft.workstation.reservation.WorkstationReservationResult;
import com.butchercraft.workstation.reservation.WorkstationReservationState;
import com.butchercraft.workstation.reservation.persistence.WorkstationReservationStorage;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeeWorkstationReservationGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final String BATCH = "zzzz_employee_workstation_reservation";
    private static final BlockPos EMPLOYEE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos SECOND_EMPLOYEE_POS = new BlockPos(1, 1, 3);
    private static final BlockPos GRINDER_POS = new BlockPos(2, 1, 2);
    private static final BlockPos PATTY_FORMER_POS = new BlockPos(3, 1, 2);
    private static final BlockPos UNSUPPORTED_POS = new BlockPos(2, 1, 3);

    private EmployeeWorkstationReservationGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void grinderReservationTargetsOperatingPositionAndWaitsWithoutOperating(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Grinder", EMPLOYEE_POS);
        GrinderBlockEntity grinder = placeGrinder(helper);
        Counts before = counts(helper);

        WorkstationReservationRecord reservation = assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS))
                .orThrow();
        EmployeeEntity entity = entity(helper, employee);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        BlockPos operating = absolute(helper, GRINDER_POS.relative(Direction.EAST));
        helper.assertTrue(reservation.state() == WorkstationReservationState.EMPLOYEE_EN_ROUTE,
                "Reservation exists while employee is en route");
        helper.assertTrue(entity.anchorPos().equals(operating), "Employee targets Grinder operating position");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_WORKSTATION.serializedName()),
                "Employee begins walking to the Grinder");

        entity.moveTo(operating.getX() + 0.5D, operating.getY(), operating.getZ() + 0.5D, 0.0F, 0.0F);
        EmployeeService.INSTANCE.synchronizeEntity(entity);
        WorkstationReservationRecord arrived = WorkstationReservationService.INSTANCE
                .managerFor(helper.getLevel().getServer())
                .findByEmployee(employee.employeeId().value())
                .orElseThrow();

        helper.assertTrue(arrived.state() == WorkstationReservationState.EMPLOYEE_ARRIVED,
                "Reservation records physical arrival");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WAITING_AT_WORKSTATION.serializedName()),
                "Employee waits at the Grinder");
        helper.assertTrue(Math.abs(entity.getYRot() - 90.0F) < 0.01F,
                "Employee faces the Grinder while waiting");
        helper.assertTrue(entity.getMainHandItem().isEmpty() && entity.getOffhandItem().isEmpty(),
                "Waiting employee does not hold products");
        assertWorkstationUnchanged(helper, grinder, before);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void pattyFormerReservationSucceeds(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Patty Former", EMPLOYEE_POS);
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        Counts before = counts(helper);

        WorkstationReservationRecord reservation = assign(helper, employee.employeeId(), absolute(helper, PATTY_FORMER_POS))
                .orThrow();
        EmployeeService.INSTANCE.synchronizeEntity(entity(helper, employee));

        BlockPos operating = absolute(helper, PATTY_FORMER_POS.relative(Direction.WEST));
        helper.assertTrue(reservation.workstationType().equals("patty_former"),
                "Reservation identifies Patty Former type");
        helper.assertTrue(entity(helper, employee).anchorPos().equals(operating),
                "Employee targets Patty Former operating position");
        assertWorkstationUnchanged(helper, pattyFormer, before);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void workstationReservationConflictsAndReleaseAreDeterministic(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord first = createPresentProcessingEmployee(helper, "Reservation First", EMPLOYEE_POS);
        EmployeeRecord second = createPresentProcessingEmployee(helper, "Reservation Second", SECOND_EMPLOYEE_POS);
        placeGrinder(helper);

        WorkstationReservationRecord firstReservation = assign(helper, first.employeeId(), absolute(helper, GRINDER_POS))
                .orThrow();
        WorkstationReservationResult<WorkstationReservationRecord> conflict =
                assign(helper, second.employeeId(), absolute(helper, GRINDER_POS));
        WorkstationReservationRecord released = WorkstationReservationService.INSTANCE.release(
                helper.getLevel().getServer(),
                first.employeeId(),
                "gametest release"
        ).orThrow();
        WorkstationReservationRecord secondReservation = assign(helper, second.employeeId(), absolute(helper, GRINDER_POS))
                .orThrow();

        helper.assertTrue(conflict.failure().orElseThrow().code()
                        == WorkstationReservationFailureCode.WORKSTATION_ALREADY_RESERVED,
                "Second employee cannot reserve occupied workstation");
        helper.assertTrue(released.state() == WorkstationReservationState.RELEASED,
                "Release is explicit");
        helper.assertTrue(firstReservation.workstationIdentity().equals(secondReservation.workstationIdentity()),
                "Release allows the same workstation to be reserved again");
        helper.assertTrue(secondReservation.employeeIdentity().equals(second.employeeId().value()),
                "Second reservation belongs to the second employee");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void employeeCannotHoldTwoWorkstationReservations(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation One Employee", EMPLOYEE_POS);
        placeGrinder(helper);
        placePattyFormer(helper);

        assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS)).orThrow();
        WorkstationReservationResult<WorkstationReservationRecord> second =
                assign(helper, employee.employeeId(), absolute(helper, PATTY_FORMER_POS));

        helper.assertTrue(second.failure().orElseThrow().code()
                        == WorkstationReservationFailureCode.EMPLOYEE_ALREADY_RESERVED,
                "Employee cannot hold two workstation reservations");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void unsupportedBlockAndAbsentEmployeeAreRejected(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Reject", EMPLOYEE_POS);
        helper.setBlock(UNSUPPORTED_POS, Blocks.STONE);
        placeGrinder(helper);

        WorkstationReservationResult<WorkstationReservationRecord> unsupported =
                assign(helper, employee.employeeId(), absolute(helper, UNSUPPORTED_POS));
        EmployeeService.INSTANCE.setPresence(
                helper.getLevel().getServer(),
                employee.employeeId(),
                EmployeePresenceState.ABSENT
        ).orThrow();
        WorkstationReservationResult<WorkstationReservationRecord> absent =
                assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS));

        helper.assertTrue(unsupported.failure().orElseThrow().code()
                        == WorkstationReservationFailureCode.UNSUPPORTED_WORKSTATION,
                "Unsupported blocks cannot be reserved");
        helper.assertTrue(absent.failure().orElseThrow().code()
                        == WorkstationReservationFailureCode.EMPLOYEE_NOT_PRESENT,
                "Absent employees cannot begin workstation trips");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void workstationRemovalInvalidatesReservation(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Removed Workstation", EMPLOYEE_POS);
        placeGrinder(helper);

        WorkstationReservationRecord reservation = assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS))
                .orThrow();
        helper.setBlock(GRINDER_POS, Blocks.AIR);

        helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .findByWorkstation(reservation.workstationIdentity())
                        .isEmpty(),
                "Workstation removal invalidates active reservation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void employeeRemovalInvalidatesReservation(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Removed Employee", EMPLOYEE_POS);
        placeGrinder(helper);

        assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS)).orThrow();
        entity(helper, employee).discard();

        helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .findByEmployee(employee.employeeId().value())
                        .isEmpty(),
                "Employee removal invalidates active reservation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void displacedArrivedEmployeeReturnsToOperatingPosition(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Displaced", EMPLOYEE_POS);
        placeGrinder(helper);
        EmployeeEntity entity = entity(helper, employee);
        BlockPos operating = absolute(helper, GRINDER_POS.relative(Direction.EAST));

        assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS)).orThrow();
        entity.moveTo(operating.getX() + 0.5D, operating.getY(), operating.getZ() + 0.5D, 0.0F, 0.0F);
        EmployeeService.INSTANCE.synchronizeEntity(entity);
        entity.moveTo(absolute(helper, new BlockPos(0, 1, 0)).getX() + 0.5D,
                absolute(helper, new BlockPos(0, 1, 0)).getY(),
                absolute(helper, new BlockPos(0, 1, 0)).getZ() + 0.5D,
                0.0F,
                0.0F);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.assertTrue(entity.anchorPos().equals(operating), "Displaced employee keeps operating target");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_WORKSTATION.serializedName()),
                "Displaced employee returns to the workstation");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void validReservationPersistsAndDuplicateReloadKeepsOneClaim(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Persist", EMPLOYEE_POS);
        placeGrinder(helper);

        WorkstationReservationRecord reservation = assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS))
                .orThrow();
        WorkstationReservationStorage storage = new WorkstationReservationStorage(
                WorkstationReservationService.reservationFile(helper.getLevel().getServer())
        );
        storage.save(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer()).directory());
        WorkstationReservationDirectory loaded = storage.load();
        WorkstationReservationManager duplicateLoad = new WorkstationReservationManager(
                WorkstationReservationDirectory.of(List.of(
                        reservation,
                        reservation.withState(WorkstationReservationState.RESERVED)
                ))
        );

        helper.assertTrue(loaded.records().contains(reservation), "Valid reservation survives persistence reload");
        helper.assertTrue(duplicateLoad.activeReservations().size() == 1,
                "Duplicate reload reconciliation keeps one active claim");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void assignWorkstationCommandUsesSynchronizedBuiltInArguments(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Tom", EMPLOYEE_POS);
        placeGrinder(helper);
        CommandSourceStack source = commandSource(helper);
        BlockPos absoluteGrinder = absolute(helper, GRINDER_POS);

        int result = execute(helper, source, "butchercraft employee assign-workstation Tom "
                + absoluteGrinder.getX() + " " + absoluteGrinder.getY() + " " + absoluteGrinder.getZ());

        helper.assertTrue(result == 1, "Assign workstation command executes with built-in greedy string argument");
        helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                        .findByEmployee(employee.employeeId().value()).isPresent(),
                "Command creates the authoritative reservation");
        helper.succeed();
    }

    private static void setup(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static EmployeeRecord createPresentProcessingEmployee(
            GameTestHelper helper,
            String displayName,
            BlockPos relativePos
    ) {
        EmployeeRecord record = EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(),
                Optional.of(displayName),
                Optional.of(absolute(helper, relativePos)),
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
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolute(helper, GRINDER_POS));
        helper.assertTrue(blockEntity instanceof GrinderBlockEntity, "Expected Grinder block entity");
        return (GrinderBlockEntity) blockEntity;
    }

    private static PattyFormerBlockEntity placePattyFormer(GameTestHelper helper) {
        helper.setBlock(PATTY_FORMER_POS, ModBlocks.PATTY_FORMER.get().defaultBlockState()
                .setValue(PattyFormerBlock.FACING, Direction.WEST));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolute(helper, PATTY_FORMER_POS));
        helper.assertTrue(blockEntity instanceof PattyFormerBlockEntity, "Expected Patty Former block entity");
        return (PattyFormerBlockEntity) blockEntity;
    }

    private static WorkstationReservationResult<WorkstationReservationRecord> assign(
            GameTestHelper helper,
            EmployeeId employeeId,
            BlockPos absoluteWorkstationPos
    ) {
        return WorkstationReservationService.INSTANCE.assign(helper.getLevel(), employeeId, absoluteWorkstationPos);
    }

    private static EmployeeEntity entity(GameTestHelper helper, EmployeeRecord record) {
        ServerLevel level = helper.getLevel();
        Entity entity = level.getEntity(record.entityLink().orElseThrow().entityUuid());
        helper.assertTrue(entity instanceof EmployeeEntity, "Linked entity is an EmployeeEntity");
        return (EmployeeEntity) entity;
    }

    private static Counts counts(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        return new Counts(
                ProductionService.INSTANCE.managerFor(server).runs().size(),
                SimulationSchedulerService.INSTANCE.managerFor(server).registry().size(),
                ExecutionService.INSTANCE.managerFor(server).operations().size()
        );
    }

    private static void assertWorkstationUnchanged(
            GameTestHelper helper,
            com.butchercraft.workstation.block.AbstractProcessingWorkstationBlockEntity blockEntity,
            Counts before
    ) {
        Counts after = counts(helper);
        helper.assertTrue(blockEntity.workstationState() == WorkstationState.IDLE,
                "Reservation does not start workstation processing");
        helper.assertTrue(blockEntity.inventory().getStackInSlot(WorkstationInventory.INPUT_SLOT).isEmpty(),
                "Reservation does not insert workstation input");
        helper.assertTrue(blockEntity.inventory().getStackInSlot(WorkstationInventory.OUTPUT_SLOT).isEmpty(),
                "Reservation does not create workstation output");
        helper.assertTrue(after.equals(before), "Reservation does not mutate Production, Scheduler, or Execution");
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

    private static BlockPos absolute(GameTestHelper helper, BlockPos relativePos) {
        return helper.absolutePos(relativePos);
    }

    private record Counts(int productionRuns, int schedulerWork, int executionOperations) {
    }
}
