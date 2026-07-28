package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.entity.employee.EmployeeEntity;
import com.butchercraft.registration.ModEntityTypes;
import com.butchercraft.world.BusinessRuntimeCalendarService;
import com.butchercraft.world.EmployeeService;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.workforce.employee.EmployeeFailureCode;
import com.butchercraft.world.workforce.employee.EmployeeId;
import com.butchercraft.world.workforce.employee.EmployeeManager;
import com.butchercraft.world.workforce.employee.EmployeeOperationResult;
import com.butchercraft.world.workforce.employee.EmployeePresenceObservation;
import com.butchercraft.world.workforce.employee.EmployeePresenceState;
import com.butchercraft.world.workforce.employee.EmployeeRecord;
import com.butchercraft.world.workforce.employee.EmployeeSchema;
import com.butchercraft.world.workforce.employee.EmployeeShiftAssignment;
import com.butchercraft.world.workforce.employee.EmployeeStatus;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EmployeeFoundationGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final BlockPos EMPLOYEE_POS = new BlockPos(2, 1, 2);
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
}
