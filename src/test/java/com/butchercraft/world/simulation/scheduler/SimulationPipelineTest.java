package com.butchercraft.world.simulation.scheduler;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationPipelineTest {
    @Test
    void emptyPipelineExecutesExactlyOncePerSequentialAuthoritativeTick() {
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(
                SchedulerTestFixtures.handler(context -> SimulationWorkResult.completed(
                        context.authoritativeSimulationTick(), 1)), 10
        );
        SimulationPipeline pipeline = new SimulationPipeline(manager, SimulationExecutionBudget.standard());

        assertEquals(PipelineStatus.COMPLETED, pipeline.execute(11).status());
        assertEquals(PipelineStatus.REJECTED, pipeline.execute(11).status());
        assertEquals(WorkFailureCode.BACKWARD_TICK, pipeline.execute(10).failureCode().orElseThrow());
        assertEquals(WorkFailureCode.INVALID_TICK, pipeline.execute(13).failureCode().orElseThrow());
        assertEquals(11, manager.lastFinalizedSimulationTick());
    }

    @Test
    void orderingUsesStageThenTickPrioritySequenceAndId() {
        List<SimulationWorkId> executionOrder = new ArrayList<>();
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            executionOrder.add(context.work().id());
            return SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1);
        });
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:execution_urgent", BuiltInSimulationStages.EXECUTION, 0, 1,
                WorkPriority.URGENT, RetryPolicy.never(), 1), 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:planning_low", BuiltInSimulationStages.PLANNING, 0, 1,
                WorkPriority.LOW, RetryPolicy.never(), 1), 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:execution_low", BuiltInSimulationStages.EXECUTION, 0, 1,
                WorkPriority.LOW, RetryPolicy.never(), 1), 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:execution_urgent_later_sequence", BuiltInSimulationStages.EXECUTION, 0, 1,
                WorkPriority.URGENT, RetryPolicy.never(), 1), 0);

        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);

        assertEquals(List.of(
                SimulationWorkId.of("test:planning_low"),
                SimulationWorkId.of("test:execution_urgent"),
                SimulationWorkId.of("test:execution_urgent_later_sequence"),
                SimulationWorkId.of("test:execution_low")
        ), executionOrder);
    }

    @Test
    void budgetExhaustionPreservesEligibleWorkForNextTick() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        for (int index = 0; index < 3; index++) {
            manager.submit(SchedulerTestFixtures.request(
                    "test:budget_" + index, BuiltInSimulationStages.EXECUTION, 0, 1), 0);
        }
        SimulationExecutionBudget budget = new SimulationExecutionBudget(2, 2, 10, 10, 10, 10, 3);
        SimulationPipeline pipeline = new SimulationPipeline(manager, budget);

        SimulationTickReport first = pipeline.execute(1);
        assertEquals(PipelineStatus.BUDGET_EXHAUSTED, first.status());
        assertEquals(2, manager.findByStatus(SimulationWorkStatus.COMPLETED).size());
        assertEquals(1, manager.findByStatus(SimulationWorkStatus.SCHEDULED).size());
        assertEquals(1, manager.findEligibleAt(1).size());

        assertEquals(PipelineStatus.COMPLETED, pipeline.execute(2).status());
        assertEquals(3, manager.findByStatus(SimulationWorkStatus.COMPLETED).size());
    }

    @Test
    void expirationPromotionLimitReportsBudgetExhaustionAndPreservesRemainder() {
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(
                SchedulerTestFixtures.handler(context ->
                        SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1)), 0
        );
        for (int index = 0; index < 5; index++) {
            SimulationWorkRequest request = new SimulationWorkRequest(
                    SimulationWorkId.of("test:expiring_budget_" + index), SchedulerTestFixtures.TYPE,
                    BuiltInSimulationStages.EXECUTION, 1, WorkPriority.NORMAL,
                    WorkOrigin.of("test:scheduler", 0, "test:unit_test"), WorkPayload.empty(),
                    RetryPolicy.never(), 1, OptionalLong.of(1), List.of()
            );
            manager.submit(request, 0);
        }
        SimulationExecutionBudget budget = new SimulationExecutionBudget(2, 2, 10, 10, 10, 10, 2);

        SimulationPipeline pipeline = new SimulationPipeline(manager, budget);
        pipeline.execute(1);
        SimulationTickReport report = pipeline.execute(2);

        assertEquals(PipelineStatus.BUDGET_EXHAUSTED, report.status());
        assertEquals(2, report.expiredWorkItems());
        assertEquals(2, manager.findByStatus(SimulationWorkStatus.COMPLETED).size());
        assertEquals(1, manager.findByStatus(SimulationWorkStatus.SCHEDULED).size());
    }

    @Test
    void deferredRetryFailedAndExceptionalResultsRemainExplicit() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            String id = context.work().id().value();
            if (id.endsWith("defer") && context.attemptNumber() == 1) {
                return SchedulerTestFixtures.result(
                        SimulationWorkOutcome.DEFERRED, context.authoritativeSimulationTick(), 1,
                        OptionalLong.of(2), List.of()
                );
            }
            if (id.endsWith("retry") && context.attemptNumber() == 1) {
                return SchedulerTestFixtures.result(
                        SimulationWorkOutcome.RETRY, context.authoritativeSimulationTick(), 1,
                        OptionalLong.empty(), List.of()
                );
            }
            if (id.endsWith("fail")) {
                return SchedulerTestFixtures.result(
                        SimulationWorkOutcome.FAILED, context.authoritativeSimulationTick(), 1,
                        OptionalLong.empty(), List.of()
                );
            }
            if (id.endsWith("exception")) throw new IllegalStateException("test exception");
            return SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1);
        });
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:defer", BuiltInSimulationStages.EXECUTION, 0, 1,
                WorkPriority.NORMAL, RetryPolicy.never(), 2), 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:retry", BuiltInSimulationStages.EXECUTION, 0, 1,
                WorkPriority.NORMAL, RetryPolicy.nextTick(), 2), 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:fail", BuiltInSimulationStages.EXECUTION, 0, 1), 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:exception", BuiltInSimulationStages.EXECUTION, 0, 1), 0);
        SimulationPipeline pipeline = new SimulationPipeline(manager, SimulationExecutionBudget.standard());

        pipeline.execute(1);
        assertEquals(SimulationWorkStatus.DEFERRED,
                manager.runtimeFor(SimulationWorkId.of("test:defer")).orElseThrow().status());
        assertEquals(SimulationWorkStatus.RETRY_WAIT,
                manager.runtimeFor(SimulationWorkId.of("test:retry")).orElseThrow().status());
        assertEquals(WorkFailureCode.HANDLER_REJECTED,
                manager.runtimeFor(SimulationWorkId.of("test:fail")).orElseThrow().lastFailureCode().orElseThrow());
        assertEquals(WorkFailureCode.HANDLER_EXCEPTION,
                manager.runtimeFor(SimulationWorkId.of("test:exception")).orElseThrow().lastFailureCode().orElseThrow());

        pipeline.execute(2);
        assertEquals(SimulationWorkStatus.COMPLETED,
                manager.runtimeFor(SimulationWorkId.of("test:defer")).orElseThrow().status());
        assertEquals(SimulationWorkStatus.COMPLETED,
                manager.runtimeFor(SimulationWorkId.of("test:retry")).orElseThrow().status());
    }

    @Test
    void deferralCannotExceedMaximumAttempts() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SchedulerTestFixtures.result(
                        SimulationWorkOutcome.DEFERRED, context.authoritativeSimulationTick(), 1,
                        OptionalLong.of(2), List.of()
                ));
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:deferral_limit", BuiltInSimulationStages.EXECUTION, 0, 1,
                WorkPriority.NORMAL, RetryPolicy.never(), 1
        );
        manager.submit(request, 0);

        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);

        SimulationWorkRuntime runtime = manager.runtimeFor(request.id()).orElseThrow();
        assertEquals(SimulationWorkStatus.FAILED, runtime.status());
        assertEquals(WorkFailureCode.RETRY_LIMIT_REACHED, runtime.lastFailureCode().orElseThrow());
        assertEquals(1, runtime.attemptCount());
    }

    @Test
    void generatedBatchExecutesOnlyInLaterPermittedStageAndDefersCurrentStage() {
        List<SimulationWorkId> executionOrder = new ArrayList<>();
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            executionOrder.add(context.work().id());
            if (context.work().id().value().equals("test:parent")) {
                SimulationWorkRequest currentStage = SchedulerTestFixtures.request(
                        "test:current_child", BuiltInSimulationStages.PLANNING, 1, 1
                );
                SimulationWorkRequest laterStage = SchedulerTestFixtures.request(
                        "test:later_child", BuiltInSimulationStages.EXECUTION, 1, 1
                );
                return SchedulerTestFixtures.result(
                        SimulationWorkOutcome.COMPLETED, 1, 1, OptionalLong.empty(),
                        List.of(currentStage, laterStage)
                );
            }
            return SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1);
        });
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:parent", BuiltInSimulationStages.PLANNING, 0, 1), 0);
        SimulationPipeline pipeline = new SimulationPipeline(manager, SimulationExecutionBudget.standard());

        pipeline.execute(1);
        assertEquals(List.of(SimulationWorkId.of("test:parent"), SimulationWorkId.of("test:later_child")),
                executionOrder);
        ScheduledSimulationWork currentChild = manager.registry().find(
                SimulationWorkId.of("test:current_child")).orElseThrow();
        assertEquals(2, currentChild.scheduledTick());
        assertEquals(SimulationWorkId.of("test:parent"), currentChild.origin().parentWorkId().orElseThrow());

        pipeline.execute(2);
        assertEquals(SimulationWorkId.of("test:current_child"), executionOrder.getLast());
    }

    @Test
    void duplicateGeneratedIdsRejectWholeBatchAndFailParent() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            SimulationWorkRequest duplicate = SchedulerTestFixtures.request(
                    "test:duplicate_child", BuiltInSimulationStages.EXECUTION, 1, 1
            );
            return SchedulerTestFixtures.result(
                    SimulationWorkOutcome.COMPLETED, 1, 1, OptionalLong.empty(), List.of(duplicate, duplicate)
            );
        });
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        SimulationWorkRequest parent = SchedulerTestFixtures.request(
                "test:duplicate_parent", BuiltInSimulationStages.PLANNING, 0, 1
        );
        manager.submit(parent, 0);

        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);

        assertEquals(1, manager.registry().size());
        assertEquals(SimulationWorkStatus.FAILED, manager.runtimeFor(parent.id()).orElseThrow().status());
        assertEquals(WorkFailureCode.DUPLICATE_WORK_ID,
                manager.runtimeFor(parent.id()).orElseThrow().lastFailureCode().orElseThrow());
    }

    @Test
    void pipelineIsNonReentrantEvenFromAHandler() {
        AtomicReference<SimulationPipeline> pipelineReference = new AtomicReference<>();
        AtomicReference<SimulationTickReport> nestedReport = new AtomicReference<>();
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            nestedReport.set(pipelineReference.get().execute(context.authoritativeSimulationTick()));
            return SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1);
        });
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:reentrant", BuiltInSimulationStages.EXECUTION, 0, 1), 0);
        SimulationPipeline pipeline = new SimulationPipeline(manager, SimulationExecutionBudget.standard());
        pipelineReference.set(pipeline);

        assertEquals(PipelineStatus.COMPLETED, pipeline.execute(1).status());
        assertEquals(PipelineStatus.REJECTED, nestedReport.get().status());
        assertEquals(SimulationWorkStatus.COMPLETED,
                manager.runtimeFor(SimulationWorkId.of("test:reentrant")).orElseThrow().status());
    }

    @Test
    void stopStageFailurePolicyPreservesRemainingEligibleWork() {
        SimulationStageDefinition stage = new SimulationStageDefinition(
                SimulationStageId.of("test:stop_stage"), "Stop Stage", 10, StageFailurePolicy.STOP_STAGE,
                true, SchedulerSchema.CURRENT_VERSION
        );
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SchedulerTestFixtures.result(
                        SimulationWorkOutcome.FAILED, context.authoritativeSimulationTick(), 1,
                        OptionalLong.empty(), List.of()
                ));
        SimulationStageRegistry stages = SimulationStageRegistry.of(List.of(stage));
        SimulationSchedulerManager manager = new SimulationSchedulerManager(
                stages, new SimulationWorkHandlerRegistry(List.of(handler)), 0
        );
        manager.submit(SchedulerTestFixtures.request("test:first_failure", stage.id(), 0, 1), 0);
        manager.submit(SchedulerTestFixtures.request("test:preserved", stage.id(), 0, 1), 0);

        SimulationTickReport report = new SimulationPipeline(
                manager, SimulationExecutionBudget.standard()).execute(1);

        assertEquals(PipelineStatus.COMPLETED, report.status());
        assertTrue(report.stageReports().getFirst().stoppedByFailurePolicy());
        assertEquals(SimulationWorkStatus.ELIGIBLE,
                manager.runtimeFor(SimulationWorkId.of("test:preserved")).orElseThrow().status());
        assertFalse(manager.hasRunningWork());
    }

    @Test
    void stopTickAndFailPipelinePoliciesSkipLaterStages() {
        assertEquals(PipelineStatus.STOPPED, executeFailurePolicy(StageFailurePolicy.STOP_TICK));
        assertEquals(PipelineStatus.FAILED, executeFailurePolicy(StageFailurePolicy.FAIL_PIPELINE));
    }

    @Test
    void generationDepthAndHandlerUnitLimitsFailExplicitlyWithoutLeavingRunningWork() {
        SimulationWorkHandler generating = SchedulerTestFixtures.handler(context ->
                SchedulerTestFixtures.result(
                        SimulationWorkOutcome.COMPLETED, context.authoritativeSimulationTick(), 1,
                        OptionalLong.empty(), List.of(SchedulerTestFixtures.request(
                                "test:too_deep_child", BuiltInSimulationStages.EXECUTION, 1, 1
                        ))
                ));
        ScheduledSimulationWork deepWork = ScheduledSimulationWork.fromRequest(
                SchedulerTestFixtures.request(
                        "test:deep_parent", BuiltInSimulationStages.PLANNING, 0, 1
                ), 0, 1
        );
        SimulationSchedulerManager depthManager = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), new SimulationWorkHandlerRegistry(List.of(generating)),
                SimulationSchedulerRegistry.of(List.of(deepWork)),
                List.of(SimulationWorkRuntime.scheduled(deepWork)), 1, 0
        );
        SimulationExecutionBudget depthBudget = new SimulationExecutionBudget(10, 10, 10, 10, 10, 10, 1);
        new SimulationPipeline(depthManager, depthBudget).execute(1);
        assertEquals(WorkFailureCode.BUDGET_EXHAUSTED,
                depthManager.runtimeFor(deepWork.id()).orElseThrow().lastFailureCode().orElseThrow());
        assertEquals(1, depthManager.registry().size());
        assertFalse(depthManager.hasRunningWork());

        SimulationWorkHandler expensive = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 2));
        SimulationSchedulerManager unitManager = SchedulerTestFixtures.manager(expensive, 0);
        SimulationWorkRequest expensiveWork = SchedulerTestFixtures.request(
                "test:expensive", BuiltInSimulationStages.EXECUTION, 0, 1
        );
        unitManager.submit(expensiveWork, 0);
        new SimulationPipeline(unitManager,
                new SimulationExecutionBudget(10, 10, 1, 10, 10, 10, 2)).execute(1);
        assertEquals(WorkFailureCode.BUDGET_EXHAUSTED,
                unitManager.runtimeFor(expensiveWork.id()).orElseThrow().lastFailureCode().orElseThrow());
        assertFalse(unitManager.hasRunningWork());
    }

    private static PipelineStatus executeFailurePolicy(StageFailurePolicy policy) {
        SimulationStageDefinition first = new SimulationStageDefinition(
                SimulationStageId.of("test:first_stage"), "First", 10, policy, true,
                SchedulerSchema.CURRENT_VERSION
        );
        SimulationStageDefinition second = new SimulationStageDefinition(
                SimulationStageId.of("test:second_stage"), "Second", 20, StageFailurePolicy.CONTINUE_STAGE,
                true, SchedulerSchema.CURRENT_VERSION
        );
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            if (context.work().stageId().equals(first.id())) {
                return SchedulerTestFixtures.result(
                        SimulationWorkOutcome.FAILED, 1, 1, OptionalLong.empty(), List.of()
                );
            }
            return SimulationWorkResult.completed(1, 1);
        });
        SimulationSchedulerManager manager = new SimulationSchedulerManager(
                SimulationStageRegistry.of(List.of(first, second)),
                new SimulationWorkHandlerRegistry(List.of(handler)), 0
        );
        manager.submit(SchedulerTestFixtures.request("test:policy_failure", first.id(), 0, 1), 0);
        manager.submit(SchedulerTestFixtures.request("test:later_stage", second.id(), 0, 1), 0);

        PipelineStatus status = new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1)
                .status();
        assertEquals(SimulationWorkStatus.ELIGIBLE,
                manager.runtimeFor(SimulationWorkId.of("test:later_stage")).orElseThrow().status());
        return status;
    }
}
