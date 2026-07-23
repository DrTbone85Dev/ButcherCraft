package com.butchercraft.world.simulation.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSchedulerManagerTest {
    @Test
    void handlerRegistryRejectsDuplicatesAndSortsDiagnostics() {
        SimulationWorkTypeId second = SimulationWorkTypeId.of("test:z_type");
        SimulationWorkHandler firstHandler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SimulationWorkHandler secondHandler = SchedulerTestFixtures.handler(
                second, work -> WorkValidationResult.acceptedResult(), context ->
                        SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1)
        );
        SimulationWorkHandlerRegistry registry = new SimulationWorkHandlerRegistry(List.of(secondHandler, firstHandler));

        assertEquals(List.of(SchedulerTestFixtures.TYPE, second),
                registry.handlers().stream().map(SimulationWorkHandler::supportedTypeId).toList());
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationWorkHandlerRegistry(List.of(firstHandler, firstHandler)));
    }

    @Test
    void managerAssignsMonotonicSequenceAndProvidesDeterministicIndexes() {
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(
                SchedulerTestFixtures.handler(context ->
                        SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1)), 0
        );
        SimulationWorkRequest first = SchedulerTestFixtures.request(
                "test:first", BuiltInSimulationStages.EXECUTION, 0, 2
        );
        SimulationWorkRequest second = SchedulerTestFixtures.request(
                "test:second", BuiltInSimulationStages.PLANNING, 0, 1
        );

        assertTrue(manager.submit(first, 0).accepted());
        assertTrue(manager.submit(second, 0).accepted());

        assertEquals(List.of(0L, 1L), manager.registry().definitions().stream()
                .map(ScheduledSimulationWork::authoritativeSubmissionSequence).toList());
        assertEquals(List.of(SimulationWorkId.of("test:second")), manager.findDueAt(1).stream()
                .map(ScheduledSimulationWork::id).toList());
        assertEquals(2, manager.findByType(SchedulerTestFixtures.TYPE).size());
        assertEquals(1, manager.findByStage(BuiltInSimulationStages.PLANNING).size());
        assertEquals(2, manager.findByOriginSubsystem("test:scheduler").size());
        assertEquals(2, manager.findByStatus(SimulationWorkStatus.SCHEDULED).size());
        assertThrows(UnsupportedOperationException.class,
                () -> manager.findDueAt(2).add(manager.registry().definitions().getFirst()));
    }

    @Test
    void duplicateUnknownInvalidAndOverflowingSubmissionsFailAtomically() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:duplicate", BuiltInSimulationStages.EXECUTION, 0, 1
        );
        assertTrue(manager.submit(request, 0).accepted());
        assertEquals(WorkFailureCode.DUPLICATE_WORK_ID,
                manager.submit(request, 0).failureCode().orElseThrow());
        assertEquals(1, manager.registry().size());

        SimulationWorkRequest badTick = SchedulerTestFixtures.request(
                "test:bad_tick", BuiltInSimulationStages.EXECUTION, 0, 1
        );
        assertEquals(WorkFailureCode.INVALID_TICK,
                manager.submit(badTick, 1).failureCode().orElseThrow());

        SimulationSchedulerManager overflow = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), new SimulationWorkHandlerRegistry(List.of(handler)),
                SimulationSchedulerRegistry.empty(), List.of(), Long.MAX_VALUE, 0
        );
        assertEquals(WorkFailureCode.INTERNAL_INVARIANT_VIOLATION,
                overflow.submit(SchedulerTestFixtures.request(
                        "test:overflow", BuiltInSimulationStages.EXECUTION, 0, 1
                ), 0).failureCode().orElseThrow());
        assertEquals(0, overflow.registry().size());
    }

    @Test
    void cancellationExpirationAndRuntimeSnapshotsAreExplicit() {
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(
                SchedulerTestFixtures.handler(context ->
                        SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1)), 0
        );
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:cancel", BuiltInSimulationStages.EXECUTION, 0, 3
        );
        manager.submit(request, 0);
        SimulationWorkRuntime originalSnapshot = manager.runtimeFor(request.id()).orElseThrow();

        assertTrue(manager.cancel(request.id(), 1, "cancelled by test").successful());
        assertEquals(SimulationWorkStatus.CANCELLED, manager.runtimeFor(request.id()).orElseThrow().status());
        assertEquals(SimulationWorkStatus.SCHEDULED, originalSnapshot.status());
        assertFalse(manager.cancel(request.id(), 2, "again").successful());

        SimulationWorkRequest expiring = new SimulationWorkRequest(
                SimulationWorkId.of("test:expires"), SchedulerTestFixtures.TYPE,
                BuiltInSimulationStages.EXECUTION, 1, WorkPriority.NORMAL,
                WorkOrigin.of("test:scheduler", 0, "test:test"), WorkPayload.empty(), RetryPolicy.never(),
                1, java.util.OptionalLong.of(1), List.of()
        );
        manager.submit(expiring, 0);
        EligibilityUpdate update = manager.promoteDue(2, 10);
        assertEquals(1, update.expired());
        assertEquals(SimulationWorkStatus.EXPIRED, manager.runtimeFor(expiring.id()).orElseThrow().status());
    }

    @Test
    void loadedRegistryRejectsDuplicateSequencesMissingRuntimeAndUnknownHandler() {
        ScheduledSimulationWork first = ScheduledSimulationWork.fromRequest(
                SchedulerTestFixtures.request("test:a", BuiltInSimulationStages.EXECUTION, 0, 1), 0, 0
        );
        ScheduledSimulationWork duplicateSequence = ScheduledSimulationWork.fromRequest(
                SchedulerTestFixtures.request("test:b", BuiltInSimulationStages.EXECUTION, 0, 1), 0, 0
        );
        assertThrows(IllegalArgumentException.class,
                () -> SimulationSchedulerRegistry.of(List.of(first, duplicateSequence)));

        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        assertThrows(IllegalArgumentException.class, () -> new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), new SimulationWorkHandlerRegistry(List.of(handler)),
                SimulationSchedulerRegistry.of(List.of(first)), List.of(), 1, 0
        ));
        assertThrows(IllegalArgumentException.class, () -> new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), SimulationWorkHandlerRegistry.empty(),
                SimulationSchedulerRegistry.of(List.of(first)), List.of(SimulationWorkRuntime.scheduled(first)), 1, 0
        ));
    }
}
