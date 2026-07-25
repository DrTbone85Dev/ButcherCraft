package com.butchercraft.world.simulation.scheduler;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

final class SchedulerTestFixtures {
    static final SimulationWorkTypeId TYPE = SimulationWorkTypeId.of("test:neutral_work");

    private SchedulerTestFixtures() { }

    static SimulationWorkHandler handler(Function<SimulationExecutionContext, SimulationWorkResult> execution) {
        return handler(TYPE, work -> WorkValidationResult.acceptedResult(), execution);
    }

    static SimulationWorkHandler handler(
            SimulationWorkTypeId type,
            Function<ScheduledSimulationWork, WorkValidationResult> validation,
            Function<SimulationExecutionContext, SimulationWorkResult> execution
    ) {
        return handler(
                type,
                HandlerEffectType.READ_ONLY,
                SchedulerEffectPolicy.defaultFor(type, HandlerEffectType.READ_ONLY),
                validation,
                execution
        );
    }

    static SimulationWorkHandler handler(
            SimulationWorkTypeId type,
            HandlerEffectType effectType,
            SchedulerEffectPolicy effectPolicy,
            Function<ScheduledSimulationWork, WorkValidationResult> validation,
            Function<SimulationExecutionContext, SimulationWorkResult> execution
    ) {
        return new SimulationWorkHandler() {
            @Override public SimulationWorkTypeId supportedTypeId() { return type; }
            @Override public HandlerEffectType effectType() { return effectType; }
            @Override public SchedulerEffectPolicy effectPolicy() { return effectPolicy; }
            @Override public WorkValidationResult validate(ScheduledSimulationWork work) {
                return validation.apply(work);
            }
            @Override public SimulationWorkResult execute(SimulationExecutionContext context) {
                return execution.apply(context);
            }
        };
    }

    static SimulationSchedulerManager manager(SimulationWorkHandler handler, long initialTick) {
        return new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), new SimulationWorkHandlerRegistry(List.of(handler)), initialTick
        );
    }

    static SimulationWorkRequest request(String id, SimulationStageId stage, long submissionTick, long scheduledTick) {
        return request(id, stage, submissionTick, scheduledTick, WorkPriority.NORMAL, RetryPolicy.never(), 1);
    }

    static SimulationWorkRequest request(
            String id,
            SimulationWorkTypeId type,
            SimulationStageId stage,
            long submissionTick,
            long scheduledTick
    ) {
        return request(id, type, stage, submissionTick, scheduledTick, WorkPriority.NORMAL, RetryPolicy.never(), 1);
    }

    static SimulationWorkRequest request(
            String id,
            SimulationStageId stage,
            long submissionTick,
            long scheduledTick,
            WorkPriority priority,
            RetryPolicy retryPolicy,
            int maximumAttempts
    ) {
        return request(id, TYPE, stage, submissionTick, scheduledTick, priority, retryPolicy, maximumAttempts);
    }

    static SimulationWorkRequest request(
            String id,
            SimulationWorkTypeId type,
            SimulationStageId stage,
            long submissionTick,
            long scheduledTick,
            WorkPriority priority,
            RetryPolicy retryPolicy,
            int maximumAttempts
    ) {
        return new SimulationWorkRequest(
                SimulationWorkId.of(id), type, stage, scheduledTick, priority,
                WorkOrigin.of("test:scheduler", submissionTick, "test:unit_test"),
                WorkPayload.empty(), retryPolicy, maximumAttempts, OptionalLong.empty(), List.of()
        );
    }

    static SimulationWorkResult result(
            SimulationWorkOutcome outcome,
            long tick,
            int units,
            OptionalLong nextTick,
            List<SimulationWorkRequest> generated
    ) {
        Optional<WorkFailureCode> failure = outcome == SimulationWorkOutcome.FAILED
                ? Optional.of(WorkFailureCode.HANDLER_REJECTED) : Optional.empty();
        return new SimulationWorkResult(
                outcome, failure, List.of("test result"), nextTick, generated,
                WorkPayload.empty(), units, tick
        );
    }
}
