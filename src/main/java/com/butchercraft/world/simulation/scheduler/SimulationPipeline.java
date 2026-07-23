package com.butchercraft.world.simulation.scheduler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SimulationPipeline {
    private final SimulationSchedulerManager manager;
    private final SimulationExecutionBudget budget;
    private final AtomicBoolean executing = new AtomicBoolean();

    public SimulationPipeline(SimulationSchedulerManager manager, SimulationExecutionBudget budget) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public SimulationTickReport execute(long authoritativeSimulationTick) {
        SchedulerValidation.requireTick(authoritativeSimulationTick, "Authoritative pipeline tick");
        if (!executing.compareAndSet(false, true)) {
            return SimulationTickReport.rejected(
                    authoritativeSimulationTick, WorkFailureCode.INVALID_STATUS,
                    "The simulation pipeline is already executing"
            );
        }
        try {
            return executeExclusive(authoritativeSimulationTick);
        } finally {
            executing.set(false);
        }
    }

    private SimulationTickReport executeExclusive(long tick) {
        long expectedTick;
        try {
            expectedTick = Math.addExact(manager.lastFinalizedSimulationTick(), 1L);
        } catch (ArithmeticException exception) {
            return SimulationTickReport.rejected(
                    tick, WorkFailureCode.INTERNAL_INVARIANT_VIOLATION,
                    "The scheduler tick sequence has overflowed"
            );
        }
        if (tick != expectedTick) {
            WorkFailureCode code = tick < expectedTick ? WorkFailureCode.BACKWARD_TICK : WorkFailureCode.INVALID_TICK;
            return SimulationTickReport.rejected(
                    tick, code, "Pipeline expected authoritative tick " + expectedTick + " but received " + tick
            );
        }

        TickAccumulator totals = new TickAccumulator(tick);
        EligibilityUpdate eligibility = manager.promoteDue(tick, budget.maximumWorkItemsPerTick());
        totals.promoted += eligibility.promoted();
        totals.expired += eligibility.expired();
        Set<SimulationStageId> completedStages = new HashSet<>();

        boolean stopTick = false;
        for (SimulationStageDefinition stage : manager.stageRegistry().definitions()) {
            if (stopTick) break;
            StageAccumulator stageTotals = new StageAccumulator(stage.id());
            List<ScheduledSimulationWork> eligible = manager.eligibleForStage(stage.id());
            for (ScheduledSimulationWork work : eligible) {
                if (totals.attempted >= budget.maximumWorkItemsPerTick()
                        || stageTotals.attempted >= budget.maximumWorkItemsPerStage()
                        || totals.workUnits >= budget.maximumHandlerWorkUnits()) {
                    stageTotals.budgetExhausted = true;
                    totals.budgetExhausted = true;
                    break;
                }

                ExecutionDisposition disposition = executeWork(
                        tick, stage, work, completedStages, totals, stageTotals
                );
                if (disposition.failed()) {
                    switch (stage.defaultFailurePolicy()) {
                        case CONTINUE_STAGE -> { }
                        case STOP_STAGE -> stageTotals.stopped = true;
                        case STOP_TICK -> {
                            stageTotals.stopped = true;
                            totals.stopped = true;
                            stopTick = true;
                        }
                        case FAIL_PIPELINE -> {
                            stageTotals.stopped = true;
                            totals.pipelineFailed = true;
                            stopTick = true;
                        }
                    }
                    if (stageTotals.stopped) break;
                }
            }
            if (!manager.eligibleForStage(stage.id()).isEmpty()) {
                stageTotals.budgetExhausted = stageTotals.budgetExhausted
                        || stageTotals.attempted >= budget.maximumWorkItemsPerStage()
                        || totals.attempted >= budget.maximumWorkItemsPerTick()
                        || totals.workUnits >= budget.maximumHandlerWorkUnits();
                totals.budgetExhausted |= stageTotals.budgetExhausted;
            }
            totals.stageReports.add(stageTotals.report());
            completedStages.add(stage.id());
        }

        if (manager.hasEligibleWork() || manager.hasDueWork(tick)) {
            totals.budgetExhausted |= totals.attempted >= budget.maximumWorkItemsPerTick()
                    || totals.workUnits >= budget.maximumHandlerWorkUnits()
                    || totals.promoted + totals.expired >= budget.maximumWorkItemsPerTick();
        }
        manager.finalizeTick(tick);
        return totals.report();
    }

    private ExecutionDisposition executeWork(
            long tick,
            SimulationStageDefinition stage,
            ScheduledSimulationWork work,
            Set<SimulationStageId> completedStages,
            TickAccumulator totals,
            StageAccumulator stageTotals
    ) {
        totals.attempted++;
        stageTotals.attempted++;
        SimulationWorkRuntime running = manager.start(work.id(), tick);
        SimulationWorkHandler handler = manager.handlerRegistry().find(work.typeId()).orElse(null);
        if (handler == null) {
            return failWork(work, tick, WorkFailureCode.HANDLER_NOT_REGISTERED,
                    "No handler is registered for this work type", 0, List.of(), totals, stageTotals);
        }

        WorkValidationResult validation;
        try {
            validation = Objects.requireNonNull(handler.validate(work), "handler validation result");
        } catch (RuntimeException exception) {
            return failWork(work, tick, WorkFailureCode.HANDLER_EXCEPTION,
                    exceptionDiagnostic("Handler validation threw", exception), 0, List.of(), totals, stageTotals);
        }
        if (!validation.accepted()) {
            WorkFailureCode code = validation.failureCode().orElse(WorkFailureCode.INVALID_PAYLOAD);
            String message = firstMessage(validation.messages(), "Handler rejected the persisted payload");
            return failWork(work, tick, code, message, 0, validation.messages(), totals, stageTotals);
        }

        SimulationExecutionContext context = new SimulationExecutionContext(
                tick, stage, work, running, running.attemptCount(),
                budget.maximumWorkItemsPerTick() - totals.attempted,
                budget.maximumWorkItemsPerStage() - stageTotals.attempted,
                budget.maximumHandlerWorkUnits() - totals.workUnits,
                work.generationDepth()
        );
        SimulationWorkResult result;
        try {
            result = Objects.requireNonNull(handler.execute(context), "handler execution result");
        } catch (RuntimeException exception) {
            return failWork(work, tick, WorkFailureCode.HANDLER_EXCEPTION,
                    exceptionDiagnostic("Handler execution threw", exception), 0, List.of(), totals, stageTotals);
        }

        if (result.executionTick() != tick) {
            return failWork(work, tick, WorkFailureCode.INVALID_TICK,
                    "Handler result execution tick does not match the authoritative tick", 0,
                    result.diagnosticMessages(), totals, stageTotals);
        }
        if (result.workUnitsConsumed() > budget.maximumHandlerWorkUnits() - totals.workUnits) {
            return failWork(work, tick, WorkFailureCode.BUDGET_EXHAUSTED,
                    "Handler result exceeds the remaining deterministic work-unit budget", 0,
                    result.diagnosticMessages(), totals, stageTotals);
        }

        PreparedGeneratedBatch generated = prepareGeneratedBatch(
                tick, stage, work, completedStages, result.generatedWork(), totals
        );
        if (!generated.accepted()) {
            return failWork(work, tick, generated.failureCode(), generated.message(),
                    result.workUnitsConsumed(), result.diagnosticMessages(), totals, stageTotals);
        }

        WorkAction action = prepareAction(tick, work, running, result, totals);
        if (!action.accepted()) {
            return failWork(work, tick, action.failureCode(), action.message(),
                    result.workUnitsConsumed(), result.diagnosticMessages(), totals, stageTotals);
        }

        WorkBatchSubmissionResult submitted = manager.submitBatch(
                generated.requests(), tick,
                generated.requests().isEmpty() ? 0 : Math.addExact(work.generationDepth(), 1)
        );
        if (!submitted.accepted()) {
            return failWork(work, tick, submitted.failureCode().orElseThrow(),
                    firstMessage(submitted.messages(), "Generated work batch was rejected"),
                    result.workUnitsConsumed(), result.diagnosticMessages(), totals, stageTotals);
        }

        applyAction(work, tick, result, action);
        totals.workUnits += result.workUnitsConsumed();
        stageTotals.workUnits += result.workUnitsConsumed();
        totals.generated += submitted.work().size();
        totals.sameTickGenerated += generated.sameTickCount();
        if (action.retry()) totals.retries++;

        int remainingPromotions = Math.max(0, budget.maximumWorkItemsPerTick() - totals.attempted);
        if (remainingPromotions > 0 && generated.sameTickCount() > 0) {
            EligibilityUpdate update = manager.promoteDue(tick, remainingPromotions);
            totals.promoted += update.promoted();
            totals.expired += update.expired();
        }

        SimulationWorkRuntime finalRuntime = manager.runtimeFor(work.id()).orElseThrow();
        stageTotals.record(finalRuntime.status());
        stageTotals.results.add(new SimulationWorkExecutionSummary(
                work.id(), work.typeId(), finalRuntime.status(), Optional.of(result.outcome()),
                finalRuntime.lastFailureCode(), result.diagnosticMessages(), result.workUnitsConsumed(),
                submitted.work().size()
        ));
        return new ExecutionDisposition(finalRuntime.status() == SimulationWorkStatus.FAILED);
    }

    private PreparedGeneratedBatch prepareGeneratedBatch(
            long tick,
            SimulationStageDefinition currentStage,
            ScheduledSimulationWork parent,
            Set<SimulationStageId> completedStages,
            List<SimulationWorkRequest> requests,
            TickAccumulator totals
    ) {
        if (requests.isEmpty()) return PreparedGeneratedBatch.accepted(List.of(), 0);
        if (parent.generationDepth() >= budget.maximumGenerationDepth()) {
            return PreparedGeneratedBatch.rejected(WorkFailureCode.BUDGET_EXHAUSTED,
                    "Generated work exceeds the maximum generation depth");
        }
        if (requests.size() > budget.maximumGeneratedWorkItems() - totals.generated) {
            return PreparedGeneratedBatch.rejected(WorkFailureCode.BUDGET_EXHAUSTED,
                    "Generated work exceeds the per-tick generation budget");
        }
        List<SimulationWorkRequest> adjusted = new ArrayList<>(requests.size());
        int sameTick = 0;
        try {
            for (SimulationWorkRequest request : requests) {
                if (request.scheduledTick() < tick) {
                    return PreparedGeneratedBatch.rejected(WorkFailureCode.BACKWARD_TICK,
                            "Generated work cannot target an earlier simulation tick");
                }
                SimulationStageDefinition target = manager.stageRegistry().find(request.stageId()).orElse(null);
                if (target == null) {
                    return PreparedGeneratedBatch.rejected(WorkFailureCode.UNKNOWN_STAGE,
                            "Generated work references an unknown stage");
                }
                long scheduledTick = request.scheduledTick();
                boolean laterUnstartedStage = target.executionOrder() > currentStage.executionOrder()
                        && !completedStages.contains(target.id());
                if (scheduledTick == tick && (!laterUnstartedStage || !target.allowsSameTickEnqueue())) {
                    scheduledTick = Math.addExact(tick, 1L);
                } else if (scheduledTick == tick) {
                    sameTick++;
                }
                WorkOrigin origin = request.origin().withParent(parent.id(), tick);
                adjusted.add(copyRequest(request, scheduledTick, origin));
            }
        } catch (RuntimeException exception) {
            return PreparedGeneratedBatch.rejected(WorkFailureCode.VALIDATION_FAILED,
                    exceptionDiagnostic("Generated work is invalid", exception));
        }
        if (sameTick > budget.maximumSameTickSubmissions() - totals.sameTickGenerated) {
            return PreparedGeneratedBatch.rejected(WorkFailureCode.BUDGET_EXHAUSTED,
                    "Generated work exceeds the same-tick submission budget");
        }
        return PreparedGeneratedBatch.accepted(adjusted, sameTick);
    }

    private WorkAction prepareAction(
            long tick,
            ScheduledSimulationWork work,
            SimulationWorkRuntime running,
            SimulationWorkResult result,
            TickAccumulator totals
    ) {
        return switch (result.outcome()) {
            case COMPLETED -> WorkAction.accepted(false, OptionalLong.empty());
            case DEFERRED -> {
                if (running.attemptCount() >= work.maximumAttempts()) {
                    yield WorkAction.rejected(WorkFailureCode.RETRY_LIMIT_REACHED,
                            "Deferred work has no remaining execution attempt");
                }
                long next = result.nextEligibleTick().orElseGet(() -> Math.addExact(tick, 1L));
                if (next <= tick) yield WorkAction.rejected(WorkFailureCode.INVALID_TICK,
                        "Deferred work must target a later simulation tick");
                yield WorkAction.accepted(false, OptionalLong.of(next));
            }
            case RETRY -> {
                if (totals.retries >= budget.maximumRetryTransitions()) {
                    yield WorkAction.rejected(WorkFailureCode.BUDGET_EXHAUSTED,
                            "Retry transition budget is exhausted");
                }
                if (running.attemptCount() >= work.maximumAttempts()
                        || work.retryPolicy().type() == RetryPolicyType.NEVER) {
                    yield WorkAction.rejected(WorkFailureCode.RETRY_LIMIT_REACHED,
                            "Work has no remaining permitted retry attempt");
                }
                try {
                    long next = work.retryPolicy().nextEligibleTick(
                            tick, running.attemptCount(), result.nextEligibleTick()
                    );
                    yield WorkAction.accepted(true, OptionalLong.of(next));
                } catch (RuntimeException exception) {
                    yield WorkAction.rejected(WorkFailureCode.RETRY_LIMIT_REACHED,
                            exceptionDiagnostic("Retry policy rejected the transition", exception));
                }
            }
            case FAILED -> WorkAction.accepted(false, OptionalLong.empty());
        };
    }

    private void applyAction(
            ScheduledSimulationWork work, long tick, SimulationWorkResult result, WorkAction action
    ) {
        String diagnostic = firstMessage(result.diagnosticMessages(), "Work handler returned " + result.outcome());
        switch (result.outcome()) {
            case COMPLETED -> manager.complete(work.id(), tick, result.resultMetadata());
            case DEFERRED -> manager.defer(work.id(), tick, action.nextEligibleTick().orElseThrow(), diagnostic);
            case RETRY -> manager.retry(
                    work.id(), tick, action.nextEligibleTick().orElseThrow(), WorkFailureCode.HANDLER_REJECTED,
                    diagnostic
            );
            case FAILED -> manager.fail(
                    work.id(), tick, result.failureCode().orElse(WorkFailureCode.UNKNOWN), diagnostic
            );
        }
    }

    private ExecutionDisposition failWork(
            ScheduledSimulationWork work,
            long tick,
            WorkFailureCode code,
            String message,
            int workUnits,
            List<String> diagnostics,
            TickAccumulator totals,
            StageAccumulator stageTotals
    ) {
        manager.fail(work.id(), tick, code, message);
        totals.workUnits += workUnits;
        stageTotals.workUnits += workUnits;
        stageTotals.record(SimulationWorkStatus.FAILED);
        List<String> messages = diagnostics.isEmpty() ? List.of(message) : diagnostics;
        stageTotals.results.add(new SimulationWorkExecutionSummary(
                work.id(), work.typeId(), SimulationWorkStatus.FAILED,
                Optional.of(SimulationWorkOutcome.FAILED), Optional.of(code), messages, workUnits, 0
        ));
        return new ExecutionDisposition(true);
    }

    private static SimulationWorkRequest copyRequest(
            SimulationWorkRequest request, long scheduledTick, WorkOrigin origin
    ) {
        return new SimulationWorkRequest(
                request.id(), request.typeId(), request.stageId(), scheduledTick, request.priority(), origin,
                request.payload(), request.retryPolicy(), request.maximumAttempts(), request.expirationTick(),
                request.references()
        );
    }

    private static String firstMessage(List<String> messages, String fallback) {
        return messages.isEmpty() ? fallback : messages.getFirst();
    }

    private static String exceptionDiagnostic(String prefix, RuntimeException exception) {
        String detail = exception.getMessage();
        return prefix + ": " + exception.getClass().getSimpleName()
                + (detail == null || detail.isBlank() ? "" : " (" + detail + ")");
    }

    private record ExecutionDisposition(boolean failed) { }

    private record PreparedGeneratedBatch(
            boolean accepted,
            List<SimulationWorkRequest> requests,
            int sameTickCount,
            WorkFailureCode failureCode,
            String message
    ) {
        private static PreparedGeneratedBatch accepted(List<SimulationWorkRequest> requests, int sameTickCount) {
            return new PreparedGeneratedBatch(true, List.copyOf(requests), sameTickCount,
                    WorkFailureCode.UNKNOWN, "");
        }

        private static PreparedGeneratedBatch rejected(WorkFailureCode code, String message) {
            return new PreparedGeneratedBatch(false, List.of(), 0, code, message);
        }
    }

    private record WorkAction(
            boolean accepted,
            boolean retry,
            OptionalLong nextEligibleTick,
            WorkFailureCode failureCode,
            String message
    ) {
        private static WorkAction accepted(boolean retry, OptionalLong nextEligibleTick) {
            return new WorkAction(true, retry, nextEligibleTick, WorkFailureCode.UNKNOWN, "");
        }

        private static WorkAction rejected(WorkFailureCode code, String message) {
            return new WorkAction(false, false, OptionalLong.empty(), code, message);
        }
    }

    private static final class StageAccumulator {
        private final SimulationStageId stageId;
        private final List<SimulationWorkExecutionSummary> results = new ArrayList<>();
        private int attempted;
        private int completed;
        private int deferred;
        private int retrying;
        private int failed;
        private long workUnits;
        private boolean budgetExhausted;
        private boolean stopped;

        private StageAccumulator(SimulationStageId stageId) { this.stageId = stageId; }

        private void record(SimulationWorkStatus status) {
            switch (status) {
                case COMPLETED -> completed++;
                case DEFERRED -> deferred++;
                case RETRY_WAIT -> retrying++;
                case FAILED -> failed++;
                default -> { }
            }
        }

        private SimulationStageReport report() {
            return new SimulationStageReport(
                    stageId, attempted, completed, deferred, retrying, failed, workUnits,
                    budgetExhausted, stopped, results
            );
        }
    }

    private static final class TickAccumulator {
        private final long tick;
        private final List<SimulationStageReport> stageReports = new ArrayList<>();
        private int promoted;
        private int expired;
        private int attempted;
        private int generated;
        private int sameTickGenerated;
        private int retries;
        private long workUnits;
        private boolean budgetExhausted;
        private boolean stopped;
        private boolean pipelineFailed;

        private TickAccumulator(long tick) { this.tick = tick; }

        private SimulationTickReport report() {
            PipelineStatus status = pipelineFailed ? PipelineStatus.FAILED
                    : stopped ? PipelineStatus.STOPPED
                    : budgetExhausted ? PipelineStatus.BUDGET_EXHAUSTED
                    : PipelineStatus.COMPLETED;
            Optional<WorkFailureCode> code = pipelineFailed
                    ? Optional.of(WorkFailureCode.HANDLER_REJECTED) : Optional.empty();
            return new SimulationTickReport(
                    tick, status, code, promoted, expired, attempted, generated, sameTickGenerated,
                    retries, workUnits, stageReports, List.of()
            );
        }
    }
}
