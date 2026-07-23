package com.butchercraft.world.simulation.scheduler;

import com.butchercraft.world.simulation.scheduler.persistence.SimulationSchedulerStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSchedulerStressTest {
    private static final int MILLION = 1_000_000;
    private static final int LARGE_RUNTIME_SET = 100_000;

    @TempDir
    Path temporaryDirectory;

    @Test
    void oneMillionDefinitionsAndOneMillionRuntimeRecordsConstructDeterministically() {
        long started = System.nanoTime();
        long checksum = 0L;
        WorkOrigin origin = WorkOrigin.of("test:stress", 0, "test:stress_test");
        for (int index = 0; index < MILLION; index++) {
            SimulationWorkRequest request = new SimulationWorkRequest(
                    SimulationWorkId.of("test:work_" + index), SchedulerTestFixtures.TYPE,
                    BuiltInSimulationStages.EXECUTION, 1, WorkPriority.NORMAL, origin,
                    WorkPayload.empty(), RetryPolicy.never(), 1, OptionalLong.empty(), List.of()
            );
            ScheduledSimulationWork work = ScheduledSimulationWork.fromRequest(request, index, 0);
            SimulationWorkRuntime runtime = SimulationWorkRuntime.scheduled(work);
            checksum += work.authoritativeSubmissionSequence();
            checksum += runtime.lastUpdatedSimulationTick();
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(499_999_500_000L, checksum);
        System.out.printf("SCHEDULER_STRESS definitions=1000000 runtimes=1000000 duration_ms=%d%n", elapsedMillis);
    }

    @Test
    void oneHundredThousandDueItemsRemainIndexedAndExecutionIsBounded() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        List<SimulationWorkRequest> requests = requests(LARGE_RUNTIME_SET, 0, 1, "test:eligible_");

        long registrationStarted = System.nanoTime();
        WorkBatchSubmissionResult submission = manager.submitBatch(requests, 0, 0);
        long registrationMillis = (System.nanoTime() - registrationStarted) / 1_000_000L;
        long dueStarted = System.nanoTime();
        assertEquals(LARGE_RUNTIME_SET, manager.findDueAt(1).size());
        long dueMillis = (System.nanoTime() - dueStarted) / 1_000_000L;

        SimulationExecutionBudget budget = new SimulationExecutionBudget(250, 250, 250, 10, 10, 10, 3);
        long pipelineStarted = System.nanoTime();
        SimulationTickReport report = new SimulationPipeline(manager, budget).execute(1);
        long pipelineMillis = (System.nanoTime() - pipelineStarted) / 1_000_000L;

        assertTrue(submission.accepted());
        assertEquals(PipelineStatus.BUDGET_EXHAUSTED, report.status());
        assertEquals(250, manager.findByStatus(SimulationWorkStatus.COMPLETED).size());
        assertEquals(99_750, manager.findEligibleAt(1).size());
        System.out.printf(
                "SCHEDULER_STRESS due_items=100000 registration_ms=%d due_query_ms=%d bounded_250_ms=%d%n",
                registrationMillis, dueMillis, pipelineMillis
        );
    }

    @Test
    void oneHundredThousandRetryWaitAndTerminalRecordsValidateAtEquivalentScale() {
        long started = System.nanoTime();
        long revisions = 0L;
        for (int index = 0; index < LARGE_RUNTIME_SET; index++) {
            SimulationWorkId retryId = SimulationWorkId.of("test:retry_" + index);
            SimulationWorkRuntime retry = new SimulationWorkRuntime(
                    retryId, SimulationWorkStatus.RETRY_WAIT, 1, 1, OptionalLong.of(1),
                    OptionalLong.empty(), OptionalLong.of(2), Optional.of(WorkFailureCode.HANDLER_REJECTED),
                    Optional.of("retry"), WorkPayload.empty(), 3, SchedulerSchema.CURRENT_VERSION
            );
            SimulationWorkId terminalId = SimulationWorkId.of("test:terminal_" + index);
            SimulationWorkRuntime terminal = new SimulationWorkRuntime(
                    terminalId, SimulationWorkStatus.COMPLETED, 1, 1, OptionalLong.of(1),
                    OptionalLong.of(1), OptionalLong.empty(), Optional.empty(), Optional.empty(),
                    WorkPayload.empty(), 3, SchedulerSchema.CURRENT_VERSION
            );
            revisions += retry.revision() + terminal.revision();
        }
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;

        assertEquals(600_000L, revisions);
        System.out.printf("SCHEDULER_STRESS retry_wait=100000 terminal=100000 duration_ms=%d%n", elapsedMillis);
    }

    @Test
    void sameTickGenerationRunsAtConfiguredLimitWithoutUnboundedRecursion() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context -> {
            if (!context.work().id().value().equals("test:generation_parent")) {
                return SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1);
            }
            return new SimulationWorkResult(
                    SimulationWorkOutcome.COMPLETED, Optional.empty(), List.of("generated at limit"),
                    OptionalLong.empty(), requests(100, 1, 1, "test:generated_"),
                    WorkPayload.empty(), 1, 1
            );
        });
        SimulationSchedulerManager manager = SchedulerTestFixtures.manager(handler, 0);
        manager.submit(SchedulerTestFixtures.request(
                "test:generation_parent", BuiltInSimulationStages.PLANNING, 0, 1), 0);
        SimulationExecutionBudget budget = new SimulationExecutionBudget(200, 150, 200, 100, 100, 10, 2);

        SimulationTickReport report = new SimulationPipeline(manager, budget).execute(1);

        assertEquals(PipelineStatus.COMPLETED, report.status());
        assertEquals(100, report.generatedWorkItems());
        assertEquals(100, report.sameTickGeneratedWorkItems());
        assertEquals(101, manager.findByStatus(SimulationWorkStatus.COMPLETED).size());
    }

    @Test
    void representativeTenThousandRecordSnapshotRoundTripsDeterministically() {
        SimulationWorkHandler handler = SchedulerTestFixtures.handler(context ->
                SimulationWorkResult.completed(context.authoritativeSimulationTick(), 1));
        SimulationWorkHandlerRegistry handlers = new SimulationWorkHandlerRegistry(List.of(handler));
        SimulationSchedulerManager manager = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(), handlers, 0
        );
        manager.submitBatch(requests(10_000, 0, 5, "test:persisted_"), 0, 0);
        SimulationSchedulerStorage storage = new SimulationSchedulerStorage(
                temporaryDirectory.resolve("simulation_scheduler.json"), handlers, 0
        );

        long saveStarted = System.nanoTime();
        String json = storage.serialize(manager);
        long saveMillis = (System.nanoTime() - saveStarted) / 1_000_000L;
        long loadStarted = System.nanoTime();
        SimulationSchedulerManager loaded = storage.deserialize(json);
        long loadMillis = (System.nanoTime() - loadStarted) / 1_000_000L;

        assertEquals(10_000, loaded.registry().size());
        assertEquals(json, storage.serialize(loaded));
        System.out.printf(
                "SCHEDULER_STRESS persisted_records=10000 json_chars=%d serialize_ms=%d reload_ms=%d%n",
                json.length(), saveMillis, loadMillis
        );
    }

    private static List<SimulationWorkRequest> requests(
            int count, long submissionTick, long scheduledTick, String idPrefix
    ) {
        List<SimulationWorkRequest> requests = new ArrayList<>(count);
        WorkOrigin origin = WorkOrigin.of("test:stress", submissionTick, "test:stress_test");
        for (int index = 0; index < count; index++) {
            requests.add(new SimulationWorkRequest(
                    SimulationWorkId.of(idPrefix + index), SchedulerTestFixtures.TYPE,
                    BuiltInSimulationStages.EXECUTION, scheduledTick, WorkPriority.NORMAL, origin,
                    WorkPayload.empty(), RetryPolicy.never(), 1, OptionalLong.empty(), List.of()
            ));
        }
        return List.copyOf(requests);
    }
}
