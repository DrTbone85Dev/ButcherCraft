package com.butchercraft.world.simulation.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSchedulerDefinitionTest {
    @Test
    void identifiersAreCanonicalImmutableAndOrdered() {
        assertEquals("test:alpha", SimulationWorkId.of("test:alpha").value());
        assertTrue(SimulationWorkId.of("test:alpha").compareTo(SimulationWorkId.of("test:beta")) < 0);
        assertThrows(IllegalArgumentException.class, () -> SimulationWorkId.of("not namespaced"));
        assertThrows(IllegalArgumentException.class, () -> SimulationStageId.of("TEST:UPPER"));
        assertThrows(IllegalArgumentException.class, () -> SimulationWorkTypeId.of("missing_namespace"));
    }

    @Test
    void builtInStagesHaveStableExplicitOrderAndSameTickPolicy() {
        SimulationStageRegistry registry = SimulationStageRegistry.builtIn();

        assertEquals(List.of(100, 200, 300, 400, 500, 600),
                registry.definitions().stream().map(SimulationStageDefinition::executionOrder).toList());
        assertEquals(List.of(
                BuiltInSimulationStages.TICK_PREPARATION,
                BuiltInSimulationStages.OBLIGATION_EVALUATION,
                BuiltInSimulationStages.PLANNING,
                BuiltInSimulationStages.EXECUTION,
                BuiltInSimulationStages.OBSERVATION,
                BuiltInSimulationStages.TICK_FINALIZATION
        ), registry.definitions().stream().map(SimulationStageDefinition::id).toList());
        assertTrue(registry.find(BuiltInSimulationStages.EXECUTION).orElseThrow().allowsSameTickEnqueue());
    }

    @Test
    void stageRegistryRejectsDuplicateIdentityOrderAndInvalidDefinition() {
        SimulationStageDefinition first = stage("test:first", 10);
        assertThrows(IllegalArgumentException.class, () -> SimulationStageRegistry.of(List.of(first, first)));
        assertThrows(IllegalArgumentException.class, () -> SimulationStageRegistry.of(List.of(
                first, stage("test:second", 10)
        )));
        assertThrows(IllegalArgumentException.class, () -> new SimulationStageDefinition(
                SimulationStageId.of("test:bad"), "Bad", -1, StageFailurePolicy.CONTINUE_STAGE,
                false, SchedulerSchema.CURRENT_VERSION
        ));
    }

    @Test
    void payloadIsCanonicalBoundedAndDefensivelyCopied() {
        List<WorkPayloadEntry> mutable = new ArrayList<>();
        mutable.add(WorkPayloadEntry.decimal("test:quantity", "10.5000"));
        mutable.add(WorkPayloadEntry.booleanValue("test:enabled", true));
        WorkPayload payload = new WorkPayload(mutable);
        mutable.clear();

        assertEquals(List.of("test:enabled", "test:quantity"),
                payload.entries().stream().map(WorkPayloadEntry::key).toList());
        assertEquals("10.5", payload.find("test:quantity").orElseThrow().canonicalValue());
        assertThrows(UnsupportedOperationException.class,
                () -> payload.entries().add(WorkPayloadEntry.string("test:new", "value")));
        assertThrows(IllegalArgumentException.class, () -> new WorkPayload(List.of(
                WorkPayloadEntry.string("test:key", "a"), WorkPayloadEntry.string("test:key", "b")
        )));
        assertThrows(IllegalArgumentException.class,
                () -> WorkPayloadEntry.string("test:large", "x".repeat(2_049)));
    }

    @Test
    void retryPoliciesUseOnlyExactSimulationTicks() {
        assertEquals(11L, RetryPolicy.nextTick().nextEligibleTick(10, 1, OptionalLong.empty()));
        assertEquals(18L, RetryPolicy.fixedInterval(8).nextEligibleTick(10, 1, OptionalLong.empty()));
        assertEquals(26L, RetryPolicy.exponential(4).nextEligibleTick(10, 3, OptionalLong.empty()));
        assertThrows(IllegalArgumentException.class, () -> RetryPolicy.fixedInterval(0));
        assertThrows(IllegalStateException.class,
                () -> RetryPolicy.never().nextEligibleTick(10, 1, OptionalLong.empty()));
        assertThrows(ArithmeticException.class,
                () -> RetryPolicy.nextTick().nextEligibleTick(Long.MAX_VALUE, 1, OptionalLong.empty()));
    }

    @Test
    void workRequestRejectsInvalidTickExpirationAttemptsAndReferences() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationWorkRequest(
                SimulationWorkId.of("test:invalid"), SchedulerTestFixtures.TYPE,
                BuiltInSimulationStages.EXECUTION, -1, WorkPriority.NORMAL,
                WorkOrigin.of("test:scheduler", 0, "test:test"), WorkPayload.empty(), RetryPolicy.never(),
                1, OptionalLong.empty(), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SimulationWorkRequest(
                SimulationWorkId.of("test:invalid"), SchedulerTestFixtures.TYPE,
                BuiltInSimulationStages.EXECUTION, 10, WorkPriority.NORMAL,
                WorkOrigin.of("test:scheduler", 0, "test:test"), WorkPayload.empty(), RetryPolicy.never(),
                1, OptionalLong.of(9), List.of()
        ));
        assertThrows(IllegalArgumentException.class, () -> new SimulationWorkRequest(
                SimulationWorkId.of("test:invalid"), SchedulerTestFixtures.TYPE,
                BuiltInSimulationStages.EXECUTION, 10, WorkPriority.NORMAL,
                WorkOrigin.of("test:scheduler", 0, "test:test"), WorkPayload.empty(), RetryPolicy.never(),
                0, OptionalLong.empty(), List.of()
        ));
    }

    private static SimulationStageDefinition stage(String id, int order) {
        return new SimulationStageDefinition(
                SimulationStageId.of(id), id, order, StageFailurePolicy.CONTINUE_STAGE, true,
                SchedulerSchema.CURRENT_VERSION
        );
    }
}
