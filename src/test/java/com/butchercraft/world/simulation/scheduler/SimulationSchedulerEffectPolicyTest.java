package com.butchercraft.world.simulation.scheduler;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSchedulerEffectPolicyTest {
    private static final SimulationWorkTypeId IDEMPOTENT_TYPE =
            SimulationWorkTypeId.of("test:idempotent_work");
    private static final SimulationWorkTypeId TRANSACTION_TYPE =
            SimulationWorkTypeId.of("test:transaction_work");
    private static final SimulationWorkTypeId NON_REPEATABLE_TYPE =
            SimulationWorkTypeId.of("test:non_repeatable_work");

    @Test
    void handlerRegistryRequiresExplicitPoliciesForConsequentialHandlers() {
        SimulationWorkHandler readOnly = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SimulationWorkHandler idempotent = handler(
                IDEMPOTENT_TYPE,
                HandlerEffectType.IDEMPOTENT,
                SchedulerEffectPolicy.idempotent(
                        IDEMPOTENT_TYPE,
                        "test:idempotent_owner",
                        "test idempotent policy"
                ),
                context -> completedWithObservation(context, "same")
        );
        SimulationWorkHandler transactionBacked = handler(
                TRANSACTION_TYPE,
                HandlerEffectType.TRANSACTION_BACKED,
                SchedulerEffectPolicy.transactionBacked(TRANSACTION_TYPE, "test transaction policy"),
                context -> completedWithObservation(context, "transaction")
        );
        SimulationWorkHandler nonRepeatable = handler(
                NON_REPEATABLE_TYPE,
                HandlerEffectType.NON_REPEATABLE,
                SchedulerEffectPolicy.nonRepeatable(
                        NON_REPEATABLE_TYPE,
                        "test:non_repeatable_owner",
                        "test non-repeatable policy"
                ),
                context -> completedWithObservation(context, "non_repeatable")
        );

        SimulationWorkHandlerRegistry registry = new SimulationWorkHandlerRegistry(List.of(
                readOnly, idempotent, transactionBacked, nonRepeatable
        ));

        assertEquals(4, registry.size());
        assertTrue(registry.policies().stream().anyMatch(policy ->
                policy.effectType() == HandlerEffectType.READ_ONLY));
        assertTrue(registry.policies().stream().anyMatch(policy ->
                policy.effectType() == HandlerEffectType.IDEMPOTENT));
        assertTrue(registry.policies().stream().anyMatch(policy ->
                policy.effectType() == HandlerEffectType.TRANSACTION_BACKED));
        assertTrue(registry.policies().stream().anyMatch(policy ->
                policy.effectType() == HandlerEffectType.NON_REPEATABLE));
        assertThrows(IllegalArgumentException.class, () -> new SimulationWorkHandlerRegistry(List.of(
                SchedulerTestFixtures.handler(
                        SimulationWorkTypeId.of("test:missing_policy"),
                        HandlerEffectType.IDEMPOTENT,
                        SchedulerEffectPolicy.defaultFor(
                                SchedulerTestFixtures.TYPE,
                                HandlerEffectType.READ_ONLY
                        ),
                        work -> WorkValidationResult.acceptedResult(),
                        context -> SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1)
                )
        )));
    }

    @Test
    void invocationIdentityIsDeterministicAndBindsAttemptTickPayloadAndPolicy() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SchedulerEffectPolicy policy = handler.effectPolicy();
        ScheduledSimulationWork work = ScheduledSimulationWork.fromRequest(
                SchedulerTestFixtures.request("test:identity", BuiltInSimulationStages.EXECUTION, 0, 1),
                0,
                0
        );
        ScheduledSimulationWork changedPayload = ScheduledSimulationWork.fromRequest(
                new SimulationWorkRequest(
                        SimulationWorkId.of("test:identity_payload"),
                        SchedulerTestFixtures.TYPE,
                        BuiltInSimulationStages.EXECUTION,
                        1,
                        WorkPriority.NORMAL,
                        WorkOrigin.of("test:scheduler", 0, "test:unit_test"),
                        new WorkPayload(List.of(WorkPayloadEntry.string("test:input", "changed"))),
                        RetryPolicy.never(),
                        1,
                        OptionalLong.empty(),
                        List.of()
                ),
                1,
                0
        );

        SchedulerInvocationIdentity first = SchedulerInvocationIdentity.forAttempt(work, handler, 1, 1, policy);
        SchedulerInvocationIdentity repeated = SchedulerInvocationIdentity.forAttempt(work, handler, 1, 1, policy);
        SchedulerInvocationIdentity changedAttempt = SchedulerInvocationIdentity.forAttempt(work, handler, 2, 1, policy);
        SchedulerInvocationIdentity changedTick = SchedulerInvocationIdentity.forAttempt(work, handler, 1, 2, policy);
        SchedulerInvocationIdentity changedPayloadIdentity = SchedulerInvocationIdentity.forAttempt(
                changedPayload,
                handler,
                1,
                1,
                policy
        );

        assertEquals(first, repeated);
        assertNotEquals(first, changedAttempt);
        assertNotEquals(first, changedTick);
        assertNotEquals(first, changedPayloadIdentity);
    }

    @Test
    void effectIdentityRemainsStableAcrossAttemptsWhileInvocationIdentityChanges() {
        SimulationWorkHandler handler = handler(
                IDEMPOTENT_TYPE,
                HandlerEffectType.IDEMPOTENT,
                SchedulerEffectPolicy.idempotent(
                        IDEMPOTENT_TYPE,
                        "test:idempotent_owner",
                        "test idempotent policy"
                ),
                context -> completedWithObservation(context, "stable")
        );
        SchedulerEffectPolicy policy = handler.effectPolicy();
        ScheduledSimulationWork work = ScheduledSimulationWork.fromRequest(
                SchedulerTestFixtures.request(
                        "test:effect_identity",
                        IDEMPOTENT_TYPE,
                        BuiltInSimulationStages.EXECUTION,
                        0,
                        1
                ),
                0,
                0
        );

        assertEquals(
                SchedulerEffectIdentity.forWork(work, handler, policy),
                SchedulerEffectIdentity.forWork(work, handler, policy)
        );
        assertNotEquals(
                SchedulerInvocationIdentity.forAttempt(work, handler, 1, 1, policy),
                SchedulerInvocationIdentity.forAttempt(work, handler, 2, 2, policy)
        );
    }

    @Test
    void sameEffectIdentityWithSameContentIsSafeAndDifferentContentConflicts() {
        assertEquals(SimulationWorkStatus.COMPLETED, observedDuplicate("same", "same").status());

        SimulationWorkRuntime conflict = observedDuplicate("old", "new");

        assertEquals(SimulationWorkStatus.FAILED, conflict.status());
        assertEquals(WorkFailureCode.HANDLER_EFFECT_IDENTITY_CONFLICT,
                conflict.lastFailureCode().orElseThrow());
    }

    @Test
    void transactionBackedCompletionRequiresOwnerResultEvidence() {
        SimulationWorkHandler handler = handler(
                TRANSACTION_TYPE,
                HandlerEffectType.TRANSACTION_BACKED,
                SchedulerEffectPolicy.transactionBacked(TRANSACTION_TYPE, "test transaction policy"),
                context -> SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1)
        );
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:missing_transaction_evidence",
                TRANSACTION_TYPE,
                BuiltInSimulationStages.EXECUTION,
                0,
                1
        );
        manager.submit(request, 0);

        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);

        SimulationWorkRuntime runtime = manager.runtimeFor(request.id()).orElseThrow();
        assertEquals(SimulationWorkStatus.UNKNOWN_OUTCOME, runtime.status());
        assertEquals(WorkFailureCode.HANDLER_TRANSACTION_EVIDENCE_MISSING,
                runtime.lastFailureCode().orElseThrow());
        assertTrue(runtime.nextEligibleTick().isEmpty());
    }

    @Test
    void nonRepeatableExceptionBecomesUnknownOutcomeAndIsNotReinvoked() {
        SimulationWorkHandler handler = handler(
                NON_REPEATABLE_TYPE,
                HandlerEffectType.NON_REPEATABLE,
                SchedulerEffectPolicy.nonRepeatable(
                        NON_REPEATABLE_TYPE,
                        "test:non_repeatable_owner",
                        "test non-repeatable policy"
                ),
                context -> {
                    throw new IllegalStateException("uncertain effect");
                }
        );
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:unknown_outcome",
                NON_REPEATABLE_TYPE,
                BuiltInSimulationStages.EXECUTION,
                0,
                1,
                WorkPriority.NORMAL,
                RetryPolicy.nextTick(),
                2
        );
        manager.submit(request, 0);

        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);
        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(2);

        SimulationWorkRuntime runtime = manager.runtimeFor(request.id()).orElseThrow();
        assertEquals(SimulationWorkStatus.UNKNOWN_OUTCOME, runtime.status());
        assertEquals(1, runtime.attemptCount());
        assertEquals(WorkFailureCode.NON_REPEATABLE_OUTCOME_UNKNOWN,
                runtime.lastFailureCode().orElseThrow());
    }

    @Test
    void nonRepeatablePreEffectValidationRejectionFailsWithoutUnknownOutcome() {
        AtomicBoolean rejectDuringExecutionValidation = new AtomicBoolean();
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(
                NON_REPEATABLE_TYPE,
                HandlerEffectType.NON_REPEATABLE,
                SchedulerEffectPolicy.nonRepeatable(
                        NON_REPEATABLE_TYPE,
                        "test:non_repeatable_owner",
                        "test non-repeatable policy"
                ),
                work -> rejectDuringExecutionValidation.get()
                        ? WorkValidationResult.rejected(WorkFailureCode.INVALID_PAYLOAD, "pre-effect rejection")
                        : WorkValidationResult.acceptedResult(),
                context -> SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1)
        );
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:pre_effect_reject",
                NON_REPEATABLE_TYPE,
                BuiltInSimulationStages.EXECUTION,
                0,
                1
        );
        manager.submit(request, 0);
        rejectDuringExecutionValidation.set(true);

        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);

        SimulationWorkRuntime runtime = manager.runtimeFor(request.id()).orElseThrow();
        assertEquals(SimulationWorkStatus.FAILED, runtime.status());
        assertEquals(0, runtime.attemptCount());
        assertEquals(WorkFailureCode.INVALID_PAYLOAD, runtime.lastFailureCode().orElseThrow());
    }

    @Test
    void recursiveAndParallelExecutionUseWorldScopedRuntimeAuthority() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            handlerStarted.countDown();
            try {
                assertTrue(releaseHandler.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1);
        });
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:parallel_authority",
                BuiltInSimulationStages.EXECUTION,
                0,
                1
        ), 0);
        SimulationPipeline first = new SimulationPipeline(manager, SimulationExecutionBudget.standard());
        SimulationPipeline second = new SimulationPipeline(manager, SimulationExecutionBudget.standard());
        try {
            var firstReport = executor.submit(() -> first.execute(1));
            assertTrue(handlerStarted.await(5, TimeUnit.SECONDS));

            SimulationTickReport rejected = second.execute(1);
            releaseHandler.countDown();

            assertEquals(PipelineStatus.REJECTED, rejected.status());
            assertEquals(WorkFailureCode.SCHEDULER_AUTHORITY_ALREADY_EXECUTING,
                    rejected.failureCode().orElseThrow());
            assertEquals(PipelineStatus.COMPLETED, firstReport.get(5, TimeUnit.SECONDS).status());
        } finally {
            releaseHandler.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void activeCancellationIsExplicitlyUnsupported() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:active_cancel",
                BuiltInSimulationStages.EXECUTION,
                0,
                1
        );
        manager.submit(request, 0);
        manager.promoteDue(1, 1);
        manager.start(request.id(), 1);

        SchedulerOperationResult cancelled = manager.cancel(request.id(), 1, "stop");

        assertEquals(WorkFailureCode.ACTIVE_CANCELLATION_UNSUPPORTED,
                cancelled.failureCode().orElseThrow());
    }

    private static SimulationWorkRuntime observedDuplicate(String existingContent, String returnedContent) {
        SimulationWorkHandler handler = handler(
                IDEMPOTENT_TYPE,
                HandlerEffectType.IDEMPOTENT,
                SchedulerEffectPolicy.idempotent(
                        IDEMPOTENT_TYPE,
                        "test:idempotent_owner",
                        "test idempotent policy"
                ),
                context -> completedWithObservation(context, returnedContent)
        );
        SchedulerEffectPolicy policy = handler.effectPolicy();
        SimulationWorkRequest request = SchedulerTestFixtures.request(
                "test:observed_duplicate",
                IDEMPOTENT_TYPE,
                BuiltInSimulationStages.EXECUTION,
                0,
                1
        );
        ScheduledSimulationWork work = ScheduledSimulationWork.fromRequest(request, 0, 0);
        SchedulerEffectIdentity effectIdentity = SchedulerEffectIdentity.forWork(work, handler, policy);
        SimulationWorkRuntime runtime = new SimulationWorkRuntime(
                work.id(),
                SimulationWorkStatus.SCHEDULED,
                0,
                0,
                OptionalLong.empty(),
                OptionalLong.empty(),
                OptionalLong.of(1),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                WorkPayload.empty(),
                java.util.Optional.empty(),
                java.util.Optional.of(effectIdentity),
                java.util.Optional.of(HandlerEffectType.IDEMPOTENT),
                java.util.Optional.of(policy.policyIdentity()),
                java.util.Optional.of("test:idempotent_owner"),
                java.util.Optional.of("test:idempotent_owner_result/same"),
                java.util.Optional.of(digest(existingContent)),
                0,
                SchedulerSchema.CURRENT_VERSION
        );
        SimulationSchedulerManager manager = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                new SimulationWorkHandlerRegistry(List.of(handler)),
                SimulationSchedulerRegistry.of(List.of(work)),
                List.of(runtime),
                1,
                0
        );

        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1);

        return manager.runtimeFor(work.id()).orElseThrow();
    }

    private static SimulationWorkHandler handler(
            SimulationWorkTypeId type,
            HandlerEffectType effectType,
            SchedulerEffectPolicy policy,
            java.util.function.Function<SimulationExecutionContext, SimulationWorkResult> execution
    ) {
        return SchedulerTestFixtures.handler(
                type,
                effectType,
                policy,
                work -> WorkValidationResult.acceptedResult(),
                execution
        );
    }

    private static SimulationWorkResult completedWithObservation(
            SimulationExecutionContext context,
            String content
    ) {
        SchedulerEffectObservation observation = SchedulerEffectObservation.of(
                context.effectIdentity().orElseThrow(),
                context.effectPolicy().effectType(),
                context.effectPolicy().ownerSubsystemId(),
                context.effectPolicy().ownerSubsystemId() + "_result/same",
                digest(content)
        );
        return SimulationWorkResult.completed(
                context.authoritativeSimulationTick(),
                1,
                WorkPayload.empty(),
                observation
        );
    }

    private static String digest(String value) {
        return SchedulerCanonicalDigest.create("test:scheduler_effect_result").add(value).finish();
    }
}
