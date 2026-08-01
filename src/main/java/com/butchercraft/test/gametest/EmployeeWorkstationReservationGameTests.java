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
import com.butchercraft.world.InventoryService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeSchema;
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
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeeWorkstationReservationGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final String BATCH = "zzzz_employee_workstation_reservation";
    private static final String GRINDER_NAVIGATION_BATCH = "zzzzz_employee_workstation_grinder_navigation";
    private static final String PATTY_FORMER_NAVIGATION_BATCH = "zzzzzz_employee_workstation_patty_navigation";
    private static final String WORKSTATION_RANGE_BATCH = "zzzzzz_employee_workstation_range";
    private static final String WORKSTATION_RANGE_FAILURE_BATCH = "zzzzzz_employee_workstation_range_failure";
    private static final BlockPos EMPLOYEE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos SECOND_EMPLOYEE_POS = new BlockPos(1, 1, 3);
    private static final BlockPos FAR_GRINDER_EMPLOYEE_POS = new BlockPos(4, 1, 0);
    private static final BlockPos FAR_PATTY_FORMER_EMPLOYEE_POS = new BlockPos(0, 1, 0);
    private static final BlockPos GRINDER_POS = new BlockPos(2, 1, 2);
    private static final BlockPos PATTY_FORMER_POS = new BlockPos(3, 1, 2);
    private static final BlockPos UNSUPPORTED_POS = new BlockPos(2, 1, 3);
    private static final int MAX_NAVIGATION_RANGE = EmployeeSchema.SCHEMA_1_MAX_NAVIGATION_RANGE_BLOCKS;
    private static final double OPERATING_HORIZONTAL_TOLERANCE = 1.1D;
    private static final double OPERATING_VERTICAL_TOLERANCE = 1.25D;

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

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = GRINDER_NAVIGATION_BATCH)
    public static void unobstructedEmployeeToGrinderTravelStartsAndArrives(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(
                helper,
                "Reservation Grinder Open Field",
                FAR_GRINDER_EMPLOYEE_POS
        );
        GrinderBlockEntity grinder = placeGrinder(helper);
        Counts before = counts(helper);
        boolean[] sawStartedPath = {false};
        boolean[] sawNodeProgress = {false};

        assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS)).orThrow();
        EmployeeEntity entity = entity(helper, employee);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.succeedWhen(() -> {
            captureNavigationProgress(entity, sawStartedPath, sawNodeProgress);
            EmployeeService.INSTANCE.synchronizeEntity(entity);
            Optional<WorkstationReservationRecord> reservation = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findByEmployee(employee.employeeId().value());
            helper.assertTrue(sawStartedPath[0],
                    "Accepted Grinder path was observed before arrival: " + entity.navigationDiagnostics());
            helper.assertTrue(reservation.isPresent()
                            && reservation.orElseThrow().state() == WorkstationReservationState.EMPLOYEE_ARRIVED,
                    "Open-field Grinder travel reaches the reservation target: " + entity.navigationDiagnostics()
                            + " | reservation=" + reservation);
            assertWorkstationUnchanged(helper, grinder, before);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = PATTY_FORMER_NAVIGATION_BATCH)
    public static void unobstructedEmployeeToPattyFormerTravelStartsAndArrives(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(
                helper,
                "Reservation Patty Former Open Field",
                FAR_PATTY_FORMER_EMPLOYEE_POS
        );
        PattyFormerBlockEntity pattyFormer = placePattyFormer(helper);
        Counts before = counts(helper);
        boolean[] sawStartedPath = {false};
        boolean[] sawNodeProgress = {false};

        assign(helper, employee.employeeId(), absolute(helper, PATTY_FORMER_POS)).orThrow();
        EmployeeEntity entity = entity(helper, employee);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.succeedWhen(() -> {
            captureNavigationProgress(entity, sawStartedPath, sawNodeProgress);
            EmployeeService.INSTANCE.synchronizeEntity(entity);
            Optional<WorkstationReservationRecord> reservation = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findByEmployee(employee.employeeId().value());
            helper.assertTrue(sawStartedPath[0],
                    "Accepted Patty Former path was observed before arrival: " + entity.navigationDiagnostics());
            helper.assertTrue(reservation.isPresent()
                            && reservation.orElseThrow().state() == WorkstationReservationState.EMPLOYEE_ARRIVED,
                    "Open-field Patty Former travel reaches the reservation target: " + entity.navigationDiagnostics()
                            + " | reservation=" + reservation);
            assertWorkstationUnchanged(helper, pattyFormer, before);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 820, batch = WORKSTATION_RANGE_BATCH)
    public static void openFieldGrinderTravelSupportsSchemaOneRange(GameTestHelper helper) {
        int startX = 2;
        setupOpenField(helper, startX + MAX_NAVIGATION_RANGE + 3, 16);
        int[] distances = {5, 16, 32, MAX_NAVIGATION_RANGE};
        EmployeeRecord[] employees = new EmployeeRecord[distances.length];
        GrinderBlockEntity[] grinders = new GrinderBlockEntity[distances.length];
        Counts before = counts(helper);

        for (int index = 0; index < distances.length; index++) {
            int laneZ = 2 + index * 3;
            BlockPos employeePos = new BlockPos(startX, 1, laneZ);
            BlockPos grinderPos = new BlockPos(startX + distances[index] - 1, 1, laneZ);
            employees[index] = createPresentProcessingEmployee(
                    helper,
                    "Range Grinder " + distances[index],
                    employeePos
            );
            grinders[index] = placeGrinder(helper, grinderPos, Direction.EAST);
            assign(helper, employees[index].employeeId(), absolute(helper, grinderPos)).orThrow();
            EmployeeEntity entity = entity(helper, employees[index]);
            entity.setOnGround(true);
            EmployeeService.INSTANCE.synchronizeEntity(entity);
            WorkstationReservationService.ResolvedWorkstationStatus status = WorkstationReservationService.INSTANCE
                    .status(helper.getLevel(), absolute(helper, grinderPos))
                    .orThrow();
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();

            helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                            .findByEmployee(employees[index].employeeId().value())
                            .filter(record -> record.state() == WorkstationReservationState.EMPLOYEE_EN_ROUTE)
                            .isPresent(),
                    "Grinder reservation remains active while long-distance travel begins");
            helper.assertTrue(diagnostics.destinationWithinRange(),
                    "Grinder destination at " + distances[index] + " blocks passes range validation: "
                            + diagnostics);
            helper.assertTrue(hasUsablePathToAnyCandidate(entity, status.target().approachCandidates()),
                    "Minecraft path search reaches a Grinder operating candidate at "
                            + distances[index] + " blocks");
            helper.assertTrue(diagnostics.pathReplacementCount() == 0,
                    "Range validation does not recreate paths before navigation starts: " + diagnostics);
            assertWorkstationUnchanged(helper, grinders[index], before);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 820, batch = WORKSTATION_RANGE_BATCH)
    public static void openFieldPattyFormerTravelSupportsSchemaOneRange(GameTestHelper helper) {
        int startX = 2;
        setupOpenField(helper, startX + MAX_NAVIGATION_RANGE + 3, 16);
        int[] distances = {5, 16, 32, MAX_NAVIGATION_RANGE};
        EmployeeRecord[] employees = new EmployeeRecord[distances.length];
        PattyFormerBlockEntity[] pattyFormers = new PattyFormerBlockEntity[distances.length];
        Counts before = counts(helper);

        for (int index = 0; index < distances.length; index++) {
            int laneZ = 2 + index * 3;
            BlockPos employeePos = new BlockPos(startX, 1, laneZ);
            BlockPos pattyFormerPos = new BlockPos(startX + distances[index] + 1, 1, laneZ);
            employees[index] = createPresentProcessingEmployee(
                    helper,
                    "Range Patty Former " + distances[index],
                    employeePos
            );
            pattyFormers[index] = placePattyFormer(helper, pattyFormerPos, Direction.WEST);
            assign(helper, employees[index].employeeId(), absolute(helper, pattyFormerPos)).orThrow();
            EmployeeEntity entity = entity(helper, employees[index]);
            entity.setOnGround(true);
            WorkstationReservationService.ResolvedWorkstationStatus status = WorkstationReservationService.INSTANCE
                    .status(helper.getLevel(), absolute(helper, pattyFormerPos))
                    .orThrow();
            BlockPos operatingCandidate = status.target().approachCandidates().getFirst();
            Path path = entity.getNavigation().createPath(operatingCandidate, 1);

            helper.assertTrue(WorkstationReservationService.INSTANCE.managerFor(helper.getLevel().getServer())
                            .findByEmployee(employees[index].employeeId().value())
                            .filter(record -> record.state() == WorkstationReservationState.EMPLOYEE_EN_ROUTE)
                            .isPresent(),
                    "Patty Former reservation remains active while long-distance travel begins");
            helper.assertTrue(path != null && path.getNodeCount() > 0,
                    "Minecraft path search reaches the Patty Former operating candidate at "
                            + distances[index] + " blocks");
            helper.assertTrue(path.getEndNode() != null
                            && path.getEndNode().asBlockPos().distManhattan(operatingCandidate) <= 1,
                    "Patty Former path endpoint remains within operating tolerance at "
                            + distances[index] + " blocks");
            assertWorkstationUnchanged(helper, pattyFormers[index], before);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 520, batch = WORKSTATION_RANGE_FAILURE_BATCH)
    public static void workstationDestinationBeyondSchemaOneRangeInvalidatesReservationExplicitly(GameTestHelper helper) {
        int startX = 2;
        BlockPos grinderPos = new BlockPos(startX + MAX_NAVIGATION_RANGE + 1, 1, 1);
        setupOpenField(helper, startX + MAX_NAVIGATION_RANGE + 5, 3);
        EmployeeRecord employee = createPresentProcessingEmployee(
                helper,
                "Range Workstation Failure",
                new BlockPos(startX, 1, 1)
        );
        GrinderBlockEntity grinder = placeGrinder(
                helper,
                grinderPos,
                Direction.EAST
        );
        Counts before = counts(helper);

        assign(helper, employee.employeeId(), absolute(helper, grinderPos)).orThrow();
        EmployeeEntity entity = entity(helper, employee);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.succeedWhen(() -> {
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            Optional<WorkstationReservationRecord> reservation = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findByEmployee(employee.employeeId().value());
            helper.assertTrue(!diagnostics.destinationWithinRange(),
                    "Workstation destination just beyond the range fails validation: " + diagnostics);
            helper.assertTrue(diagnostics.lastFailureReason().equals("destination_out_of_range")
                            && diagnostics.recoveryPhase().equals("safe_failure"),
                    "Out-of-range workstation travel fails with a typed reason: " + diagnostics);
            helper.assertTrue(diagnostics.pathReplacementCount() == 0,
                    "Out-of-range workstation travel is rejected before path publication: " + diagnostics);
            helper.assertTrue(reservation.isEmpty(),
                    "Terminal out-of-range workstation failure invalidates the reservation: " + reservation);
            assertWorkstationUnchanged(helper, grinder, before);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100, batch = BATCH)
    public static void grinderApproachCandidatesAreDeterministicAndOutsideWorkstation(GameTestHelper helper) {
        setup(helper);
        placeGrinder(helper);
        BlockPos absoluteGrinder = absolute(helper, GRINDER_POS);

        WorkstationReservationService.ResolvedWorkstationStatus status = WorkstationReservationService.INSTANCE
                .status(helper.getLevel(), absoluteGrinder)
                .orThrow();
        List<BlockPos> candidates = status.target().approachCandidates();
        WorkstationReservationService.ResolvedWorkstationStatus repeated = WorkstationReservationService.INSTANCE
                .status(helper.getLevel(), absoluteGrinder)
                .orThrow();

        helper.assertTrue(candidates.size() == 6, "Grinder exposes six ranked approach candidates");
        helper.assertTrue(candidates.getFirst().equals(absolute(helper, GRINDER_POS.relative(Direction.EAST))),
                "Primary Grinder candidate is the facing operating position");
        helper.assertTrue(candidates.stream().noneMatch(absoluteGrinder::equals),
                "Approach candidates never stand inside the workstation block");
        helper.assertTrue(candidates.equals(repeated.target().approachCandidates()),
                "Approach candidate ordering is deterministic");
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

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = BATCH)
    public static void blockedPrimaryApproachAdvancesToAlternateCandidate(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(helper, "Reservation Alternate", EMPLOYEE_POS);
        GrinderBlockEntity grinder = placeGrinder(helper);
        Counts before = counts(helper);
        BlockPos absolutePrimary = absolute(helper, GRINDER_POS.relative(Direction.EAST));
        helper.setBlock(GRINDER_POS.relative(Direction.EAST), Blocks.STONE);

        assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS)).orThrow();
        EmployeeEntity entity = entity(helper, employee);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.succeedWhen(() -> {
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            Optional<WorkstationReservationRecord> activeReservation = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findByEmployee(employee.employeeId().value());
            helper.assertTrue(activeReservation.isPresent(),
                    "Blocked primary preserves an active reservation while trying alternates: " + diagnostics);
            helper.assertTrue(diagnostics.candidateCount() > 1,
                    "Alternate approach candidates are available: " + diagnostics);
            helper.assertTrue(diagnostics.candidateIndex() > 0,
                    "Blocked primary approach advances to an alternate: " + diagnostics);
            helper.assertTrue(!absolutePrimary.equals(diagnostics.currentDestination()),
                    "Navigation no longer targets the blocked primary approach");
            assertWorkstationUnchanged(helper, grinder, before);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 360, batch = BATCH)
    public static void allBlockedApproachesInvalidateReservationSafely(GameTestHelper helper) {
        setup(helper);
        EmployeeRecord employee = createPresentProcessingEmployee(
                helper,
                "Reservation Unreachable",
                new BlockPos(0, 1, 0)
        );
        GrinderBlockEntity grinder = placeGrinder(helper);
        Counts before = counts(helper);
        WorkstationReservationService.ResolvedWorkstationStatus status = WorkstationReservationService.INSTANCE
                .status(helper.getLevel(), absolute(helper, GRINDER_POS))
                .orThrow();
        for (BlockPos candidate : status.target().approachCandidates()) {
            helper.setBlock(relative(helper, candidate), Blocks.STONE);
        }

        assign(helper, employee.employeeId(), absolute(helper, GRINDER_POS)).orThrow();
        EmployeeEntity entity = entity(helper, employee);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.succeedWhen(() -> {
            Optional<WorkstationReservationRecord> activeReservation = WorkstationReservationService.INSTANCE
                    .managerFor(helper.getLevel().getServer())
                    .findByEmployee(employee.employeeId().value());
            helper.assertTrue(activeReservation.isEmpty(),
                    "All blocked approaches invalidate the unreachable reservation instead of leaving it active: "
                            + activeReservation);
            helper.assertTrue(entity.navigationDiagnostics().lastFailureReason().equals("all_candidates_exhausted")
                            || entity.navigationDiagnostics().lastFailureReason().equals("department_unreachable"),
                    "Navigation failure remains diagnostically visible: " + entity.navigationDiagnostics());
            helper.assertTrue(!entity.blockPosition().equals(absolute(helper, GRINDER_POS)),
                    "Employee is never placed inside the workstation block");
            assertWorkstationUnchanged(helper, grinder, before);
        });
    }

    private static void setup(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        setupOpenField(helper, 4, 4);
    }

    private static void setupOpenField(GameTestHelper helper, int maxXInclusive, int maxZInclusive) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        for (int x = 0; x <= maxXInclusive; x++) {
            for (int z = 0; z <= maxZInclusive; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
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
        return placeGrinder(helper, GRINDER_POS, Direction.EAST);
    }

    private static GrinderBlockEntity placeGrinder(GameTestHelper helper, BlockPos relativePos, Direction facing) {
        helper.setBlock(relativePos, ModBlocks.GRINDER.get().defaultBlockState()
                .setValue(GrinderBlock.FACING, facing));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolute(helper, relativePos));
        helper.assertTrue(blockEntity instanceof GrinderBlockEntity, "Expected Grinder block entity");
        return (GrinderBlockEntity) blockEntity;
    }

    private static PattyFormerBlockEntity placePattyFormer(GameTestHelper helper) {
        return placePattyFormer(helper, PATTY_FORMER_POS, Direction.WEST);
    }

    private static PattyFormerBlockEntity placePattyFormer(GameTestHelper helper, BlockPos relativePos, Direction facing) {
        helper.setBlock(relativePos, ModBlocks.PATTY_FORMER.get().defaultBlockState()
                .setValue(PattyFormerBlock.FACING, facing));
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(absolute(helper, relativePos));
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
                ExecutionService.INSTANCE.managerFor(server).operations().size(),
                InventoryService.INSTANCE.managerFor(server).registry().size()
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

    private static void captureNavigationProgress(
            EmployeeEntity entity,
            boolean[] sawStartedPath,
            boolean[] sawNodeProgress
    ) {
        EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
        if (diagnostics.pathAvailable() || diagnostics.pathReplacementCount() > 0) {
            sawStartedPath[0] = true;
            if (diagnostics.activePathNodeIndex() > 0) {
                sawNodeProgress[0] = true;
            }
        }
        if (diagnostics.pathReplacementCount() > 0
                && diagnostics.recoveryPhase().equals("arrived")) {
            sawNodeProgress[0] = true;
        }
    }

    private static boolean hasUsablePathToAnyCandidate(EmployeeEntity entity, List<BlockPos> candidates) {
        for (BlockPos candidate : candidates) {
            Path path = entity.getNavigation().createPath(candidate, 1);
            if (path != null && pathEndpointWithinOperatingTolerance(path, candidate)) {
                return true;
            }
        }
        return false;
    }

    private static boolean pathEndpointWithinOperatingTolerance(Path path, BlockPos candidate) {
        if (path.getNodeCount() == 0 || path.getEndNode() == null) {
            return false;
        }
        BlockPos endpoint = path.getEndNode().asBlockPos();
        double dx = endpoint.getX() + 0.5D - (candidate.getX() + 0.5D);
        double dz = endpoint.getZ() + 0.5D - (candidate.getZ() + 0.5D);
        return dx * dx + dz * dz <= OPERATING_HORIZONTAL_TOLERANCE * OPERATING_HORIZONTAL_TOLERANCE
                && Math.abs(endpoint.getY() - candidate.getY()) <= OPERATING_VERTICAL_TOLERANCE;
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

    private static BlockPos relative(GameTestHelper helper, BlockPos absolutePos) {
        return absolutePos.subtract(helper.absolutePos(BlockPos.ZERO));
    }

    private record Counts(int productionRuns, int schedulerWork, int executionOperations, int inventoryEntries) {
    }
}
