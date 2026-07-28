package com.butchercraft.test.gametest;

import com.butchercraft.ButcherCraft;
import com.butchercraft.productioncontrol.ProductionOrderNextAction;
import com.butchercraft.productioncontrol.ProductionOrderStatusSnapshot;
import com.butchercraft.world.business.runtime.BusinessOperatingSchedule;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarSchema;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.business.runtime.BusinessScheduleBoundary;
import com.butchercraft.world.business.runtime.BusinessShiftDefinition;
import com.butchercraft.world.business.runtime.BusinessShiftSet;
import com.butchercraft.world.production.ProductionDeadline;
import com.butchercraft.world.production.ProductionDeadlineCompletionTiming;
import com.butchercraft.world.production.ProductionDeadlineStatus;
import com.butchercraft.world.production.ProductionDeadlineType;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunRuntime;
import com.butchercraft.world.production.ProductionRunSnapshot;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionSchema;
import com.butchercraft.world.production.ProductionWorkstationChain;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

@GameTestHolder(ButcherCraft.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BusinessRuntimeDeadlineGameTests {
    private static final String TEMPLATE = "empty_5x4x5";
    private static final WorldTimeConfiguration WORLD_TIME_CONFIGURATION = WorldTimeConfiguration.enabled(60);
    private static final BusinessRuntimeCalendarConfiguration DEFAULT_RUNTIME_CONFIGURATION =
            BusinessRuntimeCalendarConfiguration.defaults(WORLD_TIME_CONFIGURATION.identity());
    private static final ProductionPlanId PLAN_ID = ProductionPlanId.of("butchercraft:gametest_deadline_plan");
    private static final ProductionRunId RUN_ID = ProductionRunId.forPlan(PLAN_ID);
    private static final String DEADLINE_SOURCE = "butchercraft:gametest_deadline";

    private BusinessRuntimeDeadlineGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void plantOpenDuringConfiguredHours(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot snapshot = observe(defaultConfiguration(), calendar(0L, 10, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(snapshot.plantOpen(), "Plant is open during the configured weekday operating window");
        helper.assertTrue(snapshot.currentOperatingWindow().isPresent(), "Open plant has a current operating window");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void plantClosedOutsideConfiguredHours(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot snapshot = observe(defaultConfiguration(), calendar(0L, 19, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(!snapshot.plantOpen(), "Plant is closed after the configured weekday operating window");
        helper.assertTrue(snapshot.currentOperatingWindow().isEmpty(), "Closed plant has no current operating window");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void openingBoundaryIsInclusive(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot snapshot = observe(defaultConfiguration(), calendar(0L, 6, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(snapshot.plantOpen(), "Opening boundary is included in business hours");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void closingBoundaryIsExclusive(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot snapshot = observe(defaultConfiguration(), calendar(0L, 18, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(!snapshot.plantOpen(), "Closing boundary is excluded from business hours");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void closedWeekdayStaysClosed(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot snapshot = observe(defaultConfiguration(), calendar(5L, 10, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(snapshot.calendar().dayOfWeek() == BusinessDayOfWeek.SATURDAY,
                "Calendar fixture is Saturday");
        helper.assertTrue(!snapshot.plantOpen(), "Configured closed day remains closed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void overnightOperatingWindowSpansMidnight(GameTestHelper helper) {
        BusinessOperatingSchedule schedule = BusinessOperatingSchedule.builder()
                .open(BusinessDayOfWeek.MONDAY, "22:00-02:00")
                .build();
        BusinessRuntimeCalendarConfiguration configuration = configuration(schedule, BusinessShiftSet.of(
                java.util.List.of(BusinessShiftDefinition.of("night_shift", "Night Shift", "22:00", "02:00",
                        Set.of(BusinessDayOfWeek.MONDAY))),
                schedule
        ));

        BusinessRuntimeObservationSnapshot beforeMidnight = observe(configuration, calendar(0L, 23, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);
        BusinessRuntimeObservationSnapshot afterMidnight = observe(configuration, calendar(1L, 1, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(beforeMidnight.plantOpen(), "Overnight window is open before midnight");
        helper.assertTrue(afterMidnight.plantOpen(), "Overnight window is open after midnight");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void activeShiftIsVisible(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot snapshot = observe(defaultConfiguration(), calendar(0L, 7, 30),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(snapshot.activeShift().map(BusinessScheduleBoundary::displayName)
                        .filter("Day Shift"::equals)
                        .isPresent(),
                "Day Shift is the active shift during the morning");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void nextShiftIsDeterministic(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot snapshot = observe(defaultConfiguration(), calendar(0L, 13, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(snapshot.nextShift().map(BusinessScheduleBoundary::displayName)
                        .filter("Evening Shift"::equals)
                        .isPresent(),
                "Evening Shift is the deterministic next shift before its start");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void forwardTimeJumpUpdatesBusinessRuntimeObservation(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot before = observe(defaultConfiguration(), calendar(0L, 5, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);
        BusinessRuntimeObservationSnapshot after = observe(defaultConfiguration(), calendar(2L, 10, 0),
                WorldTimeMovementClassification.FORWARD_JUMP);

        helper.assertTrue(!before.plantOpen(), "Plant starts closed before opening");
        helper.assertTrue(after.plantOpen(), "Forward jump observes the new business time directly");
        helper.assertTrue(after.movementClassification() == WorldTimeMovementClassification.FORWARD_JUMP,
                "Business Runtime reports the supplied forward movement classification");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void backwardTimeJumpUpdatesDeadlineDisplaySafely(GameTestHelper helper) {
        ProductionDeadline deadline = deadline(60)
                .evaluate(ProductionRunStatus.READY, calendar(0L, 12, 0))
                .evaluate(ProductionRunStatus.READY, calendar(0L, 10, 30));

        helper.assertTrue(deadline.status() == ProductionDeadlineStatus.UPCOMING,
                "Nonterminal deadline display follows the recovered earlier calendar");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void productionDeadlineBeginsUpcoming(GameTestHelper helper) {
        ProductionDeadline deadline = deadline(120);

        helper.assertTrue(deadline.status() == ProductionDeadlineStatus.UPCOMING,
                "New default target deadline starts as upcoming");
        helper.assertTrue(deadline.businessTime().equals(new BusinessTimeOfDay(12, 0)),
                "Default offset creates the expected business deadline time");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void deadlineBecomesDueAndOverdue(GameTestHelper helper) {
        ProductionDeadline due = deadline(60).evaluate(ProductionRunStatus.READY, calendar(0L, 11, 0));
        ProductionDeadline overdue = due.evaluate(ProductionRunStatus.READY, calendar(0L, 11, 1));

        helper.assertTrue(due.status() == ProductionDeadlineStatus.DUE_NOW, "Deadline is due at the exact minute");
        helper.assertTrue(overdue.status() == ProductionDeadlineStatus.OVERDUE, "Deadline becomes overdue after due time");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void productionCanContinueAfterOverdueDeadline(GameTestHelper helper) {
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(RUN_ID, PLAN_ID, 120L, 0L);
        runtime.setDeadline(deadline(30), 0L);
        runtime.evaluateDeadline(calendar(0L, 11, 0));
        runtime.markReady(1L);

        helper.assertTrue(runtime.snapshot().deadline().orElseThrow().status() == ProductionDeadlineStatus.OVERDUE,
                "Run observes an overdue deadline");
        helper.assertTrue(runtime.snapshot().status() == ProductionRunStatus.READY,
                "Overdue deadline does not block Production progress");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void earlyCompletionClassified(GameTestHelper helper) {
        ProductionDeadline deadline = deadline(120).evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 11, 0));

        helper.assertTrue(deadline.status() == ProductionDeadlineStatus.COMPLETED_EARLY,
                "Completion before due time is early");
        helper.assertTrue(deadline.completionTiming().orElseThrow() == ProductionDeadlineCompletionTiming.EARLY,
                "Early completion timing is recorded");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void onTimeCompletionClassified(GameTestHelper helper) {
        ProductionDeadline deadline = deadline(60).evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 11, 0));

        helper.assertTrue(deadline.status() == ProductionDeadlineStatus.COMPLETED_ON_TIME,
                "Completion at due time is on time");
        helper.assertTrue(deadline.completionTiming().orElseThrow() == ProductionDeadlineCompletionTiming.ON_TIME,
                "On-time completion timing is recorded");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void lateCompletionClassified(GameTestHelper helper) {
        ProductionDeadline deadline = deadline(60).evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 11, 30));

        helper.assertTrue(deadline.status() == ProductionDeadlineStatus.COMPLETED_LATE,
                "Completion after due time is late");
        helper.assertTrue(deadline.completionTiming().orElseThrow() == ProductionDeadlineCompletionTiming.LATE,
                "Late completion timing is recorded");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void terminalDeadlineResultSurvivesRecordReconstruction(GameTestHelper helper) {
        ProductionDeadline late = deadline(60).evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 11, 30));
        ProductionDeadline restored = new ProductionDeadline(
                late.schemaVersion(),
                late.identity(),
                late.runId(),
                ProductionDeadlineType.TARGET,
                late.businessDayIndex(),
                late.businessTime(),
                late.businessRuntimeConfigurationIdentity(),
                late.sourceWorldDayIdentity(),
                late.sourceDimensionIdentity(),
                late.sourceIdentity(),
                late.status(),
                late.locked(),
                late.completionTiming(),
                late.evaluatedWorldDayIdentity()
        );

        helper.assertTrue(restored.equals(late), "Deadline terminal result reconstructs from persisted fields");
        helper.assertTrue(restored.status() == ProductionDeadlineStatus.COMPLETED_LATE,
                "Restored deadline keeps terminal lateness evidence");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void legacyNoDeadlineProductionRunRemainsValid(GameTestHelper helper) {
        ProductionRunSnapshot run = new ProductionRunSnapshot(
                RUN_ID,
                PLAN_ID,
                ProductionRunStatus.PLANNED,
                0L,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                60L,
                0L,
                0,
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0L,
                ProductionSchema.CURRENT_VERSION
        );

        helper.assertTrue(run.deadline().isEmpty(), "Legacy no-deadline run remains valid");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void productionOrderDisplaysOpenClosedState(GameTestHelper helper) {
        ProductionOrderStatusSnapshot open = ProductionOrderStatusSnapshot.empty(Optional.of(
                observe(defaultConfiguration(), calendar(0L, 10, 0),
                        WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT)
        ));
        ProductionOrderStatusSnapshot closed = ProductionOrderStatusSnapshot.empty(Optional.of(
                observe(defaultConfiguration(), calendar(0L, 19, 0),
                        WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT)
        ));

        helper.assertTrue(open.businessObserved() && open.plantOpen(), "Production Order displays open plant state");
        helper.assertTrue(closed.businessObserved() && !closed.plantOpen(), "Production Order displays closed plant state");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void productionOrderDisplaysShiftAndDeadline(GameTestHelper helper) {
        BusinessRuntimeObservationSnapshot business = observe(defaultConfiguration(), calendar(0L, 10, 30),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);
        ProductionRunSnapshot run = runWithDeadline(ProductionRunStatus.READY, deadline(90));
        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run,
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                Optional.of(business)
        );

        helper.assertTrue(snapshot.activeShiftDisplayCode() == 1, "Production Order displays Day Shift");
        helper.assertTrue(snapshot.hasDeadline(), "Production Order displays deadline presence");
        helper.assertTrue(snapshot.deadlineDeltaMinutes() == 60, "Production Order displays deadline delta");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void processingWorkUnitsRemainTickBased(GameTestHelper helper) {
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(RUN_ID, PLAN_ID, 60L, 0L);
        runtime.markReady(1L);
        runtime.bindScheduledWork(SimulationWorkId.of("butchercraft:gametest_deadline_work"), 2L);
        runtime.beginOrResume(3L);
        runtime.advance(59L, 4L);

        BusinessRuntimeObservationSnapshot nextBusinessTime = observe(defaultConfiguration(), calendar(0L, 14, 0),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(nextBusinessTime.activeShift().map(BusinessScheduleBoundary::displayName)
                        .filter("Day Shift"::equals)
                        .isPresent(),
                "Business Runtime can observe a later shift time independently");
        helper.assertTrue(runtime.snapshot().currentWorkUnits() == 59L,
                "Production progress remains explicit tick-based work units");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 80)
    public static void shiftBoundaryDoesNotCreateAutomatedProductionAction(GameTestHelper helper) {
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(RUN_ID, PLAN_ID, 60L, 0L);
        runtime.setDeadline(deadline(240), 0L);
        BusinessRuntimeObservationSnapshot before = observe(defaultConfiguration(), calendar(0L, 14, 29),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);
        BusinessRuntimeObservationSnapshot after = observe(defaultConfiguration(), calendar(0L, 14, 30),
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT);

        helper.assertTrue(before.activeShift().map(BusinessScheduleBoundary::displayName)
                        .filter("Day Shift"::equals)
                        .isPresent(),
                "Fixture starts before the shift boundary");
        helper.assertTrue(after.activeShift().map(BusinessScheduleBoundary::displayName)
                        .filter("Evening Shift"::equals)
                        .isPresent(),
                "Fixture crosses to the next shift");
        helper.assertTrue(runtime.snapshot().status() == ProductionRunStatus.PLANNED,
                "Shift boundary observation does not schedule Production work");
        helper.assertTrue(runtime.snapshot().executionAttemptCount() == 0,
                "Shift boundary observation does not start Production execution");
        helper.succeed();
    }

    private static BusinessRuntimeCalendarConfiguration defaultConfiguration() {
        return DEFAULT_RUNTIME_CONFIGURATION;
    }

    private static BusinessRuntimeCalendarConfiguration configuration(
            BusinessOperatingSchedule schedule,
            BusinessShiftSet shifts
    ) {
        return new BusinessRuntimeCalendarConfiguration(
                true,
                BusinessRuntimeCalendarSchema.TIMEZONE_MODE_BUSINESS_CALENDAR,
                schedule,
                shifts,
                true,
                240,
                WORLD_TIME_CONFIGURATION.identity()
        );
    }

    private static BusinessRuntimeObservationSnapshot observe(
            BusinessRuntimeCalendarConfiguration configuration,
            BusinessCalendarSnapshot calendar,
            WorldTimeMovementClassification movementClassification
    ) {
        return BusinessRuntimeObservationSnapshot.observe(calendar, configuration, movementClassification);
    }

    private static ProductionDeadline deadline(int offsetMinutes) {
        return ProductionDeadline.target(
                RUN_ID,
                calendar(0L, 10, 0),
                DEFAULT_RUNTIME_CONFIGURATION.identity(),
                offsetMinutes,
                DEADLINE_SOURCE
        );
    }

    private static ProductionRunSnapshot runWithDeadline(
            ProductionRunStatus status,
            ProductionDeadline deadline
    ) {
        return new ProductionRunSnapshot(
                RUN_ID,
                PLAN_ID,
                status,
                1L,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.empty(),
                100L,
                0L,
                0,
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(ProductionWorkstationChain.beefPattyChain(RUN_ID)),
                Optional.of(deadline),
                Optional.empty(),
                Optional.empty(),
                1L,
                ProductionSchema.CURRENT_VERSION
        );
    }

    private static BusinessCalendarSnapshot calendar(long dayIndex, int hour, int minute) {
        long minuteOfDay = hour * 60L + minute;
        long dayTimeOfDay = minuteOfDay * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                / BusinessCalendarSnapshot.BUSINESS_MINUTES_PER_DAY;
        long observedDayTime = dayIndex * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                + dayTimeOfDay
                - BusinessCalendarSnapshot.MINECRAFT_VISIBLE_MIDNIGHT_OFFSET;
        return new BusinessCalendarSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                dayIndex,
                BusinessDayOfWeek.fromDayIndex(dayIndex),
                new BusinessTimeOfDay(hour, minute),
                dayTimeOfDay,
                dayTimeOfDay,
                BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS,
                "butchercraft:world_day/v1/minecraft:overworld/" + dayIndex,
                WORLD_TIME_CONFIGURATION.identity(),
                "minecraft:overworld",
                Math.max(0L, dayIndex),
                observedDayTime
        );
    }
}
