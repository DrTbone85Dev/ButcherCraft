package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.registration.ModEntityTypes;
import com.butchercraft.world.BusinessRuntimeCalendarService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.ExecutionService;
import com.butchercraft.world.InventoryService;
import com.butchercraft.world.ProductionService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorkstationReservationService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.workforce.department.DepartmentAnchor;
import com.butchercraft.world.workforce.department.DepartmentId;
import com.butchercraft.world.workforce.department.DepartmentRecord;
import com.butchercraft.world.workforce.department.DepartmentSchema;
import com.butchercraft.world.workforce.department.DepartmentStorage;
import com.butchercraft.world.workforce.employee.EmployeeAnchor;
import com.butchercraft.world.workforce.employee.EmployeeFailureCode;
import com.butchercraft.world.workforce.employee.EmployeeEntityLink;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeManager;
import com.butchercraft.world.workforce.employee.EmployeeNavigationState;
import com.butchercraft.world.workforce.employee.EmployeeOperationResult;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeSchema;
import com.butchercraft.world.workforce.employee.EmployeeShiftAssignment;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeeFoundationGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final String DEPARTMENT_BATCH = "zz_employee_department";
    private static final String COMMAND_ACCEPTANCE_BATCH = "zzz_employee_command_acceptance";
    private static final String DEPARTMENT_ANCHOR_COMMAND_BATCH = "zzzz_employee_department_anchor_command";
    private static final String NAVIGATION_DIRECT_DEPARTMENT_BATCH = "zzzz_employee_navigation_direct_department";
    private static final String NAVIGATION_RANGE_DEPARTMENT_BATCH = "zzzz_employee_navigation_range_department";
    private static final String NAVIGATION_RANGE_OBSTACLE_BATCH = "zzzz_employee_navigation_range_obstacle";
    private static final String NAVIGATION_RANGE_FAILURE_BATCH = "zzzz_employee_navigation_range_failure";
    private static final String NAVIGATION_BLOCKED_ANCHOR_BATCH = "zzzz_employee_navigation_blocked_anchor";
    private static final String NAVIGATION_REAR_ROUTE_BATCH = "zzzz_employee_navigation_rear_route";
    private static final String NAVIGATION_SEALED_ROUTE_BATCH = "zzzzz_employee_navigation_sealed_route";
    private static final BlockPos EMPLOYEE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos DEPARTMENT_POS = new BlockPos(4, 1, 2);
    private static final BlockPos DIRECT_DEPARTMENT_EMPLOYEE_POS = new BlockPos(0, 1, 2);
    private static final BlockPos BLOCKED_ANCHOR_POS = new BlockPos(3, 1, 2);
    private static final BlockPos REAR_ROUTE_EMPLOYEE_POS = new BlockPos(2, 1, 0);
    private static final BlockPos REAR_ROUTE_DEPARTMENT_POS = new BlockPos(2, 1, 2);
    private static final int MAX_NAVIGATION_RANGE = EmployeeSchema.SCHEMA_1_MAX_NAVIGATION_RANGE_BLOCKS;
    private static boolean resetCompleted;

    private EmployeeFoundationGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void employeeEntityTypeRegistered(GameTestHelper helper) {
        helper.assertTrue(ModEntityTypes.EMPLOYEE.isBound(), "Employee entity type is registered");
        helper.assertTrue(ModEntityTypes.EMPLOYEE.getId().toString().equals("butchercraft:employee"),
                "Employee entity keeps the canonical registry id");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void employeeEntityTypeCreatesServerEntity(GameTestHelper helper) {
        EmployeeEntity entity = ModEntityTypes.EMPLOYEE.get().create(helper.getLevel());
        Entity created = entity;

        helper.assertTrue(entity != null, "Employee entity type creates an entity");
        helper.assertTrue(!(created instanceof Villager), "Employee foundation does not use villager entities");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void serviceInitializesEmployeeManager(GameTestHelper helper) {
        EmployeeManager manager = EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer());

        helper.assertTrue(manager.directory().registry().size() >= 0, "Employee manager initializes");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = DEPARTMENT_BATCH)
    public static void departmentDirectoryInitializesCanonicalDepartments(GameTestHelper helper) {
        var registry = EmployeeService.INSTANCE.departmentManagerFor(helper.getLevel().getServer()).registry();

        helper.assertTrue(registry.find(DepartmentSchema.PROCESSING).isPresent(), "Processing department is registered");
        helper.assertTrue(registry.find(DepartmentSchema.PACKAGING).isPresent(), "Packaging department is registered");
        helper.assertTrue(registry.find(DepartmentSchema.SHIPPING).isPresent(), "Shipping department is registered");
        helper.assertTrue(registry.find(DepartmentSchema.OFFICE).isPresent(), "Office department is registered");
        helper.assertTrue(registry.find(DepartmentSchema.MAINTENANCE).isPresent(), "Maintenance department is registered");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void createEmployeeWithoutEntityCreatesPersistentRecord(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation No Entity", false);

        helper.assertTrue(EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(record.employeeId()).isPresent(), "Created employee record is authoritative");
        helper.assertTrue(record.entityLink().isEmpty(), "Non-spawn creation does not bind an entity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void createEmployeeWithDisplayNamePreservesName(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Named", false);

        helper.assertTrue(record.displayName().startsWith("Foundation Named"),
                "Requested display name is stored");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void createEmployeeAssignsCurrentDefaultShift(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Shift", false);

        helper.assertTrue(record.assignedShift().isPresent(), "Employee receives a default configured shift");
        helper.assertTrue(record.assignedShift().orElseThrow().shiftIdentity().startsWith(
                "butchercraft:business_shift/v1/"), "Shift assignment stores canonical shift identity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void createEmployeeWithEntitySpawnsBoundEntity(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Entity", true);

        helper.assertTrue(record.entityLink().isPresent(), "Spawned employee record links an entity");
        helper.assertTrue(entity(helper, record) != null, "Linked entity exists in the server world");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void spawnedEntityCarriesEmployeeIdentity(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Identity", true);
        EmployeeEntity entity = entity(helper, record);

        helper.assertTrue(entity.employeeIdValue().equals(record.employeeId().value()),
                "Entity carries only the Employee Identity link");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void spawnedEntityCustomNameMatchesRecord(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Name", true);
        EmployeeEntity entity = entity(helper, record);

        helper.assertTrue(entity.displayNameValue().equals(record.displayName()),
                "Entity display name follows employee record");
        helper.assertTrue(entity.getCustomName() != null
                && entity.getCustomName().getString().equals(record.displayName()),
                "Entity custom name is visible and record-backed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void spawnedEntityAnchorMatchesSpawnPosition(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Anchor", true);
        EmployeeEntity entity = entity(helper, record);
        BlockPos expected = helper.absolutePos(EMPLOYEE_POS);

        helper.assertTrue(entity.anchorPos().equals(expected), "Entity anchor is persisted from spawn position");
        helper.assertTrue(entity.anchorRadius() == EmployeeSchema.DEFAULT_IDLE_ANCHOR_RADIUS,
                "Entity uses the default idle anchor radius");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void spawnedEmployeeIsMarkedPresentExplicitly(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Present", true);

        EmployeeRecord updated = EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(record.employeeId())
                .orElseThrow();

        helper.assertTrue(updated.presenceState() == EmployeePresenceState.PRESENT,
                "Spawned employee is explicitly present");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void entitySaveDataContainsEmployeeIdentityAndAnchor(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Save", true);
        EmployeeEntity entity = entity(helper, record);
        CompoundTag tag = new CompoundTag();

        entity.addAdditionalSaveData(tag);

        helper.assertTrue(tag.getString("EmployeeId").equals(record.employeeId().value()),
                "Entity NBT stores Employee Identity");
        helper.assertTrue(tag.getInt("AnchorRadius") == EmployeeSchema.DEFAULT_IDLE_ANCHOR_RADIUS,
                "Entity NBT stores anchor radius");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void entityReadDataRestoresEmployeeIdentityAndAnchor(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Read", true);
        EmployeeEntity entity = entity(helper, record);
        CompoundTag tag = new CompoundTag();
        entity.addAdditionalSaveData(tag);
        EmployeeEntity restored = ModEntityTypes.EMPLOYEE.get().create(helper.getLevel());

        restored.readAdditionalSaveData(tag);

        helper.assertTrue(restored.employeeIdValue().equals(record.employeeId().value()),
                "Entity NBT restores Employee Identity");
        helper.assertTrue(restored.anchorRadius() == EmployeeSchema.DEFAULT_IDLE_ANCHOR_RADIUS,
                "Entity NBT restores anchor radius");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void secondEntityBindingForSameEmployeeIsRejected(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Duplicate Bind", true);

        EmployeeOperationResult<EmployeeRecord> result = EmployeeService.INSTANCE.spawnAndBind(
                helper.getLevel(),
                record.employeeId(),
                helper.absolutePos(new BlockPos(3, 1, 2))
        );

        helper.assertTrue(!result.succeeded(), "Second entity binding is rejected");
        helper.assertTrue(result.failure().orElseThrow().code() == EmployeeFailureCode.ENTITY_ALREADY_BOUND,
                "Duplicate bind failure is explicit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void unknownEmployeeObservationFailsExplicitly(GameTestHelper helper) {
        EmployeeOperationResult<EmployeePresenceObservation> result = EmployeeService.INSTANCE.observe(
                helper.getLevel().getServer(),
                new EmployeeId("butchercraft:employee/v1/0000000000000000000000000000000000000000000000000000000000000000")
        );

        helper.assertTrue(!result.succeeded(), "Unknown employee observation is rejected");
        helper.assertTrue(result.failure().orElseThrow().code() == EmployeeFailureCode.UNKNOWN_EMPLOYEE,
                "Unknown employee failure is explicit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void setPresenceAbsentWritesExplicitState(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Absent", false);

        EmployeeService.INSTANCE.setPresence(
                helper.getLevel().getServer(),
                record.employeeId(),
                EmployeePresenceState.ABSENT
        );
        EmployeeRecord updated = EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(record.employeeId())
                .orElseThrow();

        helper.assertTrue(updated.presenceState() == EmployeePresenceState.ABSENT,
                "Absent presence is stored explicitly");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void setPresenceScheduledIsRejectedBecauseItIsDerived(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Scheduled Reject", false);

        EmployeeOperationResult<EmployeeRecord> result = EmployeeService.INSTANCE.setPresence(
                helper.getLevel().getServer(),
                record.employeeId(),
                EmployeePresenceState.SCHEDULED
        );

        helper.assertTrue(!result.succeeded(), "Scheduled presence cannot be written directly");
        helper.assertTrue(result.failure().orElseThrow().code() == EmployeeFailureCode.INVALID_PRESENCE_STATE,
                "Derived presence write failure is explicit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void setShiftUsesBusinessRuntimeShiftIdentity(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Evening", false);

        EmployeeRecord updated = EmployeeService.INSTANCE.assignShift(
                helper.getLevel().getServer(),
                record.employeeId(),
                "evening_shift"
        ).orThrow();

        helper.assertTrue(updated.assignedShift().orElseThrow().shiftId().equals("evening_shift"),
                "Shift command assigns configured shift");
        helper.assertTrue(updated.assignedShift().orElseThrow().configurationIdentity().startsWith(
                "butchercraft:business_runtime_config/v1/"), "Shift assignment stores Business Runtime config identity");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void pureObservationReportsScheduledDuringAssignedShift(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Scheduled", false);

        EmployeePresenceObservation observation = manager(helper).observe(
                record.employeeId(),
                observe(helper, 0L, 7, 0),
                configuration(helper)
        ).orThrow();

        helper.assertTrue(observation.presenceState() == EmployeePresenceState.SCHEDULED,
                "Employee is scheduled during assigned active shift");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void pureObservationReportsOffShiftOutsideAssignedShift(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Off Shift", false);

        EmployeePresenceObservation observation = manager(helper).observe(
                record.employeeId(),
                observe(helper, 0L, 15, 0),
                configuration(helper)
        ).orThrow();

        helper.assertTrue(observation.presenceState() == EmployeePresenceState.OFF_SHIFT,
                "Employee is off shift outside assigned shift");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void staleShiftIdentityMakesObservationUnavailable(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Stale Shift", false);
        EmployeeShiftAssignment assigned = record.assignedShift().orElseThrow();
        EmployeeShiftAssignment stale = new EmployeeShiftAssignment(
                assigned.shiftId(),
                "butchercraft:business_shift/v1/ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
                assigned.shiftDisplayName(),
                assigned.shiftSetIdentity(),
                assigned.configurationIdentity()
        );
        manager(helper).assignShift(record.employeeId(), Optional.of(stale));

        EmployeePresenceObservation observation = manager(helper).observe(
                record.employeeId(),
                observe(helper, 0L, 7, 0),
                configuration(helper)
        ).orThrow();

        helper.assertTrue(observation.presenceState() == EmployeePresenceState.UNAVAILABLE,
                "Changed or missing shift identity is visible as unavailable");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void inactiveEmployeeObservationIsUnavailable(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Inactive", false);
        manager(helper).transitionStatus(record.employeeId(), EmployeeStatus.INACTIVE);

        EmployeePresenceObservation observation = manager(helper).observe(
                record.employeeId(),
                observe(helper, 0L, 7, 0),
                configuration(helper)
        ).orThrow();

        helper.assertTrue(observation.presenceState() == EmployeePresenceState.UNAVAILABLE,
                "Inactive employee is unavailable");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void terminatedEmployeeCannotSpawnBoundEntity(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Terminated", false);
        EmployeeService.INSTANCE.transitionStatus(
                helper.getLevel().getServer(),
                record.employeeId(),
                EmployeeStatus.TERMINATED
        );

        EmployeeOperationResult<EmployeeRecord> result = EmployeeService.INSTANCE.spawnAndBind(
                helper.getLevel(),
                record.employeeId(),
                helper.absolutePos(EMPLOYEE_POS)
        );

        helper.assertTrue(!result.succeeded(), "Terminated employee cannot be bound to an entity");
        helper.assertTrue(result.failure().orElseThrow().code() == EmployeeFailureCode.TERMINATED_EMPLOYEE,
                "Terminated binding failure is explicit");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void employeeEntityDoesNotHoldItems(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Empty Hands", true);
        EmployeeEntity entity = entity(helper, record);

        helper.assertTrue(entity.getMainHandItem().isEmpty(), "Employee entity has no item-carrying behavior");
        helper.assertTrue(entity.getOffhandItem().isEmpty(), "Employee entity has no offhand behavior");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void employeeEntityDoesNotBecomeVillagerAi(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Not Villager", true);

        Entity entity = entity(helper, record);

        helper.assertTrue(!(entity instanceof Villager),
                "Employee foundation does not convert villagers or use villager job sites");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void employeePersistencePathUsesDedicatedFile(GameTestHelper helper) {
        String path = EmployeeService.employeeFile(helper.getLevel().getServer()).toString();

        helper.assertTrue(path.endsWith("butchercraft\\employee_records.json")
                        || path.endsWith("butchercraft/employee_records.json"),
                "Employee records use dedicated persistence");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void employeeCreationDoesNotSubmitSchedulerWork(GameTestHelper helper) {
        int before = SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer()).registry().size();

        create(helper, "Foundation Scheduler Boundary", true);

        int after = SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer()).registry().size();
        helper.assertTrue(before == after, "Employee creation does not submit Scheduler work");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = DEPARTMENT_BATCH)
    public static void employeeCanBeAssignedToProcessingDepartment(GameTestHelper helper) {
        EmployeeRecord record = create(helper, "Foundation Department", false);

        EmployeeRecord updated = EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                DepartmentSchema.PROCESSING.value()
        ).orThrow();
        EmployeePresenceObservation observation = EmployeeService.INSTANCE.observe(
                helper.getLevel().getServer(),
                record.employeeId()
        ).orThrow();

        helper.assertTrue(updated.assignedDepartmentId().filter(DepartmentSchema.PROCESSING::equals).isPresent(),
                "Employee record stores the assigned department");
        helper.assertTrue(observation.assignedDepartmentId().filter(DepartmentSchema.PROCESSING::equals).isPresent(),
                "Employee observation reports the assigned department");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = DEPARTMENT_BATCH)
    public static void presentEmployeeUsesProcessingDepartmentAnchor(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeRecord record = create(helper, "Foundation Department Anchor", true);
        EmployeeEntity entity = entity(helper, record);
        DepartmentAnchor anchor = assignProcessingAnchor(helper, DEPARTMENT_POS, 1);

        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                DepartmentSchema.PROCESSING.value()
        ).orThrow();
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.assertTrue(entity.anchorPos().equals(new BlockPos(anchor.x(), anchor.y(), anchor.z())),
                "Present employee targets the Processing department anchor");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_DEPARTMENT.serializedName()),
                "Employee outside the department radius is walking to the department");
        helper.assertTrue(entity.getMainHandItem().isEmpty() && entity.getOffhandItem().isEmpty(),
                "Department navigation does not create item-carrying behavior");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = DEPARTMENT_BATCH)
    public static void employeeIdlesWhenAlreadyInsideDepartmentRadius(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeRecord record = create(helper, "Foundation Department Idle", true);
        EmployeeEntity entity = entity(helper, record);
        assignProcessingAnchor(helper, EMPLOYEE_POS, 2);

        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                DepartmentSchema.PROCESSING.value()
        ).orThrow();
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.assertTrue(entity.insideAnchorRadius(), "Employee inside department radius remains bounded there");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.IDLE.serializedName()),
                "Employee inside the department radius idles");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = DEPARTMENT_BATCH)
    public static void displacedEmployeeReturnsTowardDepartmentAnchor(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeRecord record = create(helper, "Foundation Department Return", true);
        EmployeeEntity entity = entity(helper, record);
        DepartmentAnchor anchor = assignProcessingAnchor(helper, DEPARTMENT_POS, 1);

        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                DepartmentSchema.PROCESSING.value()
        ).orThrow();
        entity.moveTo(anchor.x() + 8.5D, anchor.y(), anchor.z() + 0.5D, 0.0F, 0.0F);
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.assertTrue(!entity.insideAnchorRadius(), "Displaced employee is outside the department radius");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_DEPARTMENT.serializedName()),
                "Displaced present employee returns toward the department anchor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = DEPARTMENT_BATCH)
    public static void unanchoredDepartmentsRemainDefinitionOnlyNavigationTargets(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeRecord record = create(helper, "Foundation Definition Department", true);
        EmployeeEntity entity = entity(helper, record);

        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                DepartmentSchema.PACKAGING.value()
        ).orThrow();
        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.assertTrue(entity.anchorPos().equals(helper.absolutePos(EMPLOYEE_POS)),
                "Unanchored departments do not replace the employee anchor");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.IDLE.serializedName()),
                "Present employee idles when assigned department has no functional anchor");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = DEPARTMENT_BATCH)
    public static void departmentAssignmentDoesNotSubmitSchedulerWork(GameTestHelper helper) {
        int before = SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer()).registry().size();
        EmployeeRecord record = create(helper, "Foundation Department Scheduler Boundary", true);

        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                DepartmentSchema.PROCESSING.value()
        ).orThrow();

        int after = SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer()).registry().size();
        helper.assertTrue(before == after, "Department assignment does not submit Scheduler work");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120, batch = DEPARTMENT_ANCHOR_COMMAND_BATCH)
    public static void departmentAnchorCommandUpdatesOnlyDepartmentAnchor(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        DepartmentRecord beforeRecord = EmployeeService.INSTANCE.departmentManagerFor(helper.getLevel().getServer())
                .find(DepartmentSchema.PROCESSING)
                .orElseThrow();
        DepartmentAnchor previousAnchor = beforeRecord.anchor().orElseThrow();
        EmployeeRecord employee = createPresentProcessingEmployeeAt(
                helper,
                "Anchor Command Employee",
                EMPLOYEE_POS
        );
        Counts beforeCounts = counts(helper);
        BlockPos sourceAnchor = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos explicitAnchor = helper.absolutePos(new BlockPos(3, 1, 3));
        CommandSourceStack source = commandSource(helper)
                .withPosition(Vec3.atCenterOf(sourceAnchor));

        List<String> departmentSuggestions = suggestions(helper, source, "butchercraft department set-anchor ");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.PROCESSING.value()),
                "Department anchor command suggests processing");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.PACKAGING.value()),
                "Department anchor command suggests packaging");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.SHIPPING.value()),
                "Department anchor command suggests shipping");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.OFFICE.value()),
                "Department anchor command suggests office");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.MAINTENANCE.value()),
                "Department anchor command suggests maintenance");
        List<String> partialSuggestions = suggestions(helper, source, "butchercraft department set-anchor pro");
        helper.assertTrue(partialSuggestions.contains(DepartmentSchema.PROCESSING.value()),
                "Partial department anchor command input suggests processing");

        int sourcePositionResult = execute(
                helper,
                source,
                "butchercraft department set-anchor " + DepartmentSchema.PROCESSING.value()
        );
        DepartmentRecord sourceUpdated = EmployeeService.INSTANCE.departmentManagerFor(helper.getLevel().getServer())
                .find(DepartmentSchema.PROCESSING)
                .orElseThrow();
        DepartmentAnchor sourceUpdatedAnchor = sourceUpdated.anchor().orElseThrow();

        helper.assertTrue(sourcePositionResult == 1, "Source-position anchor command succeeds");
        assertAnchor(helper, sourceUpdatedAnchor, sourceAnchor, previousAnchor.radius());
        helper.assertTrue(sourceUpdated.departmentId().equals(beforeRecord.departmentId()),
                "Department identity is preserved by source-position anchor command");
        helper.assertTrue(sourceUpdated.worldIdentityRoot().equals(beforeRecord.worldIdentityRoot())
                        && sourceUpdated.worldIdentityRootDigest().equals(beforeRecord.worldIdentityRootDigest()),
                "World identity fields are preserved by source-position anchor command");
        helper.assertTrue(sourceUpdated.recordRevision() == beforeRecord.recordRevision() + 1L,
                "Changed department anchor increments the department record revision");

        int duplicateResult = execute(
                helper,
                source,
                "butchercraft department set-anchor " + DepartmentSchema.PROCESSING.value()
        );
        DepartmentRecord duplicateUpdated = EmployeeService.INSTANCE.departmentManagerFor(helper.getLevel().getServer())
                .find(DepartmentSchema.PROCESSING)
                .orElseThrow();
        helper.assertTrue(duplicateResult == 1, "Duplicate identical anchor command succeeds");
        helper.assertTrue(duplicateUpdated.recordRevision() == sourceUpdated.recordRevision(),
                "Duplicate identical anchor command does not churn the department revision");

        int explicitResult = execute(
                helper,
                source,
                "butchercraft department set-anchor " + DepartmentSchema.PROCESSING.value()
                        + " " + explicitAnchor.getX()
                        + " " + explicitAnchor.getY()
                        + " " + explicitAnchor.getZ()
        );
        DepartmentRecord explicitUpdated = EmployeeService.INSTANCE.departmentManagerFor(helper.getLevel().getServer())
                .find(DepartmentSchema.PROCESSING)
                .orElseThrow();
        DepartmentAnchor explicitUpdatedAnchor = explicitUpdated.anchor().orElseThrow();

        helper.assertTrue(explicitResult == 1, "Explicit coordinate anchor command succeeds");
        assertAnchor(helper, explicitUpdatedAnchor, explicitAnchor, previousAnchor.radius());
        helper.assertTrue(explicitUpdated.departmentId().equals(beforeRecord.departmentId()),
                "Department identity is preserved by explicit coordinate anchor command");
        helper.assertTrue(explicitUpdated.recordRevision() == sourceUpdated.recordRevision() + 1L,
                "Second changed department anchor increments the department record revision once");

        int unknownResult = execute(helper, source, "butchercraft department set-anchor unknown_department");
        DepartmentRecord afterUnknown = EmployeeService.INSTANCE.departmentManagerFor(helper.getLevel().getServer())
                .find(DepartmentSchema.PROCESSING)
                .orElseThrow();
        helper.assertTrue(unknownResult == 0, "Unknown department anchor command is rejected");
        helper.assertTrue(afterUnknown.equals(explicitUpdated),
                "Unknown department rejection does not alter the existing department");

        DepartmentStorage storage = new DepartmentStorage(EmployeeService.departmentFile(helper.getLevel().getServer()));
        DepartmentAnchor persistedAnchor = storage.load(WorldIdentityRootIdentities.from(
                        WorldIdentityService.INSTANCE.getOrCreate(helper.getLevel().getServer())))
                .registry()
                .find(DepartmentSchema.PROCESSING)
                .orElseThrow()
                .anchor()
                .orElseThrow();
        assertAnchor(helper, persistedAnchor, explicitAnchor, previousAnchor.radius());

        EmployeeRecord afterEmployee = EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(employee.employeeId())
                .orElseThrow();
        helper.assertTrue(afterEmployee.employeeId().equals(employee.employeeId()),
                "Department anchor command preserves Employee Identity");
        helper.assertTrue(afterEmployee.assignedDepartmentId().filter(DepartmentSchema.PROCESSING::equals).isPresent(),
                "Department anchor command preserves existing employee department assignment");
        helper.assertTrue(counts(helper).equals(beforeCounts),
                "Department anchor command does not mutate Production, Scheduler, Execution, Inventory, or reservations");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = NAVIGATION_DIRECT_DEPARTMENT_BATCH)
    public static void unobstructedDepartmentTravelStartsAndArrives(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        setupOpenNavigationField(helper);
        assignProcessingAnchor(helper, DEPARTMENT_POS, 1);
        EmployeeRecord record = createPresentProcessingEmployeeAt(
                helper,
                "Direct Department Employee",
                DIRECT_DEPARTMENT_EMPLOYEE_POS
        );
        EmployeeEntity entity = entity(helper, record);
        boolean[] sawStartedPath = {false};
        boolean[] sawNodeProgress = {false};

        EmployeeService.INSTANCE.synchronizeEntity(entity);
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_DEPARTMENT.serializedName()),
                "Open-field department travel starts in department travel state");

        helper.succeedWhen(() -> {
            captureNavigationProgress(entity, sawStartedPath, sawNodeProgress);
            EmployeeService.INSTANCE.synchronizeEntity(entity);
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            helper.assertTrue(!diagnostics.recoveryPhase().equals("safe_failure"),
                    "Open-field department travel does not enter safe failure: " + diagnostics);
            helper.assertTrue(sawStartedPath[0],
                    "Accepted department path was observed before arrival: " + entity.navigationDiagnostics());
            helper.assertTrue(sawNodeProgress[0],
                    "Accepted department path produced node progress: " + entity.navigationDiagnostics());
            helper.assertTrue(entity.insideAnchorRadius(),
                    "Open-field department travel reaches the department anchor: " + entity.navigationDiagnostics()
                            + " | entity=" + entity.blockPosition()
                            + " | target=" + helper.absolutePos(DEPARTMENT_POS));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 720, batch = NAVIGATION_RANGE_DEPARTMENT_BATCH)
    public static void openFieldDepartmentTravelSupportsSchemaOneRange(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        int startX = 2;
        setupOpenNavigationField(helper, startX + MAX_NAVIGATION_RANGE + 2, 12);
        int[] distances = {5, 16, 32, MAX_NAVIGATION_RANGE};

        for (int index = 0; index < distances.length; index++) {
            EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
            setupOpenNavigationField(helper, startX + MAX_NAVIGATION_RANGE + 2, 3);
            int laneZ = 2;
            BlockPos anchor = new BlockPos(startX + distances[index], 1, laneZ);
            assignProcessingAnchor(helper, anchor, 1);
            EmployeeRecord record = createPresentEmployeeAtDepartment(
                    helper,
                    "Range Department " + distances[index],
                    new BlockPos(startX, 1, laneZ),
                    DepartmentSchema.PROCESSING
            );
            EmployeeEntity entity = entity(helper, record);
            entity.setOnGround(true);
            EmployeeService.INSTANCE.synchronizeEntity(entity);
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            Path path = entity.getNavigation().createPath(helper.absolutePos(anchor), 1);

            helper.assertTrue(diagnostics.configuredMaximumNavigationRange() == MAX_NAVIGATION_RANGE,
                    "Navigation diagnostics expose the schema-1 maximum range");
            helper.assertTrue(diagnostics.pathSearchRange() >= MAX_NAVIGATION_RANGE,
                    "Minecraft path search range is aligned with the Workforce range policy");
            helper.assertTrue(path != null && path.getNodeCount() > 0,
                    "Minecraft path search reaches a department anchor at "
                            + distances[index] + " blocks");
            helper.assertTrue(path.getEndNode() != null
                            && path.getEndNode().asBlockPos().distManhattan(helper.absolutePos(anchor)) <= 1,
                    "Department path endpoint remains within operating tolerance at "
                            + distances[index] + " blocks");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 1100, batch = NAVIGATION_RANGE_OBSTACLE_BATCH)
    public static void departmentRouteAroundObstacleBeyondFifteenBlocksPreservesPath(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        setupOpenNavigationField(helper, 26, 9);
        for (int y = 1; y <= 2; y++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(12, y, z), Blocks.STONE);
            }
        }
        assignProcessingAnchor(helper, new BlockPos(22, 1, 2), 1);
        EmployeeRecord record = createPresentProcessingEmployeeAt(
                helper,
                "Range Obstacle Employee",
                new BlockPos(2, 1, 2)
        );
        EmployeeEntity entity = entity(helper, record);
        boolean[] sawStartedPath = {false};
        boolean[] sawNodeProgress = {false};

        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.succeedWhen(() -> {
            captureNavigationProgress(entity, sawStartedPath, sawNodeProgress);
            EmployeeService.INSTANCE.synchronizeEntity(entity);
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            helper.assertTrue(diagnostics.destinationWithinRange(),
                    "Obstacle-route destination remains inside the configured navigation range: " + diagnostics);
            helper.assertTrue(!diagnostics.recoveryPhase().equals("safe_failure"),
                    "Reachable obstacle route beyond 15 blocks must not fail safely: " + diagnostics);
            helper.assertTrue(diagnostics.pathReplacementCount() <= 1,
                    "Obstacle route preserves the accepted path: " + diagnostics);
            helper.assertTrue(sawStartedPath[0],
                    "Obstacle route starts an accepted path beyond 15 blocks: " + diagnostics);
            helper.assertTrue(sawNodeProgress[0] || diagnostics.activePathNodeIndex() > 0,
                    "Obstacle route records path-node progress beyond 15 blocks: " + diagnostics);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 520, batch = NAVIGATION_RANGE_FAILURE_BATCH)
    public static void departmentDestinationBeyondSchemaOneRangeFailsExplicitly(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        int startX = 2;
        setupOpenNavigationField(helper, startX + MAX_NAVIGATION_RANGE + 4, 3);
        assignProcessingAnchor(helper, new BlockPos(startX + MAX_NAVIGATION_RANGE + 2, 1, 1), 1);
        EmployeeRecord record = createPresentProcessingEmployeeAt(
                helper,
                "Range Failure Department",
                new BlockPos(startX, 1, 1)
        );
        EmployeeEntity entity = entity(helper, record);

        EmployeeService.INSTANCE.synchronizeEntity(entity);

        helper.succeedWhen(() -> {
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            helper.assertTrue(!diagnostics.destinationWithinRange(),
                    "Destination beyond the schema-1 range fails range validation: " + diagnostics);
            helper.assertTrue(!diagnostics.pathAvailable(),
                    "Out-of-range destination never starts a Minecraft path: " + diagnostics);
            helper.assertTrue(diagnostics.pathReplacementCount() == 0,
                    "Out-of-range destination is rejected before path publication: " + diagnostics);
            helper.assertTrue(diagnostics.lastFailureReason().equals("destination_out_of_range")
                            && diagnostics.recoveryPhase().equals("safe_failure"),
                    "Out-of-range department travel fails with a typed reason: " + diagnostics);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 180, batch = NAVIGATION_BLOCKED_ANCHOR_BATCH)
    public static void departmentTravelAcceptsNearbyEndpointWhenLogicalAnchorIsBlocked(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        setupOpenNavigationField(helper);
        helper.setBlock(BLOCKED_ANCHOR_POS, Blocks.STONE);
        helper.setBlock(BLOCKED_ANCHOR_POS.above(), Blocks.STONE);
        assignProcessingAnchor(helper, BLOCKED_ANCHOR_POS, 1);
        EmployeeRecord record = createPresentProcessingEmployeeAt(
                helper,
                "Blocked Anchor Employee",
                DIRECT_DEPARTMENT_EMPLOYEE_POS
        );
        EmployeeEntity entity = entity(helper, record);
        boolean[] sawStartedPath = {false};
        boolean[] sawNodeProgress = {false};

        EmployeeService.INSTANCE.synchronizeEntity(entity);
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_DEPARTMENT.serializedName()),
                "Blocked logical anchor still starts department travel to a nearby candidate");

        helper.succeedWhen(() -> {
            captureNavigationProgress(entity, sawStartedPath, sawNodeProgress);
            EmployeeService.INSTANCE.synchronizeEntity(entity);
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            helper.assertTrue(!diagnostics.recoveryPhase().equals("safe_failure"),
                    "Nearby endpoint acceptance avoids false safe failure: " + diagnostics);
            helper.assertTrue(diagnostics.currentDestination() != null
                            && !diagnostics.currentDestination().equals(helper.absolutePos(BLOCKED_ANCHOR_POS)),
                    "Blocked logical anchor is not used as the active standing position: " + diagnostics);
            helper.assertTrue(sawStartedPath[0],
                    "Accepted nearby endpoint path was observed before arrival: " + entity.navigationDiagnostics());
            helper.assertTrue(sawNodeProgress[0],
                    "Accepted nearby endpoint path produced node progress: " + entity.navigationDiagnostics());
            helper.assertTrue(entity.insideAnchorRadius()
                            && !entity.blockPosition().equals(helper.absolutePos(BLOCKED_ANCHOR_POS)),
                    "Employee reaches the department radius without standing inside the blocked logical anchor: "
                            + entity.navigationDiagnostics()
                            + " | entity=" + entity.blockPosition()
                            + " | anchor=" + helper.absolutePos(BLOCKED_ANCHOR_POS));
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 420, batch = NAVIGATION_REAR_ROUTE_BATCH)
    public static void employeeFollowsRearEntranceRouteWhenDirectDistanceTemporarilyIncreases(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        setupRearEntranceDepartment(helper, false);
        EmployeeRecord record = createNavigationFixtureEmployee(helper, "Rear Route Employee");
        EmployeeEntity entity = entity(helper, record);
        BlockPos target = helper.absolutePos(REAR_ROUTE_DEPARTMENT_POS);
        double initialFinalDistance = horizontalDistance(entity, target);
        int[] highestNodeIndex = {-1};
        boolean[] sawPathNodeProgress = {false};
        boolean[] sawDirectDistanceIncrease = {false};

        EmployeeService.INSTANCE.synchronizeEntity(entity);
        helper.assertTrue(entity.anchorPos().equals(target),
                "Navigation fixture applies the department anchor before travel starts");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_DEPARTMENT.serializedName()),
                "Navigation fixture starts in department travel state");

        helper.succeedWhen(() -> {
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            if (diagnostics.pathAvailable()) {
                if (diagnostics.activePathNodeIndex() > highestNodeIndex[0]) {
                    sawPathNodeProgress[0] = highestNodeIndex[0] >= 0;
                    highestNodeIndex[0] = diagnostics.activePathNodeIndex();
                }
                if (diagnostics.distanceToFinalDestination() > initialFinalDistance + 0.05D) {
                    sawDirectDistanceIncrease[0] = true;
                }
            }
            helper.assertTrue(diagnostics.pathReplacementCount() <= 1,
                    "Rear route preserves the active Path instead of repeatedly replacing it: " + diagnostics);
            helper.assertTrue(!diagnostics.recoveryPhase().equals("safe_failure"),
                    "Reachable rear-route department must not enter safe failure: " + diagnostics);
            helper.assertTrue(entity.insideAnchorRadius()
                            && entity.blockPosition().getZ() >= target.getZ(),
                    "Employee reaches the department through the rear opening: " + diagnostics
                            + " | entity=" + entity.blockPosition()
                            + " | target=" + target);
            helper.assertTrue(sawPathNodeProgress[0],
                    "Employee advances through active path nodes while navigating around the building");
            helper.assertTrue(sawDirectDistanceIncrease[0],
                    "Direct final-destination distance may increase while path-node progress continues");
            helper.assertTrue(diagnostics.pathReplacementCount() == 1,
                    "Route completes with one accepted path replacement: " + diagnostics);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 620, batch = NAVIGATION_SEALED_ROUTE_BATCH)
    public static void sealedDepartmentBuildingFailsSafelyWithoutWalkingToNearestWall(GameTestHelper helper) {
        helper.getLevel().setDayTime(0L);
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        setupRearEntranceDepartment(helper, true);
        EmployeeRecord record = createNavigationFixtureEmployee(helper, "Sealed Route Employee");
        EmployeeEntity entity = entity(helper, record);
        BlockPos start = helper.absolutePos(REAR_ROUTE_EMPLOYEE_POS);

        EmployeeService.INSTANCE.synchronizeEntity(entity);
        helper.assertTrue(entity.anchorPos().equals(helper.absolutePos(REAR_ROUTE_DEPARTMENT_POS)),
                "Sealed navigation fixture applies the department anchor before travel starts");
        helper.assertTrue(entity.navigationStateValue().equals(EmployeeNavigationState.WALKING_TO_DEPARTMENT.serializedName()),
                "Sealed navigation fixture starts in department travel state");

        helper.succeedWhen(() -> {
            EmployeeEntity.NavigationDiagnostics diagnostics = entity.navigationDiagnostics();
            helper.assertTrue(diagnostics.pathReplacementCount() == 0,
                    "Sealed building rejects partial nearest-wall paths instead of accepting them: " + diagnostics);
            helper.assertTrue(entity.blockPosition().equals(start),
                    "Employee does not walk to the exterior wall on an incomplete path: " + diagnostics
                            + " | entity=" + entity.blockPosition()
                            + " | start=" + start);
            helper.assertTrue(diagnostics.lastFailureReason().equals("department_unreachable")
                            && diagnostics.recoveryPhase().equals("safe_failure"),
                    "Sealed building reaches bounded explicit navigation failure: " + diagnostics);
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = COMMAND_ACCEPTANCE_BATCH)
    public static void registeredCommandTreeUsesSynchronizedArgumentTypes(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();

        assertSynchronizedArgumentTypes(helper, dispatcher.getRoot());

        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80, batch = COMMAND_ACCEPTANCE_BATCH)
    public static void employeeAndDepartmentCommandSuggestionsAreExecutable(GameTestHelper helper) {
        EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
        EmployeeRecord tom = createExact(helper, "Tom", false);
        EmployeeRecord spaced = createExact(helper, "Tom Cutter", false);
        CommandSourceStack source = commandSource(helper);
        String statusPrefix = "butchercraft employee status ";

        List<String> employeeSuggestions = suggestions(helper, source, statusPrefix);
        helper.assertTrue(employeeSuggestions.contains(employeeNumberReference(tom)),
                "Employee suggestions include the simple-name employee number");
        helper.assertTrue(employeeSuggestions.contains("Tom"),
                "Employee suggestions include the executable simple display name");
        helper.assertTrue(employeeSuggestions.contains(tom.employeeId().value()),
                "Employee suggestions include the canonical Employee ID");
        helper.assertTrue(employeeSuggestions.contains(employeeNumberReference(spaced)),
                "Employee suggestions include the spaced-name employee number");
        helper.assertTrue(employeeSuggestions.contains("\"Tom Cutter\""),
                "Employee suggestions quote display names containing spaces");
        helper.assertTrue(employeeSuggestions.contains(spaced.employeeId().value()),
                "Employee suggestions include canonical Employee IDs for spaced display names");
        for (String suggestion : employeeSuggestions) {
            int result = execute(helper, source, statusPrefix + suggestion);
            helper.assertTrue(result == 1, "Employee suggestion executes unchanged: " + suggestion);
        }
        helper.assertTrue(execute(helper, source, statusPrefix + "#1") == 1,
                "Ordinal employee references execute manually");
        helper.assertTrue(execute(helper, source, statusPrefix + "Tom") == 1,
                "Simple display names execute manually");
        helper.assertTrue(execute(helper, source, statusPrefix + "\"Tom Cutter\"") == 1,
                "Quoted display names containing spaces execute manually");
        helper.assertTrue(execute(helper, source, statusPrefix + tom.employeeId().value()) == 1,
                "Canonical Employee IDs execute manually");

        String assignPrefix = "butchercraft employee assign-department Tom ";
        List<String> departmentSuggestions = suggestions(helper, source, assignPrefix);
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.PROCESSING.value()),
                "Department suggestions include processing");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.PACKAGING.value()),
                "Department suggestions include packaging");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.SHIPPING.value()),
                "Department suggestions include shipping");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.OFFICE.value()),
                "Department suggestions include office");
        helper.assertTrue(departmentSuggestions.contains(DepartmentSchema.MAINTENANCE.value()),
                "Department suggestions include maintenance");

        List<String> partialDepartmentSuggestions = suggestions(helper, source, assignPrefix + "pro");
        helper.assertTrue(partialDepartmentSuggestions.contains(DepartmentSchema.PROCESSING.value()),
                "Partial department input suggests processing");

        int assignmentResult = execute(helper, source, assignPrefix + DepartmentSchema.PROCESSING.value());
        helper.assertTrue(assignmentResult == 1, "Selected department suggestion executes assignment command");
        EmployeeRecord assigned = manager(helper).find(tom.employeeId()).orElseThrow();
        helper.assertTrue(assigned.assignedDepartmentId().filter(DepartmentSchema.PROCESSING::equals).isPresent(),
                "Executed department suggestion updates the employee assignment");

        int unknownResult = execute(helper, source, assignPrefix + "unknown_department");
        helper.assertTrue(unknownResult == 0, "Unknown department is rejected");
        EmployeeRecord stillAssigned = manager(helper).find(tom.employeeId()).orElseThrow();
        helper.assertTrue(stillAssigned.assignedDepartmentId().filter(DepartmentSchema.PROCESSING::equals).isPresent(),
                "Rejected department does not replace the existing assignment");
        helper.succeed();
    }

    private static EmployeeRecord create(GameTestHelper helper, String baseName, boolean spawnEntity) {
        resetEmployeesOnce(helper);
        return EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(),
                Optional.of(baseName + " " + helper.getLevel().getGameTime()),
                Optional.of(helper.absolutePos(EMPLOYEE_POS)),
                spawnEntity
        ).orThrow();
    }

    private static synchronized void resetEmployeesOnce(GameTestHelper helper) {
        if (!resetCompleted) {
            EmployeeService.INSTANCE.resetGameTestEmployees(helper.getLevel().getServer());
            resetCompleted = true;
        }
    }

    private static EmployeeRecord createExact(GameTestHelper helper, String displayName, boolean spawnEntity) {
        return EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(),
                Optional.of(displayName),
                Optional.of(helper.absolutePos(EMPLOYEE_POS)),
                spawnEntity
        ).orThrow();
    }

    private static EmployeeRecord createNavigationFixtureEmployee(GameTestHelper helper, String displayName) {
        DepartmentAnchor anchor = assignProcessingAnchor(helper, REAR_ROUTE_DEPARTMENT_POS, 1);
        EmployeeRecord record = EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(),
                Optional.of(displayName),
                Optional.of(helper.absolutePos(REAR_ROUTE_EMPLOYEE_POS)),
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
        EmployeeEntityLink link = EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(record.employeeId())
                .flatMap(EmployeeRecord::entityLink)
                .orElseThrow();
        EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .bindEntity(record.employeeId(), link, new EmployeeAnchor(
                        anchor.dimensionIdentity(),
                        anchor.x(),
                        anchor.y(),
                        anchor.z(),
                        anchor.radius()
                )).orThrow();
        helper.assertTrue(anchor.radius() == 1, "Navigation fixture uses a one-block department radius");
        return EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer())
                .find(record.employeeId())
                .orElseThrow();
    }

    private static EmployeeRecord createPresentProcessingEmployeeAt(
            GameTestHelper helper,
            String displayName,
            BlockPos relativePos
    ) {
        return createPresentEmployeeAtDepartment(helper, displayName, relativePos, DepartmentSchema.PROCESSING);
    }

    private static EmployeeRecord createPresentEmployeeAtDepartment(
            GameTestHelper helper,
            String displayName,
            BlockPos relativePos,
            DepartmentId departmentId
    ) {
        EmployeeRecord record = EmployeeService.INSTANCE.createGameTestEmployee(
                helper.getLevel(),
                Optional.of(displayName),
                Optional.of(helper.absolutePos(relativePos)),
                true
        ).orThrow();
        EmployeeService.INSTANCE.assignDepartment(
                helper.getLevel().getServer(),
                record.employeeId(),
                departmentId.value()
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

    private static void setupOpenNavigationField(GameTestHelper helper) {
        setupOpenNavigationField(helper, 5, 5);
    }

    private static void setupOpenNavigationField(GameTestHelper helper, int maxXInclusive, int maxZInclusive) {
        for (int x = 0; x <= maxXInclusive; x++) {
            for (int z = 0; z <= maxZInclusive; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
    }

    private static void setupRearEntranceDepartment(GameTestHelper helper, boolean sealed) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int x = 1; x <= 3; x++) {
                helper.setBlock(new BlockPos(x, y, 1), Blocks.STONE);
            }
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(1, y, z), Blocks.STONE);
                helper.setBlock(new BlockPos(3, y, z), Blocks.STONE);
            }
            helper.setBlock(new BlockPos(1, y, 3), Blocks.STONE);
            helper.setBlock(new BlockPos(3, y, 3), Blocks.STONE);
            if (sealed) {
                helper.setBlock(new BlockPos(2, y, 3), Blocks.STONE);
            } else {
                helper.setBlock(new BlockPos(2, y, 3), Blocks.AIR);
            }
        }
        helper.setBlock(REAR_ROUTE_DEPARTMENT_POS, Blocks.AIR);
        helper.setBlock(REAR_ROUTE_DEPARTMENT_POS.above(), Blocks.AIR);
    }

    private static double horizontalDistance(EmployeeEntity entity, BlockPos target) {
        double dx = target.getX() + 0.5D - entity.getX();
        double dz = target.getZ() + 0.5D - entity.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void assertAnchor(
            GameTestHelper helper,
            DepartmentAnchor anchor,
            BlockPos expectedPosition,
            int expectedRadius
    ) {
        helper.assertTrue(anchor.dimensionIdentity().equals(EmployeeService.dimensionIdentity(helper.getLevel())),
                "Department anchor dimension follows the command source dimension");
        helper.assertTrue(anchor.x() == expectedPosition.getX()
                        && anchor.y() == expectedPosition.getY()
                        && anchor.z() == expectedPosition.getZ(),
                "Department anchor position matches the command position");
        helper.assertTrue(anchor.radius() == expectedRadius,
                "Department anchor preserves the configured idle radius");
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
        if (diagnostics.pathReplacementCount() > 0 && entity.insideAnchorRadius()) {
            sawNodeProgress[0] = true;
        }
    }

    private static String employeeNumberReference(EmployeeRecord record) {
        return "#" + Math.addExact(record.sequence(), 1L);
    }

    private static CommandSourceStack commandSource(GameTestHelper helper) {
        return helper.getLevel().getServer().createCommandSourceStack()
                .withPermission(4)
                .withSuppressedOutput();
    }

    private static List<String> suggestions(GameTestHelper helper, CommandSourceStack source, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        return dispatcher.getCompletionSuggestions(dispatcher.parse(command, source))
                .join()
                .getList()
                .stream()
                .map(Suggestion::getText)
                .toList();
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

    private static Counts counts(GameTestHelper helper) {
        return new Counts(
                ProductionService.INSTANCE.managerFor(helper.getLevel().getServer()).runs().size(),
                SimulationSchedulerService.INSTANCE.managerFor(helper.getLevel().getServer()).registry().size(),
                ExecutionService.INSTANCE.managerFor(helper.getLevel().getServer()).operations().size(),
                InventoryService.INSTANCE.managerFor(helper.getLevel().getServer()).registry().size(),
                WorkstationReservationService.INSTANCE.activeReservations(helper.getLevel().getServer()).size()
        );
    }

    private static void assertSynchronizedArgumentTypes(
            GameTestHelper helper,
            CommandNode<CommandSourceStack> node
    ) {
        if (node instanceof ArgumentCommandNode<CommandSourceStack, ?> argumentNode) {
            try {
                ArgumentTypeInfos.byClass(argumentNode.getType());
            } catch (IllegalArgumentException exception) {
                helper.assertTrue(false, "Command argument type must synchronize: "
                        + argumentNode.getType().getClass().getName());
            }
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            assertSynchronizedArgumentTypes(helper, child);
        }
    }

    private static DepartmentAnchor assignProcessingAnchor(GameTestHelper helper, BlockPos relativePos, int radius) {
        return assignDepartmentAnchor(helper, DepartmentSchema.PROCESSING, relativePos, radius);
    }

    private static DepartmentAnchor assignDepartmentAnchor(
            GameTestHelper helper,
            DepartmentId departmentId,
            BlockPos relativePos,
            int radius
    ) {
        BlockPos absolute = helper.absolutePos(relativePos);
        DepartmentAnchor anchor = new DepartmentAnchor(
                EmployeeService.dimensionIdentity(helper.getLevel()),
                absolute.getX(),
                absolute.getY(),
                absolute.getZ(),
                radius
        );
        DepartmentRecord record = EmployeeService.INSTANCE.departmentManagerFor(helper.getLevel().getServer())
                .assignAnchor(departmentId, anchor);
        return record.anchor().orElseThrow();
    }

    private static EmployeeEntity entity(GameTestHelper helper, EmployeeRecord record) {
        ServerLevel level = helper.getLevel();
        Entity entity = level.getEntity(record.entityLink().orElseThrow().entityUuid());
        helper.assertTrue(entity instanceof EmployeeEntity, "Linked entity is an EmployeeEntity");
        return (EmployeeEntity) entity;
    }

    private static EmployeeManager manager(GameTestHelper helper) {
        return EmployeeService.INSTANCE.managerFor(helper.getLevel().getServer());
    }

    private static BusinessRuntimeCalendarConfiguration configuration(GameTestHelper helper) {
        return BusinessRuntimeCalendarService.INSTANCE
                .currentConfiguration(helper.getLevel().getServer())
                .orElseThrow();
    }

    private static BusinessRuntimeObservationSnapshot observe(
            GameTestHelper helper,
            long businessDay,
            int hour,
            int minute
    ) {
        return BusinessRuntimeObservationSnapshot.observe(
                calendar(businessDay, hour, minute),
                configuration(helper),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT
        );
    }

    private static BusinessCalendarSnapshot calendar(long businessDay, int hour, int minute) {
        return new BusinessCalendarSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                businessDay,
                BusinessDayOfWeek.fromDayIndex(businessDay),
                new BusinessTimeOfDay(hour, minute),
                0L,
                0L,
                BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS,
                "butchercraft:world_day/test/" + businessDay,
                WorldTimeConfiguration.enabled(60).identity(),
                "minecraft:overworld",
                0L,
                0L
        );
    }

    private record Counts(
            int productionRuns,
            int schedulerWork,
            int executionOperations,
            int inventoryEntries,
            int workstationReservations
    ) {
    }
}
