package com.butchercraft.world.execution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MachineRunRegistryTest {
    private static final MachineRunConfiguration RUN_CONFIGURATION = MachineRunConfiguration.standard();
    private static final String INSTANCE = "butchercraft:workstation_instance/v1/" + "c".repeat(64);
    private static final String POLICY = "butchercraft:machine_operating_policy/test";

    @Test
    void duplicateAndConcurrentStartsPreserveOneActiveRun() {
        MachineRunRegistry registry = MachineRunRegistry.empty(ExecutionTestFixtures.WORLD_IDENTITY, RUN_CONFIGURATION);
        MachineStartAuthorizationEvidence firstRequest = start("test:start/one", 1L);
        MachineRunRegistryMutation first = registry.acceptStart(firstRequest, RUN_CONFIGURATION, 1L);

        MachineRunRegistryMutation duplicate = first.registry().acceptStart(firstRequest, RUN_CONFIGURATION, 2L);
        MachineRunRegistryMutation concurrent = first.registry().acceptStart(
                start("test:start/two", 2L),
                RUN_CONFIGURATION,
                2L
        );

        assertEquals(MachineRunResultCode.ACCEPTED, first.code());
        assertEquals(MachineRunResultCode.EXISTING_RUN, duplicate.code());
        assertEquals(first.run().orElseThrow().runIdentity(), duplicate.run().orElseThrow().runIdentity());
        assertEquals(MachineRunResultCode.ALREADY_RUNNING, concurrent.code());
        assertEquals(1, first.registry().runs().size());
        assertEquals(2L, first.registry().generationAllocators().getFirst().nextGeneration());
    }

    @Test
    void stopTargetsExactRunAndOldStopCannotAffectLaterGeneration() {
        MachineRunRegistry registry = startedRegistry();
        MachineRunRecord firstRun = registry.activeFor(INSTANCE).orElseThrow();
        MachineStopAuthorizationEvidence firstStop = stop(firstRun, "test:stop/one", 4L);
        MachineRunRegistryMutation stopping = registry.acceptStop(firstStop, 4L);
        MachineRunRegistryMutation stopped = stopping.registry().completeStop(firstRun.runIdentity(), 5L);
        MachineRunRegistryMutation secondStart = stopped.registry().acceptStart(
                start("test:start/two", 6L),
                RUN_CONFIGURATION,
                6L
        );
        MachineRunRecord secondRun = secondStart.run().orElseThrow();

        MachineRunRegistryMutation staleStop = secondStart.registry().acceptStop(
                stop(firstRun, "test:stop/stale", 7L),
                7L
        );

        assertEquals(MachineRunResultCode.STALE_RUN, staleStop.code());
        assertNotEquals(firstRun.runIdentity(), secondRun.runIdentity());
        assertEquals(2L, secondRun.generation());
        assertEquals(MachineRunLifecycle.AUTHORIZED,
                staleStop.registry().find(secondRun.runIdentity()).orElseThrow().lifecycle());
    }

    @Test
    void oneNonterminalChildBlocksTheNextAndStopBlocksFurtherAdmission() {
        MachineRunRegistry registry = startedRegistry();
        MachineRunRecord run = registry.activeFor(INSTANCE).orElseThrow();
        ExecutionAuthorization firstAuthorization = childAuthorization(run, 1L, "test:child/one");
        MachineRunRegistryMutation firstPrepared = registry.prepareChild(
                run.runIdentity(),
                run.revision(),
                firstAuthorization.evidence(),
                RUN_CONFIGURATION,
                2L
        );
        MachineRunRecord preparedRun = firstPrepared.run().orElseThrow();
        ExecutionAuthorization secondAuthorization = childAuthorization(preparedRun, 2L, "test:child/two");

        MachineRunRegistryMutation blocked = firstPrepared.registry().prepareChild(
                run.runIdentity(),
                preparedRun.revision(),
                secondAuthorization.evidence(),
                RUN_CONFIGURATION,
                3L
        );

        assertEquals(MachineRunResultCode.CHILD_ALREADY_ACTIVE, blocked.code());
        assertEquals(1L, preparedRun.currentChild().orElseThrow().sequence());

        ExecutionManager execution = executionManager();
        ExecutionOperationSnapshot operation = execution.acceptAuthorization(firstAuthorization, 2L)
                .value().orElseThrow();
        MachineRunRegistryMutation admitted = firstPrepared.registry().admitPreparedChild(
                run.runIdentity(),
                operation,
                2L
        );
        ExecutionOperationSnapshot cancelled = execution.cancelBeforeStart(
                operation.operationId(),
                4L,
                "safe stop boundary"
        ).value().orElseThrow();
        MachineRunRegistryMutation observed = admitted.registry().observeChild(run.runIdentity(), cancelled, 4L);
        MachineRunRecord childComplete = observed.run().orElseThrow();
        assertTrue(childComplete.currentChild().isEmpty());
        assertEquals(MachineRunChildState.CANCELLED,
                childComplete.terminalChildren().getFirst().state());

        MachineRunRegistryMutation stop = observed.registry().acceptStop(
                stop(childComplete, "test:stop/child", 5L),
                5L
        );
        MachineRunRegistryMutation afterStop = stop.registry().prepareChild(
                run.runIdentity(),
                stop.run().orElseThrow().revision(),
                secondAuthorization.evidence(),
                RUN_CONFIGURATION,
                6L
        );
        assertEquals(MachineRunResultCode.INVALID_LIFECYCLE, afterStop.code());
    }

    @Test
    void restartPolicyPreservesExactRunIdentityAndGeneration() {
        MachineRunRegistry registry = startedRegistry();
        MachineRunRecord run = registry.activeFor(INSTANCE).orElseThrow();

        MachineRunRegistryMutation suspended = registry.suspendForRestart(run.runIdentity(), 10L);
        MachineRunRecord restartRequired = suspended.run().orElseThrow();
        MachineRunRegistryMutation resumed = suspended.registry().resume(
                run.runIdentity(),
                restartRequired.revision(),
                11L
        );

        assertEquals(MachineRunLifecycle.SUSPENDED_RESTART_REQUIRED, restartRequired.lifecycle());
        assertEquals(MachineRunLifecycle.AUTHORIZED, resumed.run().orElseThrow().lifecycle());
        assertEquals(run.runIdentity(), resumed.run().orElseThrow().runIdentity());
        assertEquals(run.generation(), resumed.run().orElseThrow().generation());
        assertEquals(2L, resumed.registry().generationAllocators().getFirst().nextGeneration());
    }

    @Test
    void preparedChildCanBeCancelledBeforeExecutionAdmissionWithoutReuse() {
        MachineRunRegistry registry = startedRegistry();
        MachineRunRecord run = registry.activeFor(INSTANCE).orElseThrow();
        ExecutionAuthorization authorization = childAuthorization(run, 1L, "test:child/rejected");
        MachineRunRegistryMutation prepared = registry.prepareChild(
                run.runIdentity(), run.revision(), authorization.evidence(), RUN_CONFIGURATION, 2L);

        MachineRunRegistryMutation cancelled = prepared.registry().cancelPreparedChild(
                run.runIdentity(),
                authorization.operationId(),
                MachineRunResultCode.CHILD_RESULT_CONFLICT,
                3L
        );

        assertTrue(cancelled.run().orElseThrow().currentChild().isEmpty());
        assertEquals(2L, cancelled.run().orElseThrow().nextChildSequence());
        assertEquals(MachineRunChildState.CANCELLED,
                cancelled.run().orElseThrow().terminalChildren().getFirst().state());
        assertFalse(executionManager().find(authorization.operationId()).isPresent());
    }

    @Test
    void runAndChildTransitionsRejectBackwardSimulationTicks() {
        MachineRunRegistry registry = startedRegistry();
        MachineRunRecord run = registry.activeFor(INSTANCE).orElseThrow();
        MachineRunRecord suspended = registry.suspendForRestart(run.runIdentity(), 10L).run().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> suspended.resume(9L));

        ExecutionAuthorization authorization = childAuthorization(run, 1L, "test:child/tick");
        MachineRunRecord prepared = registry.prepareChild(
                run.runIdentity(), run.revision(), authorization.evidence(), RUN_CONFIGURATION, 10L
        ).run().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> prepared.cancelPreparedChild(
                authorization.operationId(),
                9L,
                MachineRunResultCode.CHILD_RESULT_CONFLICT
        ));
    }

    private static MachineRunRegistry startedRegistry() {
        MachineRunRegistry registry = MachineRunRegistry.empty(ExecutionTestFixtures.WORLD_IDENTITY, RUN_CONFIGURATION);
        return registry.acceptStart(start("test:start/one", 1L), RUN_CONFIGURATION, 1L).registry();
    }

    private static MachineStartAuthorizationEvidence start(String request, long tick) {
        return MachineStartAuthorizationEvidence.issued(
                ExecutionTestFixtures.WORLD_IDENTITY,
                INSTANCE,
                0L,
                "test:machine_control",
                request,
                POLICY,
                RUN_CONFIGURATION.configurationIdentity(),
                tick
        );
    }

    private static MachineStopAuthorizationEvidence stop(MachineRunRecord run, String request, long tick) {
        return MachineStopAuthorizationEvidence.issued(
                ExecutionTestFixtures.WORLD_IDENTITY,
                INSTANCE,
                run.runIdentity(),
                run.revision(),
                2L,
                "test:machine_control",
                request,
                RUN_CONFIGURATION.configurationIdentity(),
                tick
        );
    }

    private static ExecutionAuthorization childAuthorization(
            MachineRunRecord run,
            long sequence,
            String executableReference
    ) {
        ExecutionAuthorizationEvidence evidence = ExecutionAuthorizationEvidence.issued(
                ExecutionTestFixtures.AUTHORITY_OWNER,
                ExecutionTestFixtures.EXECUTABLE_REFERENCE_TYPE,
                executableReference,
                ExecutionTestFixtures.OPERATION_TYPE,
                ExecutionTestFixtures.HANDLER_ID,
                "test:frozen/" + sequence,
                "test:freshness/" + sequence,
                ExecutionTestFixtures.CONFIGURATION.configurationIdentity(),
                ExecutionTestFixtures.WORLD_IDENTITY,
                2L,
                OptionalLong.empty(),
                List.of(
                        run.runIdentity().value(),
                        run.workstationInstanceIdentity(),
                        MachineRunRecord.childSequenceIdentity(sequence),
                        run.operatingPolicyIdentity()
                )
        );
        return ExecutionAuthorization.issue(evidence);
    }

    private static ExecutionManager executionManager() {
        return new ExecutionManager(
                ExecutionTestFixtures.registry(ExecutionTestFixtures::ownerResult),
                ExecutionTestFixtures.CONFIGURATION
        );
    }
}
