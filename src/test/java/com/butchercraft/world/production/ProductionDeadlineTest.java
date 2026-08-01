package com.butchercraft.world.production;

import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeConfigurationIdentity;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionDeadlineTest {
    private static final ProductionPlanId PLAN_ID = ProductionPlanId.of("test:deadline_plan");
    private static final ProductionRunId RUN_ID = ProductionRunId.forPlan(PLAN_ID);
    private static final BusinessRuntimeConfigurationIdentity CONFIGURATION_ID =
            BusinessRuntimeCalendarConfiguration.defaults(WorldTimeConfiguration.enabled(60).identity()).identity();
    private static final String SOURCE = "butchercraft:test_deadline";

    @Test
    void nonterminalDeadlineStatusFollowsCurrentBusinessCalendar() {
        ProductionDeadline deadline = deadline(120);

        assertEquals(ProductionDeadlineStatus.UPCOMING,
                deadline.evaluate(ProductionRunStatus.PLANNED, calendar(0L, 11, 59)).status());
        assertEquals(ProductionDeadlineStatus.DUE_NOW,
                deadline.evaluate(ProductionRunStatus.PLANNED, calendar(0L, 12, 0)).status());
        ProductionDeadline overdue = deadline.evaluate(ProductionRunStatus.PLANNED, calendar(0L, 12, 1));
        assertEquals(ProductionDeadlineStatus.OVERDUE, overdue.status());
        assertEquals(ProductionDeadlineStatus.UPCOMING,
                overdue.evaluate(ProductionRunStatus.PLANNED, calendar(0L, 11, 0)).status());
    }

    @Test
    void completionTimingIsTerminalAndStable() {
        assertTerminal(
                deadline(120).evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 11, 59)),
                ProductionDeadlineStatus.COMPLETED_EARLY,
                ProductionDeadlineCompletionTiming.EARLY
        );
        assertTerminal(
                deadline(120).evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 12, 0)),
                ProductionDeadlineStatus.COMPLETED_ON_TIME,
                ProductionDeadlineCompletionTiming.ON_TIME
        );
        ProductionDeadline late = deadline(120).evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 12, 1));
        assertTerminal(late, ProductionDeadlineStatus.COMPLETED_LATE, ProductionDeadlineCompletionTiming.LATE);
        assertEquals(late, late.evaluate(ProductionRunStatus.COMPLETED, calendar(0L, 11, 0)));
    }

    @Test
    void overdueDeadlineDoesNotCompleteOrFailRun() {
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(RUN_ID, PLAN_ID, 10L, 0L);

        runtime.setDeadline(deadline(60), 0L);
        runtime.evaluateDeadline(calendar(0L, 12, 0));

        assertEquals(ProductionRunStatus.PLANNED, runtime.snapshot().status());
        assertEquals(ProductionDeadlineStatus.OVERDUE, runtime.snapshot().deadline().orElseThrow().status());
    }

    @Test
    void noDeadlineRunIgnoresDeadlineEvaluation() {
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(RUN_ID, PLAN_ID, 10L, 0L);

        runtime.evaluateDeadline(calendar(0L, 12, 0));

        assertFalse(runtime.snapshot().deadline().isPresent());
        assertEquals(ProductionRunStatus.PLANNED, runtime.snapshot().status());
    }

    @Test
    void deadlineLocksAfterExecutionBeginsAndDuplicateAssignmentIsSafe() {
        ProductionRunRuntime runtime = ProductionRunRuntime.planned(RUN_ID, PLAN_ID, 10L, 0L);
        ProductionDeadline deadline = deadline(120);

        runtime.setDeadline(deadline, 0L);
        runtime.setDeadline(deadline, 0L);
        runtime.markReady(1L);
        runtime.bindScheduledWork(SimulationWorkId.of("test:deadline_work"), 2L);

        assertTrue(runtime.snapshot().deadline().orElseThrow().locked());
        assertThrows(IllegalStateException.class, () -> runtime.setDeadline(deadline(240), 3L));
    }

    @Test
    void cancelledDeadlineIsExplicitAndLockedWithoutCompletionTiming() {
        ProductionDeadline cancelled = deadline(120).evaluate(ProductionRunStatus.CANCELLED, calendar(0L, 11, 0));

        assertEquals(ProductionDeadlineStatus.CANCELLED, cancelled.status());
        assertTrue(cancelled.locked());
        assertFalse(cancelled.completionTiming().isPresent());
    }

    private static void assertTerminal(
            ProductionDeadline deadline,
            ProductionDeadlineStatus status,
            ProductionDeadlineCompletionTiming timing
    ) {
        assertEquals(status, deadline.status());
        assertEquals(timing, deadline.completionTiming().orElseThrow());
        assertTrue(deadline.locked());
    }

    private static ProductionDeadline deadline(int offsetMinutes) {
        return ProductionDeadline.target(RUN_ID, calendar(0L, 10, 0), CONFIGURATION_ID, offsetMinutes, SOURCE);
    }

    private static BusinessCalendarSnapshot calendar(long dayIndex, int hour, int minute) {
        long minuteOfDay = hour * 60L + minute;
        long dayTimeOfDay = minuteOfDay * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                / BusinessCalendarSnapshot.BUSINESS_MINUTES_PER_DAY;
        long observedDayTime = dayIndex * BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS
                + dayTimeOfDay - BusinessCalendarSnapshot.MINECRAFT_VISIBLE_MIDNIGHT_OFFSET;
        return new BusinessCalendarSnapshot(
                WorldTimeSchema.CURRENT_VERSION,
                dayIndex,
                BusinessDayOfWeek.fromDayIndex(dayIndex),
                new BusinessTimeOfDay(hour, minute),
                dayTimeOfDay,
                dayTimeOfDay,
                BusinessCalendarSnapshot.MINECRAFT_DAY_UNITS,
                "butchercraft:world_day/v1/minecraft:overworld/" + dayIndex,
                WorldTimeConfiguration.enabled(60).identity(),
                "minecraft:overworld",
                Math.max(0L, dayIndex),
                observedDayTime
        );
    }
}
