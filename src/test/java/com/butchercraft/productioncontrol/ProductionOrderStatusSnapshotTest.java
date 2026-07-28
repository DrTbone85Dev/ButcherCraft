package com.butchercraft.productioncontrol;

import com.butchercraft.workstation.WorkstationState;
import com.butchercraft.workstation.WorkstationFailureCode;
import com.butchercraft.world.business.runtime.BusinessRuntimeCalendarConfiguration;
import com.butchercraft.world.business.runtime.BusinessRuntimeObservationSnapshot;
import com.butchercraft.world.production.ProductionDeadline;
import com.butchercraft.world.production.ProductionDeadlineStatus;
import com.butchercraft.world.production.ProductionChainCompletionEvidence;
import com.butchercraft.world.production.ProductionChainStepStatus;
import com.butchercraft.world.production.ProductionFailureCode;
import com.butchercraft.world.production.ProductionPlanId;
import com.butchercraft.world.production.ProductionRunId;
import com.butchercraft.world.production.ProductionRunSnapshot;
import com.butchercraft.world.production.ProductionRunStatus;
import com.butchercraft.world.production.ProductionSchema;
import com.butchercraft.world.production.ProductionWorkstationChain;
import com.butchercraft.world.production.ProductionWorkstationChainStep;
import com.butchercraft.world.production.ProductionWorkstationCompletionEvidence;
import com.butchercraft.world.simulation.time.BusinessCalendarSnapshot;
import com.butchercraft.world.simulation.time.BusinessDayOfWeek;
import com.butchercraft.world.simulation.time.BusinessTimeOfDay;
import com.butchercraft.world.simulation.time.WorldTimeConfiguration;
import com.butchercraft.world.simulation.time.WorldTimeMovementClassification;
import com.butchercraft.world.simulation.time.WorldTimeSchema;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionOrderStatusSnapshotTest {
    private static final ProductionPlanId PLAN_ID = ProductionPlanId.of("butchercraft:manual_beef_patties/pabc/t1/n1");
    private static final ProductionRunId RUN_ID = ProductionRunId.forPlan(PLAN_ID);
    private static final String GRINDER_ID = "butchercraft:workstation/grinder/minecraft/overworld/1/2/3";
    private static final String PATTY_FORMER_ID =
            "butchercraft:workstation/patty_former/minecraft/overworld/4/5/6";
    private static final String GRIND_BEEF = "butchercraft:grind_beef";
    private static final String FORM_BEEF_PATTIES = "butchercraft:form_beef_patties";
    private static final String EXECUTION_SUCCEEDED = "butchercraft:execution_status/succeeded";

    @Test
    void emptyAndStaleSnapshotsExposeNonAuthoritativePlayerGuidance() {
        ProductionOrderStatusSnapshot empty = ProductionOrderStatusSnapshot.empty();
        ProductionOrderStatusSnapshot stale = ProductionOrderStatusSnapshot.stale();

        assertFalse(empty.hasRun());
        assertEquals(ProductionOrderNextAction.CREATE_RUN, empty.nextAction());
        assertTrue(stale.staleReference());
        assertEquals(ProductionOrderNextAction.STALE_REFERENCE, stale.nextAction());
    }

    @Test
    void newlyCreatedRunAsksForGrinderAssignment() {
        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.READY, ProductionWorkstationChain.beefPattyChain(RUN_ID)),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        );

        assertTrue(snapshot.hasRun());
        assertEquals(ProductionOrderNextAction.ASSIGN_GRINDER, snapshot.nextAction());
        assertTrue(snapshot.canCancel());
    }

    @Test
    void assignedIdleGrinderAsksForBeefTrim() {
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        chain = chain.withStepAssignment(grinderStep(chain).stepIdentity(), GRINDER_ID);

        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.READY, chain),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 0, WorkstationState.IDLE),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        );

        assertEquals(ProductionOrderNextAction.LOAD_BEEF_TRIM, snapshot.nextAction());
        assertTrue(snapshot.grinderAssigned());
        assertTrue(snapshot.canCancel());
    }

    @Test
    void runningGrinderAsksPlayerToWaitAndDisablesCancellation() {
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String stepId = grinderStep(chain).stepIdentity();
        chain = chain.withStepAssignment(stepId, GRINDER_ID)
                .withStepExecution(stepId, GRINDER_ID, GRIND_BEEF, executionId("1"));

        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.RUNNING, chain),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 42, WorkstationState.PROCESSING),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        );

        assertEquals(ProductionOrderNextAction.WAIT_FOR_GRINDER, snapshot.nextAction());
        assertEquals(42, snapshot.grinderProgressPercent());
        assertFalse(snapshot.canCancel());
    }

    @Test
    void completedGrinderWithoutPattyFormerAsksForPattyFormerAssignment() {
        ProductionWorkstationChain chain = grinderCompleteChain();

        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.READY, chain, 50L),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 100, WorkstationState.COMPLETE),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        );

        assertEquals(ProductionOrderNextAction.ASSIGN_PATTY_FORMER, snapshot.nextAction());
    }

    @Test
    void completedGrinderWithAssignedPattyFormerAsksForManualTransfer() {
        ProductionWorkstationChain chain = grinderCompleteChain();
        chain = chain.withStepAssignment(pattyStep(chain).stepIdentity(), PATTY_FORMER_ID);

        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.READY, chain, 50L),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 100, WorkstationState.COMPLETE),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 0, WorkstationState.IDLE)
        );

        assertEquals(ProductionOrderNextAction.MOVE_GROUND_BEEF, snapshot.nextAction());
        assertTrue(snapshot.pattyFormerAssigned());
    }

    @Test
    void runningPattyFormerAsksPlayerToWait() {
        ProductionWorkstationChain chain = grinderCompleteChain();
        String stepId = pattyStep(chain).stepIdentity();
        chain = chain.withStepAssignment(stepId, PATTY_FORMER_ID)
                .withStepExecution(stepId, PATTY_FORMER_ID, FORM_BEEF_PATTIES, executionId("2"));

        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.RUNNING, chain, 75L),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 100, WorkstationState.COMPLETE),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 64, WorkstationState.PROCESSING)
        );

        assertEquals(ProductionOrderNextAction.WAIT_FOR_PATTY_FORMER, snapshot.nextAction());
        assertEquals(64, snapshot.pattyFormerProgressPercent());
        assertFalse(snapshot.canCancel());
    }

    @Test
    void completedChainGuidesPlayerToCollectPatties() {
        ProductionWorkstationChain chain = completeChain();

        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.COMPLETED, chain, 100L),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 100, WorkstationState.COMPLETE),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 100, WorkstationState.COMPLETE)
        );

        assertEquals(ProductionOrderNextAction.COLLECT_BEEF_PATTIES, snapshot.nextAction());
        assertFalse(snapshot.canCancel());
    }

    @Test
    void failureCancellationUnknownOutcomeAndMissingWorkstationsAreVisible() {
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String stepId = grinderStep(chain).stepIdentity();
        ProductionWorkstationChain failedChain = chain.withFailure(stepId, ProductionChainStepStatus.FAILED);
        ProductionWorkstationChain unknownChain = chain.withFailure(stepId, ProductionChainStepStatus.UNKNOWN_OUTCOME);
        ProductionWorkstationChain assigned = chain.withStepAssignment(stepId, GRINDER_ID);

        assertEquals(ProductionOrderNextAction.FAILED, ProductionOrderStatusSnapshot.fromRun(
                failedRun(failedChain),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        ).nextAction());
        assertEquals(ProductionOrderNextAction.UNKNOWN_OUTCOME, ProductionOrderStatusSnapshot.fromRun(
                failedRun(unknownChain),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        ).nextAction());
        assertEquals(ProductionOrderNextAction.CANCELLED, ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.CANCELLED, chain.cancelledBeforeFirstEffect()),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        ).nextAction());

        ProductionOrderStatusSnapshot missing = ProductionOrderStatusSnapshot.fromRun(
                run(ProductionRunStatus.READY, assigned),
                ProductionOrderStatusSnapshot.WorkstationObservation.missingWorkstation(),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        );
        assertTrue(missing.grinderMissing());
    }

    @Test
    void outputBlockedWorkstationsExposeSpecificPlayerGuidance() {
        ProductionWorkstationChain grinderChain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        grinderChain = grinderChain.withStepAssignment(grinderStep(grinderChain).stepIdentity(), GRINDER_ID);
        ProductionOrderStatusSnapshot grinderBlocked = ProductionOrderStatusSnapshot.fromRun(
                failedRun(grinderChain.withFailure(
                        grinderStep(grinderChain).stepIdentity(),
                        ProductionChainStepStatus.FAILED
                )),
                new ProductionOrderStatusSnapshot.WorkstationObservation(
                        false,
                        0,
                        WorkstationState.BLOCKED,
                        Optional.of(WorkstationFailureCode.OUTPUT_OCCUPIED)
                ),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned()
        );

        ProductionWorkstationChain pattyChain = grinderCompleteChain();
        pattyChain = pattyChain.withStepAssignment(pattyStep(pattyChain).stepIdentity(), PATTY_FORMER_ID);
        ProductionOrderStatusSnapshot pattyBlocked = ProductionOrderStatusSnapshot.fromRun(
                failedRun(pattyChain.withFailure(
                        pattyStep(pattyChain).stepIdentity(),
                        ProductionChainStepStatus.FAILED
                )),
                new ProductionOrderStatusSnapshot.WorkstationObservation(false, 100, WorkstationState.COMPLETE),
                new ProductionOrderStatusSnapshot.WorkstationObservation(
                        false,
                        0,
                        WorkstationState.BLOCKED,
                        Optional.of(WorkstationFailureCode.OUTPUT_OCCUPIED)
                )
        );

        assertEquals(ProductionOrderNextAction.CLEAR_GRINDER_OUTPUT, grinderBlocked.nextAction());
        assertEquals(ProductionOrderNextAction.CLEAR_PATTY_FORMER_OUTPUT, pattyBlocked.nextAction());
    }

    @Test
    void businessAndDeadlineFieldsExposeDisplayValuesOnly() {
        BusinessRuntimeCalendarConfiguration configuration =
                BusinessRuntimeCalendarConfiguration.defaults(WorldTimeConfiguration.enabled(60).identity());
        BusinessRuntimeObservationSnapshot business = BusinessRuntimeObservationSnapshot.observe(
                calendar(0L, 10, 30),
                configuration,
                WorldTimeMovementClassification.NORMAL_SCALED_ADVANCEMENT
        );
        ProductionDeadline deadline = ProductionDeadline.target(
                RUN_ID,
                calendar(0L, 10, 0),
                configuration.identity(),
                120,
                "butchercraft:test_deadline"
        );
        ProductionOrderStatusSnapshot snapshot = ProductionOrderStatusSnapshot.fromRun(
                runWithDeadline(ProductionRunStatus.READY, ProductionWorkstationChain.beefPattyChain(RUN_ID), deadline),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                ProductionOrderStatusSnapshot.WorkstationObservation.unassigned(),
                Optional.of(business)
        );

        assertTrue(snapshot.businessObserved());
        assertTrue(snapshot.plantOpen());
        assertEquals(BusinessDayOfWeek.MONDAY.ordinal(), snapshot.businessDayOfWeekOrdinal());
        assertEquals(10, snapshot.businessHour());
        assertEquals(30, snapshot.businessMinute());
        assertEquals(1, snapshot.activeShiftDisplayCode());
        assertEquals(2, snapshot.nextShiftDisplayCode());
        assertTrue(snapshot.hasDeadline());
        assertEquals(ProductionDeadlineStatus.UPCOMING, snapshot.deadlineStatus());
        assertEquals(BusinessDayOfWeek.MONDAY.ordinal(), snapshot.deadlineDayOfWeekOrdinal());
        assertEquals(12, snapshot.deadlineHour());
        assertEquals(0, snapshot.deadlineMinute());
        assertEquals(90, snapshot.deadlineDeltaMinutes());
    }

    private static ProductionWorkstationChain grinderCompleteChain() {
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String stepId = grinderStep(chain).stepIdentity();
        return chain.withStepAssignment(stepId, GRINDER_ID)
                .withStepExecution(stepId, GRINDER_ID, GRIND_BEEF, executionId("1"))
                .withStepCompletion(stepId, evidence(GRINDER_ID, GRIND_BEEF, executionId("1"), 10L));
    }

    private static ProductionWorkstationChain completeChain() {
        ProductionWorkstationChain chain = grinderCompleteChain();
        String stepId = pattyStep(chain).stepIdentity();
        chain = chain.withStepAssignment(stepId, PATTY_FORMER_ID)
                .withStepExecution(stepId, PATTY_FORMER_ID, FORM_BEEF_PATTIES, executionId("2"))
                .withStepCompletion(stepId, evidence(PATTY_FORMER_ID, FORM_BEEF_PATTIES, executionId("2"), 20L));
        return chain.withCompletionEvidence(ProductionChainCompletionEvidence.published(RUN_ID, chain, 20L));
    }

    private static ProductionRunSnapshot failedRun(ProductionWorkstationChain chain) {
        return new ProductionRunSnapshot(
                RUN_ID,
                PLAN_ID,
                ProductionRunStatus.FAILED,
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
                Optional.of(chain),
                Optional.of(ProductionFailureCode.WORKSTATION_FAILED),
                Optional.of("Workstation failed"),
                1L,
                ProductionSchema.CURRENT_VERSION
        );
    }

    private static ProductionRunSnapshot run(ProductionRunStatus status, ProductionWorkstationChain chain) {
        return run(status, chain, 0L);
    }

    private static ProductionRunSnapshot run(
            ProductionRunStatus status,
            ProductionWorkstationChain chain,
            long currentWorkUnits
    ) {
        return new ProductionRunSnapshot(
                RUN_ID,
                PLAN_ID,
                status,
                1L,
                OptionalLong.empty(),
                OptionalLong.empty(),
                status == ProductionRunStatus.COMPLETED ? OptionalLong.of(20L) : OptionalLong.empty(),
                100L,
                currentWorkUnits,
                0,
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(chain),
                Optional.empty(),
                Optional.empty(),
                1L,
                ProductionSchema.CURRENT_VERSION
        );
    }

    private static ProductionRunSnapshot runWithDeadline(
            ProductionRunStatus status,
            ProductionWorkstationChain chain,
            ProductionDeadline deadline
    ) {
        return new ProductionRunSnapshot(
                RUN_ID,
                PLAN_ID,
                status,
                1L,
                OptionalLong.empty(),
                OptionalLong.empty(),
                status == ProductionRunStatus.COMPLETED ? OptionalLong.of(20L) : OptionalLong.empty(),
                100L,
                0L,
                0,
                OptionalLong.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(chain),
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

    private static ProductionWorkstationCompletionEvidence evidence(
            String workstationIdentity,
            String processIdentity,
            String executionIdentity,
            long tick
    ) {
        return ProductionWorkstationCompletionEvidence.published(
                RUN_ID,
                workstationIdentity,
                processIdentity,
                executionIdentity,
                EXECUTION_SUCCEEDED,
                "butchercraft:workstation_result/v1/" + "a".repeat(64),
                "sha256:" + "b".repeat(64),
                "butchercraft:execution_result_evidence/v1/" + "c".repeat(64),
                "sha256:" + "d".repeat(64),
                tick
        );
    }

    private static ProductionWorkstationChainStep grinderStep(ProductionWorkstationChain chain) {
        return chain.steps().getFirst();
    }

    private static ProductionWorkstationChainStep pattyStep(ProductionWorkstationChain chain) {
        return chain.steps().get(1);
    }

    private static String executionId(String seed) {
        return "butchercraft:execution_operation/v1/" + seed.repeat(64);
    }
}
