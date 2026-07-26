package com.butchercraft.world.planning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningCadenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void periodicCadenceDoesNotExecuteFullCycleEveryTick() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningManager manager = PlanningTestFixtures.manager(context);

        PlanningCadenceExecutionResult first = manager.executeCadenceCycle(20L, 1_000);
        PlanningCadenceExecutionResult second = manager.executeCadenceCycle(21L, 1_000);

        assertTrue(first.cycleExecuted());
        assertFalse(second.cycleExecuted());
        assertEquals(1, manager.cycles().size());
        assertEquals(1_220L, first.nextEligibleTick());
        assertEquals(1_220L, second.nextEligibleTick());
        assertTrue(first.cycle().orElseThrow().cadenceEvidence().isPresent());
    }

    @Test
    void sourceOwnedTriggerMakesPlanningEligibleAfterMinimumSeparation() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningManager manager = PlanningTestFixtures.manager(context);
        manager.executeCadenceCycle(20L, 1_000);

        PlanningTriggerRecord trigger = orderTrigger(100L, "test:planning_order/freshness_1");
        PlanningTriggerPublicationResult publication = manager.publishTrigger(trigger, 100L);
        PlanningTriggerPublicationResult duplicate = manager.publishTrigger(trigger, 100L);
        PlanningCadenceExecutionResult triggered = manager.executeCadenceCycle(101L, 1_000);

        assertTrue(publication.accepted());
        assertEquals(101L, publication.nextEligibleTick().orElseThrow());
        assertEquals(PlanningTriggerPublicationResult.Status.DUPLICATE, duplicate.status());
        assertTrue(triggered.cycleExecuted());
        PlanningCycleCadenceEvidence evidence = triggered.cycle().orElseThrow()
                .cadenceEvidence().orElseThrow();
        assertEquals(1, evidence.consumedTriggers().size());
        assertEquals(trigger.triggerIdentity(), evidence.consumedTriggers().getFirst().triggerIdentity());
        assertEquals(1_301L, triggered.nextEligibleTick());
    }

    @Test
    void conflictingTriggerIdentityIsRejectedBeforePublication() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningManager manager = PlanningTestFixtures.manager(context);
        PlanningTriggerRecord trigger = orderTrigger(100L, "test:planning_order/freshness_1");
        PlanningTriggerRecord conflict = new PlanningTriggerRecord(
                trigger.schemaVersion(),
                trigger.triggerIdentity(),
                PlanningValidation.derivedId("test_planning_trigger_content", "different"),
                trigger.sourceOwner(),
                trigger.authoritativeSimulationTick(),
                trigger.triggerType(),
                trigger.sourceReference(),
                trigger.sourceFreshnessIdentity(),
                trigger.payloadMetadata()
        );

        assertTrue(manager.publishTrigger(trigger, 100L).accepted());
        PlanningTriggerPublicationResult result = manager.publishTrigger(conflict, 100L);

        assertFalse(result.accepted());
        assertEquals(PlanningFailureCode.PLANNING_TRIGGER_IDENTITY_CONFLICT,
                result.failureCode().orElseThrow());
    }

    @Test
    void loadedOverdueCadenceSchedulesOneRecoveryCycleWithoutBurstCatchUp() {
        PlanningTestFixtures.Context context = PlanningTestFixtures.context();
        PlanningTestFixtures.submitAcceptedOrder(context, 3L);
        PlanningManager manager = PlanningTestFixtures.manager(context);
        manager.executeCycle(20L);
        PlanningStorage storage = storage(context);
        storage.save(manager);

        PlanningManager loaded = storage.load();
        long next = loaded.nextCadenceEligibilityTick(5_000L);
        PlanningCadenceExecutionResult overdue = loaded.executeCadenceCycle(next, 1_000);

        assertTrue(overdue.cycleExecuted());
        assertEquals(2, loaded.cycles().size());
        assertEquals(5_001L, overdue.cycle().orElseThrow().simulationTick());
        assertEquals(6_201L, overdue.nextEligibleTick());
    }

    private PlanningStorage storage(PlanningTestFixtures.Context context) {
        return new PlanningStorage(
                temporaryDirectory, context.dependencies(), PlanningSelectionPolicy.standard(),
                PlanningExecutionBudget.standard()
        );
    }

    private static PlanningTriggerRecord orderTrigger(long tick, String freshnessIdentity) {
        return PlanningTriggerRecord.sourceOwned(
                "butchercraft:orders",
                tick,
                PlanningTriggerType.ORDER_INTENT,
                PlanningTestFixtures.ORDER.value(),
                freshnessIdentity,
                Map.of("butchercraft:order_id", PlanningTestFixtures.ORDER.value())
        );
    }
}
