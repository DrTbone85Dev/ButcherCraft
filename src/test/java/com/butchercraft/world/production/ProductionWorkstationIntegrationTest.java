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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionWorkstationIntegrationTest {
    private static final ProductionRunId RUN_ID = ProductionRunId.forPlan(ProductionTestFixtures.PLAN_ID);
    private static final String GRINDER_ID = "butchercraft:workstation/grinder/minecraft/overworld/1/2/3";
    private static final String OTHER_GRINDER_ID = "butchercraft:workstation/grinder/minecraft/overworld/4/5/6";
    private static final String PATTY_FORMER_ID =
            "butchercraft:workstation/patty_former/minecraft/overworld/7/8/9";
    private static final String PROCESS_IDENTITY = "butchercraft:grind_beef";
    private static final String PATTY_PROCESS_IDENTITY = "butchercraft:form_beef_patties";
    private static final String OTHER_PROCESS_IDENTITY = "butchercraft:grind_pork";
    private static final String EXECUTION_OPERATION_ID = "butchercraft:execution_operation/v1/" + "a".repeat(64);
    private static final String OTHER_EXECUTION_OPERATION_ID = "butchercraft:execution_operation/v1/" + "b".repeat(64);
    private static final String CHAIN_GRINDER_EXECUTION_OPERATION_ID =
            "butchercraft:execution_operation/v1/" + "1".repeat(64);
    private static final String CHAIN_PATTY_EXECUTION_OPERATION_ID =
            "butchercraft:execution_operation/v1/" + "2".repeat(64);
    private static final String OWNER_RESULT_ID = "butchercraft:workstation_result/v1/" + "c".repeat(64);
    private static final String OWNER_RESULT_DIGEST = "sha256:" + "d".repeat(64);
    private static final String EXECUTION_RESULT_ID =
            "butchercraft:execution_result_evidence/v1/" + "e".repeat(64);
    private static final String EXECUTION_RESULT_DIGEST = "sha256:" + "f".repeat(64);
    private static final String SECOND_OWNER_RESULT_ID = "butchercraft:workstation_result/v1/" + "3".repeat(64);
    private static final String SECOND_OWNER_RESULT_DIGEST = "sha256:" + "4".repeat(64);
    private static final String SECOND_EXECUTION_RESULT_ID =
            "butchercraft:execution_result_evidence/v1/" + "5".repeat(64);
    private static final String SECOND_EXECUTION_RESULT_DIGEST = "sha256:" + "6".repeat(64);
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

    @Test
    void productionAssignsOrderedBeefPattyChainWithoutSchedulingOrTransactions() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);

        ProductionOperationResult<ProductionRunSnapshot> assigned =
                manager.assignWorkstationChain(RUN_ID, chain, 0L);

        assertTrue(assigned.accepted());
        ProductionRunSnapshot run = assigned.value().orElseThrow();
        assertEquals(ProductionRunStatus.READY, run.status());
        assertTrue(run.workstationAssignment().isEmpty());
        assertTrue(run.workstationChain().isPresent());
        assertEquals(ProductionWorkstationChainStatus.AWAITING_GRINDER_ASSIGNMENT,
                run.workstationChain().orElseThrow().status());
        assertTrue(run.scheduledWorkId().isEmpty());
        assertTrue(run.completionTransactionId().isEmpty());
        assertEquals(0, context.dependencies().transactionManager().size());

        ProductionWorkstationChainStep grinder = grinderStep(run.workstationChain().orElseThrow());
        ProductionWorkstationChainStep pattyFormer = pattyFormerStep(run.workstationChain().orElseThrow());
        assertEquals("butchercraft:workstation/grinder", grinder.expectedWorkstationType());
        assertEquals(PROCESS_IDENTITY, grinder.processIdentity());
        assertEquals("butchercraft:beef_trim", grinder.inputProductIdentity());
        assertEquals("butchercraft:ground_beef", grinder.outputProductIdentity());
        assertEquals("butchercraft:workstation/patty_former", pattyFormer.expectedWorkstationType());
        assertEquals(PATTY_PROCESS_IDENTITY, pattyFormer.processIdentity());
        assertEquals("butchercraft:ground_beef", pattyFormer.inputProductIdentity());
        assertEquals("butchercraft:beef_patties", pattyFormer.outputProductIdentity());

        assertEquals(run, manager.assignWorkstationChain(RUN_ID, chain, 0L).value().orElseThrow());
        ProductionOperationResult<ProductionRunSnapshot> scheduled =
                manager.schedule(RUN_ID, scheduler(manager), 0L);
        assertFalse(scheduled.accepted());
        assertEquals(ProductionFailureCode.WORK_ALREADY_BOUND, scheduled.failures().getFirst().code());
    }

    @Test
    void productionCompletesBeefPattyChainOnlyAfterBothWorkstationOwnerResults() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        assertTrue(manager.assignWorkstationChain(RUN_ID, chain, 0L).accepted());
        String grinderStep = grinderStep(chain).stepIdentity();
        String pattyStep = pattyFormerStep(chain).stepIdentity();

        assertTrue(manager.assignWorkstationChainStep(RUN_ID, grinderStep, GRINDER_ID, PROCESS_IDENTITY, 1L)
                .accepted());
        assertTrue(manager.recordWorkstationChainExecution(
                RUN_ID,
                grinderStep,
                GRINDER_ID,
                PROCESS_IDENTITY,
                CHAIN_GRINDER_EXECUTION_OPERATION_ID,
                2L
        ).accepted());

        ProductionOperationResult<ProductionRunSnapshot> firstCompleted =
                completeGrinderChainStep(manager, grinderStep, 3L);

        assertTrue(firstCompleted.accepted());
        ProductionRunSnapshot afterGrinder = firstCompleted.value().orElseThrow();
        assertEquals(ProductionRunStatus.READY, afterGrinder.status());
        assertEquals(afterGrinder.requiredWorkUnits() / 2L, afterGrinder.currentWorkUnits());
        ProductionWorkstationChain afterGrinderChain = afterGrinder.workstationChain().orElseThrow();
        assertEquals(ProductionWorkstationChainStatus.AWAITING_MANUAL_TRANSFER, afterGrinderChain.status());
        assertEquals(ProductionChainStepStatus.COMPLETE, grinderStep(afterGrinderChain).status());
        assertEquals(ProductionChainStepStatus.AWAITING_ASSIGNMENT, pattyFormerStep(afterGrinderChain).status());
        assertTrue(afterGrinderChain.completionEvidence().isEmpty());
        assertTrue(afterGrinder.completionTransactionId().isEmpty());
        assertEquals(0, context.dependencies().transactionManager().size());

        assertTrue(manager.assignWorkstationChainStep(RUN_ID, pattyStep, PATTY_FORMER_ID, PATTY_PROCESS_IDENTITY, 4L)
                .accepted());
        assertTrue(manager.recordWorkstationChainExecution(
                RUN_ID,
                pattyStep,
                PATTY_FORMER_ID,
                PATTY_PROCESS_IDENTITY,
                CHAIN_PATTY_EXECUTION_OPERATION_ID,
                5L
        ).accepted());

        ProductionOperationResult<ProductionRunSnapshot> completed =
                completePattyChainStep(manager, pattyStep, 6L);

        assertTrue(completed.accepted());
        ProductionRunSnapshot run = completed.value().orElseThrow();
        assertEquals(ProductionRunStatus.COMPLETED, run.status());
        assertEquals(run.requiredWorkUnits(), run.currentWorkUnits());
        assertTrue(run.completionTransactionId().isEmpty());
        assertEquals(2, run.executionAttemptCount());
        ProductionWorkstationChain completedChain = run.workstationChain().orElseThrow();
        assertEquals(ProductionWorkstationChainStatus.COMPLETE, completedChain.status());
        assertEquals(ProductionChainStepStatus.COMPLETE, grinderStep(completedChain).status());
        assertEquals(ProductionChainStepStatus.COMPLETE, pattyFormerStep(completedChain).status());
        ProductionChainCompletionEvidence evidence = completedChain.completionEvidence().orElseThrow();
        assertTrue(evidence.digestMatches());
        assertEquals("butchercraft:ground_beef", evidence.firstStepOutputProductIdentity());
        assertEquals("butchercraft:ground_beef", evidence.secondStepInputProductIdentity());
        assertEquals(6L, evidence.completedSimulationTick());
        assertEquals(0, context.dependencies().transactionManager().size());
        assertTrue(manager.findByWorkstationExecution(CHAIN_GRINDER_EXECUTION_OPERATION_ID).isPresent());
        assertTrue(manager.findByWorkstationExecution(CHAIN_PATTY_EXECUTION_OPERATION_ID).isPresent());
    }

    @Test
    void duplicateChainObservationsReturnExistingAuthoritativeRun() {
        ProductionManager manager = ProductionTestFixtures.populatedManager(ProductionTestFixtures.context());
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String grinderStep = grinderStep(chain).stepIdentity();
        String pattyStep = pattyFormerStep(chain).stepIdentity();

        ProductionRunSnapshot assigned = manager.assignWorkstationChain(RUN_ID, chain, 0L)
                .value().orElseThrow();
        assertEquals(assigned, manager.assignWorkstationChain(RUN_ID, chain, 0L).value().orElseThrow());

        ProductionRunSnapshot bound = manager.recordWorkstationChainExecution(
                RUN_ID,
                grinderStep,
                GRINDER_ID,
                PROCESS_IDENTITY,
                CHAIN_GRINDER_EXECUTION_OPERATION_ID,
                1L
        ).value().orElseThrow();
        assertEquals(bound, manager.recordWorkstationChainExecution(
                RUN_ID,
                grinderStep,
                GRINDER_ID,
                PROCESS_IDENTITY,
                CHAIN_GRINDER_EXECUTION_OPERATION_ID,
                2L
        ).value().orElseThrow());

        ProductionRunSnapshot firstCompleted = completeGrinderChainStep(manager, grinderStep, 3L)
                .value().orElseThrow();
        assertEquals(firstCompleted, completeGrinderChainStep(manager, grinderStep, 3L)
                .value().orElseThrow());

        ProductionRunSnapshot secondBound = manager.recordWorkstationChainExecution(
                RUN_ID,
                pattyStep,
                PATTY_FORMER_ID,
                PATTY_PROCESS_IDENTITY,
                CHAIN_PATTY_EXECUTION_OPERATION_ID,
                4L
        ).value().orElseThrow();
        assertEquals(secondBound, manager.recordWorkstationChainExecution(
                RUN_ID,
                pattyStep,
                PATTY_FORMER_ID,
                PATTY_PROCESS_IDENTITY,
                CHAIN_PATTY_EXECUTION_OPERATION_ID,
                5L
        ).value().orElseThrow());

        ProductionRunSnapshot completed = completePattyChainStep(manager, pattyStep, 6L)
                .value().orElseThrow();
        assertEquals(completed, completePattyChainStep(manager, pattyStep, 6L)
                .value().orElseThrow());
    }

    @Test
    void conflictingChainAssignmentExecutionAndProductFlowAreRejected() {
        ProductionManager manager = ProductionTestFixtures.populatedManager(ProductionTestFixtures.context());
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String grinderStep = grinderStep(chain).stepIdentity();
        assertTrue(manager.assignWorkstationChain(RUN_ID, chain, 0L).accepted());
        assertTrue(manager.assignWorkstationChainStep(RUN_ID, grinderStep, GRINDER_ID, PROCESS_IDENTITY, 1L)
                .accepted());

        ProductionOperationResult<ProductionRunSnapshot> reassigned =
                manager.assignWorkstationChainStep(RUN_ID, grinderStep, OTHER_GRINDER_ID, PROCESS_IDENTITY, 2L);
        assertFalse(reassigned.accepted());
        assertEquals(ProductionFailureCode.WORKSTATION_CHAIN_CONFLICT, reassigned.failures().getFirst().code());

        assertTrue(manager.recordWorkstationChainExecution(
                RUN_ID,
                grinderStep,
                GRINDER_ID,
                PROCESS_IDENTITY,
                CHAIN_GRINDER_EXECUTION_OPERATION_ID,
                3L
        ).accepted());
        ProductionOperationResult<ProductionRunSnapshot> differentExecution =
                manager.recordWorkstationChainExecution(
                        RUN_ID,
                        grinderStep,
                        GRINDER_ID,
                        PROCESS_IDENTITY,
                        OTHER_EXECUTION_OPERATION_ID,
                        4L
                );
        assertFalse(differentExecution.accepted());
        assertEquals(ProductionFailureCode.WORKSTATION_EXECUTION_CONFLICT,
                differentExecution.failures().getFirst().code());

        ProductionWorkstationChainStep badPattyStep = new ProductionWorkstationChainStep(
                ProductionSchema.CURRENT_VERSION,
                pattyFormerStep(chain).stepIdentity(),
                1,
                "butchercraft:workstation/patty_former",
                PATTY_PROCESS_IDENTITY,
                "butchercraft:ground_pork",
                "butchercraft:beef_patties",
                ProductionChainStepStatus.AWAITING_ASSIGNMENT,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
        assertThrows(IllegalArgumentException.class, () -> new ProductionWorkstationChain(
                ProductionSchema.CURRENT_VERSION,
                chain.chainIdentity(),
                ProductionWorkstationChainStatus.AWAITING_GRINDER_ASSIGNMENT,
                List.of(grinderStep(chain), badPattyStep),
                Optional.empty()
        ));
    }

    @Test
    void chainRejectionFailureAndUnknownOutcomeStopWithoutAutomaticRerun() {
        assertChainTerminalFailure(
                ProductionFailureCode.WORKSTATION_REJECTED,
                ProductionWorkstationChainStatus.FAILED,
                ProductionChainStepStatus.FAILED,
                (manager, step) -> manager.recordWorkstationChainRejection(
                        RUN_ID,
                        step,
                        "Grinder rejected input",
                        1L
                )
        );
        assertChainTerminalFailure(
                ProductionFailureCode.WORKSTATION_FAILED,
                ProductionWorkstationChainStatus.FAILED,
                ProductionChainStepStatus.FAILED,
                (manager, step) -> manager.recordWorkstationChainFailure(
                        RUN_ID,
                        step,
                        "Grinder failed input",
                        1L
                )
        );
        assertChainTerminalFailure(
                ProductionFailureCode.WORKSTATION_UNKNOWN_OUTCOME,
                ProductionWorkstationChainStatus.UNKNOWN_OUTCOME,
                ProductionChainStepStatus.UNKNOWN_OUTCOME,
                (manager, step) -> manager.recordWorkstationChainUnknownOutcome(
                        RUN_ID,
                        step,
                        "Execution outcome unknown",
                        1L
                )
        );
    }

    @Test
    void productionChainCancellationIsAllowedOnlyBeforeFirstWorkstationExecutionBegins() {
        ProductionTestFixtures.TestContext cancellableContext = ProductionTestFixtures.context();
        ProductionManager cancellable = ProductionTestFixtures.populatedManager(cancellableContext);
        assertTrue(cancellable.assignWorkstationChain(RUN_ID, ProductionWorkstationChain.beefPattyChain(RUN_ID), 0L)
                .accepted());
        ProductionOperationResult<ProductionRunSnapshot> cancelled =
                cancellable.cancel(RUN_ID, scheduler(cancellable), 1L, "before grinder starts");
        assertTrue(cancelled.accepted());
        assertEquals(ProductionRunStatus.CANCELLED, cancelled.value().orElseThrow().status());
        assertEquals(ProductionWorkstationChainStatus.CANCELLED_BEFORE_FIRST_EFFECT,
                cancelled.value().orElseThrow().workstationChain().orElseThrow().status());

        ProductionTestFixtures.TestContext runningContext = ProductionTestFixtures.context();
        ProductionManager running = ProductionTestFixtures.populatedManager(runningContext);
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String grinderStep = grinderStep(chain).stepIdentity();
        assertTrue(running.assignWorkstationChain(RUN_ID, chain, 0L).accepted());
        assertTrue(running.recordWorkstationChainExecution(
                RUN_ID,
                grinderStep,
                GRINDER_ID,
                PROCESS_IDENTITY,
                CHAIN_GRINDER_EXECUTION_OPERATION_ID,
                1L
        ).accepted());
        ProductionOperationResult<ProductionRunSnapshot> rejected =
                running.cancel(RUN_ID, scheduler(running), 2L, "too late");
        assertFalse(rejected.accepted());
        assertEquals(ProductionFailureCode.INVALID_STATUS, rejected.failures().getFirst().code());
        assertEquals(ProductionRunStatus.RUNNING, running.findRun(RUN_ID).orElseThrow().status());
    }

    @Test
    void workstationChainEvidenceRoundTripsThroughProductionPersistenceAndLegacyRunsStillLoad() {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionRunSnapshot completed = completeBeefPattyChain(manager);
        ProductionStorage storage = storage(context);

        String processes = storage.serializeProcesses(manager.processRegistry());
        String plans = storage.serializePlans(manager.planRegistry());
        String runs = storage.serializeRuns(manager.runs());
        assertTrue(runs.contains("\"workstation_chain\""));
        assertTrue(runs.contains(PATTY_PROCESS_IDENTITY));

        ProductionPersistenceSnapshot snapshot = storage.deserialize(processes, plans, runs);

        assertEquals(List.of(completed), snapshot.runs());
        assertEquals(runs, storage.serializeRuns(snapshot.runs()));

        storage.save(manager);
        ProductionManager loaded = storage.load();
        assertEquals(completed, loaded.findRun(RUN_ID).orElseThrow());
        assertTrue(loaded.findByWorkstationExecution(CHAIN_GRINDER_EXECUTION_OPERATION_ID).isPresent());
        assertTrue(loaded.findByWorkstationExecution(CHAIN_PATTY_EXECUTION_OPERATION_ID).isPresent());

        ProductionTestFixtures.TestContext legacyContext = ProductionTestFixtures.context();
        ProductionManager legacyManager = ProductionTestFixtures.populatedManager(legacyContext);
        assertTrue(legacyManager.assignWorkstation(RUN_ID, GRINDER_ID, PROCESS_IDENTITY, 0L).accepted());
        assertTrue(legacyManager.recordWorkstationExecution(
                RUN_ID,
                GRINDER_ID,
                PROCESS_IDENTITY,
                EXECUTION_OPERATION_ID,
                1L
        ).accepted());
        ProductionRunSnapshot legacyCompleted = complete(legacyManager, 2L).value().orElseThrow();
        ProductionStorage legacyStorage = storage(legacyContext);
        String legacyRuns = legacyStorage.serializeRuns(legacyManager.runs())
                .replace("      \"workstation_chain\": null,\r\n", "")
                .replace("      \"workstation_chain\": null,\n", "");

        ProductionPersistenceSnapshot legacySnapshot = legacyStorage.deserialize(
                legacyStorage.serializeProcesses(legacyManager.processRegistry()),
                legacyStorage.serializePlans(legacyManager.planRegistry()),
                legacyRuns
        );
        assertEquals(List.of(legacyCompleted), legacySnapshot.runs());
        assertTrue(legacySnapshot.runs().getFirst().workstationChain().isEmpty());
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

    private static ProductionRunSnapshot completeBeefPattyChain(ProductionManager manager) {
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String grinderStep = grinderStep(chain).stepIdentity();
        String pattyStep = pattyFormerStep(chain).stepIdentity();
        assertTrue(manager.assignWorkstationChain(RUN_ID, chain, 0L).accepted());
        assertTrue(manager.recordWorkstationChainExecution(
                RUN_ID,
                grinderStep,
                GRINDER_ID,
                PROCESS_IDENTITY,
                CHAIN_GRINDER_EXECUTION_OPERATION_ID,
                1L
        ).accepted());
        assertTrue(completeGrinderChainStep(manager, grinderStep, 2L).accepted());
        assertTrue(manager.recordWorkstationChainExecution(
                RUN_ID,
                pattyStep,
                PATTY_FORMER_ID,
                PATTY_PROCESS_IDENTITY,
                CHAIN_PATTY_EXECUTION_OPERATION_ID,
                3L
        ).accepted());
        return completePattyChainStep(manager, pattyStep, 4L).value().orElseThrow();
    }

    private static ProductionOperationResult<ProductionRunSnapshot> completeGrinderChainStep(
            ProductionManager manager,
            String stepIdentity,
            long tick
    ) {
        return manager.completeWorkstationChainStepFromWorkstation(
                RUN_ID,
                stepIdentity,
                GRINDER_ID,
                PROCESS_IDENTITY,
                CHAIN_GRINDER_EXECUTION_OPERATION_ID,
                EXECUTION_SUCCEEDED,
                OWNER_RESULT_ID,
                OWNER_RESULT_DIGEST,
                EXECUTION_RESULT_ID,
                EXECUTION_RESULT_DIGEST,
                tick
        );
    }

    private static ProductionOperationResult<ProductionRunSnapshot> completePattyChainStep(
            ProductionManager manager,
            String stepIdentity,
            long tick
    ) {
        return manager.completeWorkstationChainStepFromWorkstation(
                RUN_ID,
                stepIdentity,
                PATTY_FORMER_ID,
                PATTY_PROCESS_IDENTITY,
                CHAIN_PATTY_EXECUTION_OPERATION_ID,
                EXECUTION_SUCCEEDED,
                SECOND_OWNER_RESULT_ID,
                SECOND_OWNER_RESULT_DIGEST,
                SECOND_EXECUTION_RESULT_ID,
                SECOND_EXECUTION_RESULT_DIGEST,
                tick
        );
    }

    private static ProductionWorkstationChainStep grinderStep(ProductionWorkstationChain chain) {
        return chain.steps().getFirst();
    }

    private static ProductionWorkstationChainStep pattyFormerStep(ProductionWorkstationChain chain) {
        return chain.steps().get(1);
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

    private static void assertChainTerminalFailure(
            ProductionFailureCode expectedCode,
            ProductionWorkstationChainStatus expectedChainStatus,
            ProductionChainStepStatus expectedStepStatus,
            ChainOperation operation
    ) {
        ProductionTestFixtures.TestContext context = ProductionTestFixtures.context();
        ProductionManager manager = ProductionTestFixtures.populatedManager(context);
        ProductionWorkstationChain chain = ProductionWorkstationChain.beefPattyChain(RUN_ID);
        String stepIdentity = grinderStep(chain).stepIdentity();
        assertTrue(manager.assignWorkstationChain(RUN_ID, chain, 0L).accepted());

        ProductionOperationResult<ProductionRunSnapshot> result = operation.apply(manager, stepIdentity);

        assertTrue(result.accepted());
        ProductionRunSnapshot run = result.value().orElseThrow();
        assertEquals(ProductionRunStatus.FAILED, run.status());
        assertEquals(expectedCode, run.failureCode().orElseThrow());
        assertTrue(run.scheduledWorkId().isEmpty());
        assertTrue(run.completionTransactionId().isEmpty());
        ProductionWorkstationChain failedChain = run.workstationChain().orElseThrow();
        assertEquals(expectedChainStatus, failedChain.status());
        assertEquals(expectedStepStatus, grinderStep(failedChain).status());
        assertEquals(0, context.dependencies().transactionManager().size());
        assertFalse(manager.schedule(RUN_ID, scheduler(manager), 2L).accepted());
    }

    @FunctionalInterface
    private interface ChainOperation {
        ProductionOperationResult<ProductionRunSnapshot> apply(ProductionManager manager, String stepIdentity);
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
