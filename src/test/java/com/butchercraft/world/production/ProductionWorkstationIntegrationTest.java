package com.butchercraft.world.production;

import com.butchercraft.world.production.persistence.ProductionPersistenceSnapshot;
import com.butchercraft.world.production.persistence.ProductionStorage;
import com.butchercraft.world.production.scheduler.ProductionSimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionWorkstationIntegrationTest {
    private static final ProductionRunId RUN_ID = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
    private static final String GRINDER_ID = "butchercraft:workstation/grinder/minecraft/overworld/1/2/3";
    private static final String OTHER_GRINDER_ID = "butchercraft:workstation/grinder/minecraft/overworld/4/5/6";
    private static final String PROCESS_IDENTITY = "butchercraft:grind_beef";
    private static final String OTHER_PROCESS_IDENTITY = "butchercraft:grind_pork";
    private static final String EXECUTION_OPERATION_ID = "butchercraft:execution_operation/v1/" + "a".repeat(64);
    private static final String OTHER_EXECUTION_OPERATION_ID = "butchercraft:execution_operation/v1/" + "b".repeat(64);
    private static final String OWNER_RESULT_ID = "butchercraft:workstation_result/v1/" + "c".repeat(64);
    private static final String OWNER_RESULT_DIGEST = "sha256:" + "d".repeat(64);
    private static final String EXECUTION_RESULT_ID =
            "butchercraft:execution_result_evidence/v1/" + "e".repeat(64);
    private static final String EXECUTION_RESULT_DIGEST = "sha256:" + "f".repeat(64);
    private static final String EXECUTION_SUCCEEDED = "butchercraft:execution_status/succeeded";

    @TempDir
    Path temporaryDirectory;

    @Test
    void productionAssignsGrinderWithoutSchedulingTransactionBackedWork() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);

        ProductionOperationResult<ProductionRunSnapshot> assigned =
                manager.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L);

        assertTrue(assigned.accepted());
        ProductionRunSnapshot run = assigned.value().orElseThrow();
        assertEquals(ProductionRunStatus.READY, run.status());
        assertTrue(run.workstationAssignment().isPresent());
        assertEquals(GRINDER_ID, run.workstationAssignment().orElseThrow().workstationIdentity());
        assertEquals(PROCESS_IDENTITY, run.workstationAssignment().orElseThrow().processIdentity());
        assertTrue(run.scheduledWorkId().isEmpty());
        assertTrue(run.completionTransactionId().isEmpty());
        assertEquals(0, context.dependencies().transactionManager().size());

        ProductionOperationResult<ProductionRunSnapshot> scheduled =
                manager.schedule(RUN_ID, scheduler(manager), 0L);
        assertFalse(scheduled.accepted());
        assertEquals(ProductionFailureCode.WORK_ALREADY_BOUND, scheduled.failures().getFirst().code());
    }

    @Test
    void productionCompletesOnlyAfterObservedGrinderOwnerResultAndExecutionEvidence() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        assertTrue(manager.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());

        ProductionOperationResult<ProductionRunSnapshot> bound = manager.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        );

        assertTrue(bound.accepted());
        assertEquals(ProductionRunStatus.RUNNING, bound.value().orElseThrow().status());
        assertEquals(1, bound.value().orElseThrow().executionAttemptCount());
        assertTrue(manager.findByWorkstationExecution(EXECUTION_OPERATION_ID).isPresent());

        ProductionOperationResult<ProductionRunSnapshot> completed = complete(manager, 2L);

        assertTrue(completed.accepted());
        ProductionRunSnapshot run = completed.value().orElseThrow();
        assertEquals(ProductionRunStatus.COMPLETED, run.status());
        assertEquals(run.requiredWorkUnits(), run.currentWorkUnits());
        assertTrue(run.completionTransactionId().isEmpty());
        ProductionWorkstationCompletionEvidence evidence =
                run.workstationAssignment().orElseThrow().completionEvidence().orElseThrow();
        assertEquals(RUN_ID, evidence.runId());
        assertEquals(EXECUTION_OPERATION_ID, evidence.executionOperationIdentity());
        assertEquals(EXECUTION_SUCCEEDED, evidence.executionTerminalStatus());
        assertTrue(evidence.digestMatches());
        assertTrue(manager.findByWorkstationCompletionEvidence(evidence.evidenceIdentity()).isPresent());
        assertEquals(0, context.dependencies().transactionManager().size());
    }

    @Test
    void duplicateWorkstationObservationsReturnExistingAuthoritativeRun() {
        ProductionManager manager = ProductionTestFixtures.populatedManager(ProductionTestFixtures.context());

        ProductionRunSnapshot assigned = manager.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L)
                .value().orElseThrow();
        assertEquals(assigned, manager.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L)
                .value().orElseThrow());

        ProductionRunSnapshot bound = manager.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        ).value().orElseThrow();
        assertEquals(bound, manager.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                3L
        ).value().orElseThrow());

        ProductionRunSnapshot completed = complete(manager, 4L).value().orElseThrow();
        assertEquals(completed, complete(manager, 4L).value().orElseThrow());
    }

    @Test
    void conflictingDuplicateWorkstationIdentityIsRejected() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager first = ProductionTestFixtures.populatedManager(context);
        assertTrue(first.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());
        assertFalse(first.assignWorkstation(RUN_ID, OTHER_GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());
        assertTrue(first.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        ).accepted());
        assertFalse(first.recordWorkstationExecution(
                RUN_ID,
                OTHER_GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                2L
        ).accepted());
        assertFalse(first.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                OTHER_EXECUTION_OPERATION_ID,
                2L
        ).accepted());

        ProductionTestFixtures.TestContext secondContext = ProductionTestFixtures.context();
        ProductionManager second = secondContext.manager();
        assertTrue(second.registerProcess(ProductionTestFixtures.process()).accepted());
        assertTrue(second.registerPlan(ProductionTestFixtures.plan()).accepted());
        ProductionPlanDefinition otherPlan = ProductionPlanDefinition.builder()
                .id(ProductionPlanId.of("test:second_plan"))
                .processId(ProductionTestFixtures.PROCESS_ID)
                .producerActorId(ProductionTestFixtures.ACTOR)
                .batchCount(1L)
                .inventoryBinding(ProductionTestFixtures.plan().inventoryBindings().get(0))
                .inventoryBinding(ProductionTestFixtures.plan().inventoryBindings().get(1))
                .inventoryBinding(ProductionTestFixtures.plan().inventoryBindings().get(2))
                .createdSimulationTick(0L)
                .earliestStartTick(1L)
                .build();
        assertTrue(second.registerPlan(otherPlan).accepted());
        ProductionRunId otherRun = ProductionRunId.forPlan(otherPlan.id());
        assertTrue(second.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());
        assertTrue(second.assignWorkstation(otherRun, OTHER_GRINDER_ID, OTHER_PROCESS_IDENTITY, 0L).accepted());
        assertTrue(second.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        ).accepted());
        ProductionOperationResult<ProductionRunSnapshot> duplicateExecution = second.recordWorkstationExecution(
                otherRun,
                OTHER_GRINDER_ID,
                OTHER_PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        );
        assertFalse(duplicateExecution.accepted());
        assertEquals(ProductionFailureCode.WORKSTATION_EXECUTION_CONFLICT,
                duplicateExecution.failures().getFirst().code());
    }

    @Test
    void workstationRejectionFailureAndUnknownOutcomeStopWithoutAutomaticRerun() {
        assertWorkstationTerminalFailure(
                ProductionFailureCode.WORKSTATION_REJECTED,
                manager -> manager.recordWorkstationRejection(RUN_ID, "Grinder rejected input", 1L)
        );
        assertWorkstationTerminalFailure(
                ProductionFailureCode.WORKSTATION_FAILED,
                manager -> manager.recordWorkstationFailure(RUN_ID, "Grinder failed input", 1L)
        );
        assertWorkstationTerminalFailure(
                ProductionFailureCode.WORKSTATION_UNKNOWN_OUTCOME,
                manager -> manager.recordWorkstationUnknownOutcome(RUN_ID, "Execution outcome unknown", 1L)
        );
    }

    @Test
    void productionCancellationIsAllowedOnlyBeforeGrinderProcessingBegins() {
        ProductionTestFixtures.TestContext cancellableContext = ProductionTestFixtures.context();
        ProductionManager cancellable = ProductionTestFixtures.populatedManager(cancellableContext);
        assertTrue(cancellable.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());
        assertTrue(cancellable.cancel(RUN_ID, scheduler(cancellable), 1L, "before grinder starts").accepted());

        ProductionTestFixtures.TestContext runningContext = ProductionTestFixtures.context();
        ProductionManager running = ProductionTestFixtures.populatedManager(runningContext);
        assertTrue(running.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());
        assertTrue(running.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        ).accepted());
        ProductionOperationResult<ProductionRunSnapshot> cancelled =
                running.cancel(RUN_ID, scheduler(running), 2L, "too late");
        assertFalse(cancelled.accepted());
        assertEquals(ProductionFailureCode.INVALID_STATUS, cancelled.failures().getFirst().code());
        assertEquals(ProductionRunStatus.RUNNING, running.findRun(RUN_ID).orElseThrow().status());
    }

    @Test
    void workstationAssignmentAndCompletionEvidenceRoundTripThroughProductionPersistence() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        assertTrue(manager.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());
        assertTrue(manager.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        ).accepted());
        ProductionRunSnapshot completed = complete(manager, 2L).value().orElseThrow();
        ProductionStorage storage = storage(context);

        String processes = storage.serializeProcesses(manager.processRegistry());
        String plans = storage.serializePlans(manager.planRegistry());
        String runs = storage.serializeRuns(manager.runs());
        ProductionPersistenceSnapshot snapshot = storage.deserialize(processes, plans, runs);

        assertEquals(List.of(completed), snapshot.runs());
        assertEquals(runs, storage.serializeRuns(snapshot.runs()));

        storage.save(manager);
        ProductionManager loaded = storage.load();
        assertEquals(completed, loaded.findRun(RUN_ID).orElseThrow());
        assertTrue(loaded.findByWorkstationExecution(EXECUTION_OPERATION_ID).isPresent());
    }

    private static ProductionOperationResult<ProductionRunSnapshot> complete(
            ProductionManager manager,
            long tick
    ) {
        return manager.completeFromWorkstation(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                EXECUTION_SUCCEEDED,
                OWNER_RESULT_ID,
                OWNER_RESULT_DIGEST,
                EXECUTION_RESULT_ID,
                EXECUTION_RESULT_DIGEST,
                tick
        );
    }

    private static void assertWorkstationTerminalFailure(
            ProductionFailureCode expectedCode,
            java.util.function.Function<ProductionManager, ProductionOperationResult<ProductionRunSnapshot>> operation
    ) {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        assertTrue(manager.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());

        ProductionOperationResult<ProductionRunSnapshot> result = operation.apply(manager);

        assertTrue(result.accepted());
        ProductionRunSnapshot run = result.value().orElseThrow();
        assertEquals(ProductionRunStatus.FAILED, run.status());
        assertEquals(expectedCode, run.failureCode().orElseThrow());
        assertTrue(run.scheduledWorkId().isEmpty());
        assertTrue(run.completionTransactionId().isEmpty());
        assertEquals(0, context.dependencies().transactionManager().size());
        assertFalse(manager.schedule(RUN_ID, scheduler(manager), 2L).accepted());
    }

    private ProductionStorage storage(ProductionTestFixtures.TestContext context) {
        return new ProductionStorage(
                temporaryDirectory.resolve(ProductionSchema.PROCESSES_FILE_NAME),
                temporaryDirectory.resolve(ProductionSchema.PLANS_FILE_NAME),
                temporaryDirectory.resolve(ProductionSchema.RUNS_FILE_NAME),
                context.dependencies()
        );
    }

    private static SimulationSchedulerManager scheduler(ProductionManager manager) {
        return new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(new ProductionSimulationWorkHandler(manager))),
                0L
        );
    }
}
