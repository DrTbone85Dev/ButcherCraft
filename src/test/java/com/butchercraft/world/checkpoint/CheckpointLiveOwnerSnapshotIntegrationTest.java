package com.butchercraft.world.checkpoint;

import com.butchercraft.test.TestProjectPaths;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationEventBus;
import com.butchercraft.world.simulation.checkpoint.SimulationClockCheckpointSnapshotProvider;
import com.butchercraft.world.simulation.checkpoint.SimulationClockCheckpointSnapshotRestorer;
import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import com.butchercraft.world.simulation.scheduler.RetryPolicy;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SchedulerEffectPolicy;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionBudget;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;
import com.butchercraft.world.simulation.scheduler.SimulationPipeline;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkOutcome;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkResult;
import com.butchercraft.world.simulation.scheduler.SimulationWorkStatus;
import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;
import com.butchercraft.world.simulation.scheduler.WorkFailureCode;
import com.butchercraft.world.simulation.scheduler.WorkOrigin;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPriority;
import com.butchercraft.world.simulation.scheduler.WorkValidationResult;
import com.butchercraft.world.simulation.scheduler.checkpoint.SimulationSchedulerCheckpointSnapshotProvider;
import com.butchercraft.world.simulation.scheduler.checkpoint.SimulationSchedulerCheckpointSnapshotRestorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckpointLiveOwnerSnapshotIntegrationTest {
    private static final SimulationConfiguration CONFIGURATION = SimulationConfiguration.standard();
    private static final SimulationWorkTypeId WORK_TYPE =
            SimulationWorkTypeId.of("test:checkpoint_work");
    private static final CheckpointOwnerId EXTRA_OWNER_A = CheckpointOwnerId.of("test:owner_a");
    private static final CheckpointOwnerId EXTRA_OWNER_B = CheckpointOwnerId.of("test:owner_b");

    @TempDir
    Path temporaryDirectory;

    @Test
    void clockSnapshotRoundTripRestoresClockState() {
        long tick = 8L;
        SimulationClock source = clockAt(tick);
        CheckpointOwnerSnapshotContext context = context(1L, tick);
        CheckpointOwnerSnapshotCaptureResult capture =
                new SimulationClockCheckpointSnapshotProvider(source).capture(context);
        AtomicReference<SimulationClock> restored = new AtomicReference<>();
        SimulationClockCheckpointSnapshotRestorer restorer =
                new SimulationClockCheckpointSnapshotRestorer(
                        CONFIGURATION,
                        new SimulationEventBus(),
                        restored::get,
                        restored::set
                );

        CheckpointOwnerRestorationPreparation preparation = restorer.prepare(restorationRequest(
                capture.snapshot().orElseThrow().payload(),
                context
        ));
        assertTrue(preparation.successful());
        assertTrue(preparation.candidate().orElseThrow().publish().successful());

        assertEquals(tick, restored.get().simulationTick());
        assertEquals(source.state(), restored.get().state());
    }

    @Test
    void schedulerSnapshotRoundTripRestoresSchedulerState() {
        long tick = 6L;
        SimulationSchedulerManager manager = schedulerAt(tick);
        manager.submit(work("test:work_a", tick, tick + 3L), tick);
        CheckpointOwnerSnapshotContext context = context(1L, tick);
        CheckpointOwnerSnapshotCaptureResult capture =
                new SimulationSchedulerCheckpointSnapshotProvider(manager).capture(context);
        AtomicReference<SimulationSchedulerManager> restored = new AtomicReference<>();

        CheckpointOwnerRestorationPreparation preparation = schedulerRestorer(restored).prepare(
                restorationRequest(capture.snapshot().orElseThrow().payload(), context)
        );
        assertTrue(preparation.successful());
        assertTrue(preparation.candidate().orElseThrow().publish().successful());

        assertEquals(tick, restored.get().lastFinalizedSimulationTick());
        assertEquals(1, restored.get().registry().size());
        assertEquals(manager.nextSubmissionSequence(), restored.get().nextSubmissionSequence());
    }

    @Test
    void schedulerSnapshotRestoresUnknownOutcomeWithoutAutomaticRetry() {
        SimulationWorkTypeId type = SimulationWorkTypeId.of("test:checkpoint_unknown");
        SimulationWorkId workId = SimulationWorkId.of("test:checkpoint_unknown_work");
        SimulationWorkHandlerRegistry registry = new SimulationWorkHandlerRegistry(List.of(
                nonRepeatableThrowingHandler(type)
        ));
        SimulationSchedulerManager manager = new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                registry,
                0L
        );
        SimulationWorkRequest request = new SimulationWorkRequest(
                workId,
                type,
                BuiltInSimulationStages.EXECUTION,
                1L,
                WorkPriority.NORMAL,
                WorkOrigin.of("test:checkpoint", 0L, "test:unknown_outcome"),
                WorkPayload.empty(),
                RetryPolicy.nextTick(),
                2,
                OptionalLong.empty(),
                List.of()
        );
        assertTrue(manager.submit(request, 0L).accepted());
        new SimulationPipeline(manager, SimulationExecutionBudget.standard()).execute(1L);
        CheckpointOwnerSnapshotContext context = context(1L, 1L);
        CheckpointOwnerSnapshotPayload payload = new SimulationSchedulerCheckpointSnapshotProvider(manager)
                .capture(context)
                .snapshot().orElseThrow()
                .payload();
        String payloadJson = new String(payload.payloadBytes(), StandardCharsets.UTF_8);
        AtomicReference<SimulationSchedulerManager> restored = new AtomicReference<>();

        CheckpointOwnerRestorationPreparation preparation = new SimulationSchedulerCheckpointSnapshotRestorer(
                registry,
                restored::get,
                restored::set
        ).prepare(restorationRequest(payload, context));
        assertTrue(preparation.successful());
        assertTrue(preparation.candidate().orElseThrow().publish().successful());
        new SimulationPipeline(restored.get(), SimulationExecutionBudget.standard()).execute(2L);

        assertTrue(payloadJson.contains("\"status\": \"unknown_outcome\""));
        assertTrue(payloadJson.contains("\"last_invocation_identity\""));
        assertTrue(payloadJson.contains("\"last_effect_identity\""));
        assertTrue(payloadJson.contains("\"effect_policy_identity\""));
        var runtime = restored.get().runtimeFor(workId).orElseThrow();
        assertEquals(SimulationWorkStatus.UNKNOWN_OUTCOME, runtime.status());
        assertEquals(1, runtime.attemptCount());
        assertEquals(WorkFailureCode.NON_REPEATABLE_OUTCOME_UNKNOWN,
                runtime.lastFailureCode().orElseThrow());
    }

    @Test
    void snapshotIdentityIsStableForUnchangedContentAndDigestChangesWithContent() {
        SimulationClock clock = clockAt(5L);
        CheckpointOwnerSnapshotContext firstContext = context(1L, 5L);
        CheckpointOwnerSnapshotCaptureResult first =
                new SimulationClockCheckpointSnapshotProvider(clock).capture(firstContext);
        CheckpointOwnerSnapshotCaptureResult second =
                new SimulationClockCheckpointSnapshotProvider(clock).capture(firstContext);

        assertEquals(
                first.snapshot().orElseThrow().payload().descriptor().snapshotIdentity(),
                second.snapshot().orElseThrow().payload().descriptor().snapshotIdentity()
        );
        assertEquals(
                first.snapshot().orElseThrow().payload().descriptor().contentDigest(),
                second.snapshot().orElseThrow().payload().descriptor().contentDigest()
        );

        clock.advance(1L);
        CheckpointOwnerSnapshotCaptureResult changed =
                new SimulationClockCheckpointSnapshotProvider(clock).capture(context(2L, 6L));

        assertFalse(first.snapshot().orElseThrow().payload().descriptor().contentDigest()
                .equals(changed.snapshot().orElseThrow().payload().descriptor().contentDigest()));
    }

    @Test
    void ownerProvidersRemainInOwningPackagesAndStoreTreatsPayloadAsOpaque() {
        assertTrue(SimulationClockCheckpointSnapshotProvider.class.getPackageName()
                .startsWith("com.butchercraft.world.simulation"));
        assertTrue(SimulationSchedulerCheckpointSnapshotProvider.class.getPackageName()
                .startsWith("com.butchercraft.world.simulation.scheduler"));

        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("opaque"));
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 1L);
        CheckpointOwnerSnapshotPayload payload = fakePayload(
                CheckpointOwnerId.of("test:opaque_owner"),
                generationId,
                "not-json-owner-payload".getBytes(StandardCharsets.UTF_8),
                worldRoot(),
                platformManifest(),
                1L
        );

        CheckpointPublicationReport report = store.publish(new CheckpointPublicationRequest(
                generationId,
                Optional.empty(),
                Optional.empty(),
                1L,
                List.of(payload),
                List.of(payload.descriptor().ownerId()),
                platformManifest(),
                worldRoot()
        ));

        assertTrue(report.successful());
    }

    @Test
    void allRequiredOwnersPublishInOneCheckpointGeneration() {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("publish"));

        CheckpointPublicationReport report = publishClockScheduler(store, 1L, 7L, Optional.empty(), Optional.empty());

        assertTrue(report.successful());
        assertEquals(2, report.generationManifest().orElseThrow().ownerSnapshots().size());
    }

    @Test
    void missingClockOwnerFailsCapture() {
        CheckpointOwnerSnapshotCoordinator coordinator = new CheckpointOwnerSnapshotCoordinator(
                requiredClockSchedulerOwners(),
                List.of(new SimulationSchedulerCheckpointSnapshotProvider(schedulerAt(4L))),
                List.of()
        );

        CheckpointCoordinatedCaptureReport report = coordinator.capture(context(1L, 4L));

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), CheckpointFailureCode.OWNER_PROVIDER_MISSING));
    }

    @Test
    void missingSchedulerOwnerFailsCapture() {
        CheckpointOwnerSnapshotCoordinator coordinator = new CheckpointOwnerSnapshotCoordinator(
                requiredClockSchedulerOwners(),
                List.of(new SimulationClockCheckpointSnapshotProvider(clockAt(4L))),
                List.of()
        );

        CheckpointCoordinatedCaptureReport report = coordinator.capture(context(1L, 4L));

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), CheckpointFailureCode.OWNER_PROVIDER_MISSING));
    }

    @Test
    void duplicateOwnerProviderFailsCapture() {
        SimulationClock clock = clockAt(4L);
        CheckpointOwnerSnapshotCoordinator coordinator = new CheckpointOwnerSnapshotCoordinator(
                List.of(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER),
                List.of(
                        new SimulationClockCheckpointSnapshotProvider(clock),
                        new SimulationClockCheckpointSnapshotProvider(clock)
                ),
                List.of()
        );

        CheckpointCoordinatedCaptureReport report = coordinator.capture(context(1L, 4L));

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), CheckpointFailureCode.OWNER_PROVIDER_DUPLICATE));
    }

    @Test
    void clockSchedulerConsistencyValidationPassesAndTickMismatchFails() {
        CheckpointOwnerSnapshotCoordinator valid = coordinatorFor(clockAt(5L), schedulerAt(5L), null, null);
        assertTrue(valid.capture(context(1L, 5L)).successful());

        CheckpointOwnerSnapshotCoordinator invalid = coordinatorFor(clockAt(5L), schedulerAt(4L), null, null);
        CheckpointCoordinatedCaptureReport report = invalid.capture(context(1L, 5L));

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), CheckpointFailureCode.CLOCK_SCHEDULER_TICK_MISMATCH));
    }

    @Test
    void invalidSchedulerRuntimeStateFailsRestoration() {
        CheckpointOwnerSnapshotPayload original = schedulerPayloadAt(5L);
        String corrupted = new String(original.payloadBytes(), StandardCharsets.UTF_8)
                .replaceFirst("\"status\": \"scheduled\"", "\"status\": \"running\"");
        CheckpointOwnerSnapshotPayload invalid = samePayloadWithNewDigest(
                original,
                corrupted.getBytes(StandardCharsets.UTF_8)
        );

        CheckpointOwnerRestorationPreparation preparation = schedulerRestorer(new AtomicReference<>()).prepare(
                restorationRequest(invalid, context(1L, 5L))
        );

        assertFalse(preparation.successful());
        assertTrue(hasFailure(preparation.failures(), CheckpointFailureCode.SCHEDULER_INVARIANT_VIOLATION));
    }

    @Test
    void unsupportedClockSchemaFailsRestoration() {
        CheckpointOwnerSnapshotPayload payload =
                new SimulationClockCheckpointSnapshotProvider(clockAt(5L))
                        .capture(context(1L, 5L))
                        .snapshot().orElseThrow()
                        .payload();
        CheckpointOwnerSnapshotPayload unsupported = withSchema(payload, 99);

        CheckpointOwnerRestorationPreparation preparation =
                new SimulationClockCheckpointSnapshotRestorer(
                        CONFIGURATION,
                        new SimulationEventBus(),
                        () -> null,
                        ignored -> { }
                )
                        .prepare(restorationRequest(unsupported, context(1L, 5L)));

        assertFalse(preparation.successful());
        assertTrue(hasFailure(preparation.failures(), CheckpointFailureCode.UNSUPPORTED_OWNER_SNAPSHOT_SCHEMA));
    }

    @Test
    void unsupportedSchedulerSchemaFailsRestoration() {
        CheckpointOwnerSnapshotPayload unsupported = withSchema(schedulerPayloadAt(5L), 99);

        CheckpointOwnerRestorationPreparation preparation = schedulerRestorer(new AtomicReference<>()).prepare(
                restorationRequest(unsupported, context(1L, 5L))
        );

        assertFalse(preparation.successful());
        assertTrue(hasFailure(preparation.failures(), CheckpointFailureCode.UNSUPPORTED_OWNER_SNAPSHOT_SCHEMA));
    }

    @Test
    void worldIdentityMismatchAndPlatformManifestMismatchBlockRecovery() {
        CheckpointFilesystemStore worldStore = new CheckpointFilesystemStore(temporaryDirectory.resolve("world"));
        publishClockScheduler(worldStore, 1L, 5L, Optional.empty(), Optional.empty());

        CheckpointCoordinatedRestorationReport worldReport = emptyRestoreCoordinator().restoreSelected(
                worldStore,
                new CheckpointFilesystemRecoveryRequest(requiredClockSchedulerOwners(), otherWorldRoot(), platformManifest())
        );
        assertEquals(CheckpointCoordinatedRestorationOutcome.RECOVERY_BLOCKED, worldReport.outcome());
        assertTrue(hasFailure(worldReport.failures(), CheckpointFailureCode.WORLD_IDENTITY_MISMATCH));

        CheckpointFilesystemStore platformStore = new CheckpointFilesystemStore(temporaryDirectory.resolve("platform"));
        publishClockScheduler(platformStore, 1L, 5L, Optional.empty(), Optional.empty());
        CheckpointCoordinatedRestorationReport platformReport = emptyRestoreCoordinator().restoreSelected(
                platformStore,
                new CheckpointFilesystemRecoveryRequest(requiredClockSchedulerOwners(), worldRoot(), otherPlatformManifest())
        );
        assertEquals(CheckpointCoordinatedRestorationOutcome.RECOVERY_BLOCKED, platformReport.outcome());
        assertTrue(hasFailure(platformReport.failures(), CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH));
    }

    @Test
    void ownerValidationFailurePreventsAllPublicationAndPreservesRuntime() {
        CheckpointPublicationReport report = publishClockScheduler(
                new CheckpointFilesystemStore(temporaryDirectory.resolve("validation_failure")),
                1L,
                5L,
                Optional.empty(),
                Optional.empty()
        );
        CheckpointRecoveredGeneration recovered = recoveredFrom(report);
        CheckpointOwnerSnapshotPayload invalidScheduler = samePayloadWithNewDigest(
                snapshotFor(recovered, CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER),
                "invalid scheduler payload".getBytes(StandardCharsets.UTF_8)
        );
        CheckpointRecoveredGeneration invalidRecovered = withSnapshot(recovered, invalidScheduler);
        AtomicReference<SimulationClock> clockTarget = new AtomicReference<>(clockAt(100L));
        AtomicReference<SimulationSchedulerManager> schedulerTarget = new AtomicReference<>(schedulerAt(100L));

        CheckpointCoordinatedRestorationReport restore = coordinatorFor(
                null,
                null,
                clockTarget,
                schedulerTarget
        ).restore(invalidRecovered);

        assertEquals(CheckpointCoordinatedRestorationOutcome.RECOVERY_BLOCKED, restore.outcome());
        assertTrue(hasFailure(restore.failures(), CheckpointFailureCode.SCHEDULER_INVARIANT_VIOLATION));
        assertEquals(100L, clockTarget.get().simulationTick());
        assertEquals(100L, schedulerTarget.get().lastFinalizedSimulationTick());
        assertTrue(restore.publishedOwners().isEmpty());
    }

    @Test
    void ownerPublicationFailureDoesNotPublishAnyOwnerWhenPrePublicationCheckFails() {
        CheckpointRecoveredGeneration recovered = fakeRecoveredGeneration();
        AtomicInteger published = new AtomicInteger();
        CheckpointOwnerSnapshotCoordinator coordinator = new CheckpointOwnerSnapshotCoordinator(
                List.of(EXTRA_OWNER_A, EXTRA_OWNER_B),
                List.of(),
                List.of(
                        fakeRestorer(EXTRA_OWNER_A, published, false),
                        fakeRestorer(EXTRA_OWNER_B, published, true)
                )
        );

        CheckpointCoordinatedRestorationReport report = coordinator.restore(recovered);

        assertEquals(CheckpointCoordinatedRestorationOutcome.RECOVERY_BLOCKED, report.outcome());
        assertEquals(0, published.get());
        assertTrue(report.publishedOwners().isEmpty());
        assertTrue(hasFailure(report.failures(), CheckpointFailureCode.OWNER_PUBLICATION_FAILURE));
    }

    @Test
    void lateOwnerPublicationFailureRollsBackAttemptedPublications() {
        CheckpointRecoveredGeneration recovered = fakeRecoveredGeneration();
        AtomicInteger published = new AtomicInteger();
        CheckpointOwnerSnapshotCoordinator coordinator = new CheckpointOwnerSnapshotCoordinator(
                List.of(EXTRA_OWNER_A, EXTRA_OWNER_B),
                List.of(),
                List.of(
                        fakeRestorer(EXTRA_OWNER_A, published, false, false),
                        fakeRestorer(EXTRA_OWNER_B, published, false, true)
                )
        );

        CheckpointCoordinatedRestorationReport report = coordinator.restore(recovered);

        assertEquals(CheckpointCoordinatedRestorationOutcome.RECOVERY_BLOCKED, report.outcome());
        assertEquals(0, published.get());
        assertTrue(report.publishedOwners().isEmpty());
        assertTrue(hasFailure(report.failures(), CheckpointFailureCode.OWNER_PUBLICATION_FAILURE));
        assertTrue(hasFailure(report.failures(), CheckpointFailureCode.COORDINATED_PUBLICATION_FAILURE));
    }

    @Test
    void successfulCoordinatedRestorationPublishesBothOwners() {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("restore"));
        publishClockScheduler(store, 1L, 9L, Optional.empty(), Optional.empty());
        AtomicReference<SimulationClock> clockTarget = new AtomicReference<>();
        AtomicReference<SimulationSchedulerManager> schedulerTarget = new AtomicReference<>();

        CheckpointCoordinatedRestorationReport report = coordinatorFor(
                null,
                null,
                clockTarget,
                schedulerTarget
        ).restoreSelected(store, recoveryRequest());

        assertEquals(CheckpointCoordinatedRestorationOutcome.RESTORED, report.outcome());
        assertEquals(2, report.publishedOwners().size());
        assertEquals(9L, clockTarget.get().simulationTick());
        assertEquals(9L, schedulerTarget.get().lastFinalizedSimulationTick());
    }

    @Test
    void filesystemRecoverySuppliesSelectedGenerationPayloads() {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("selected"));
        CheckpointPublicationReport published = publishClockScheduler(
                store,
                1L,
                5L,
                Optional.empty(),
                Optional.empty()
        );

        CheckpointRecoveredGenerationReport recovered = store.loadSelectedGeneration(recoveryRequest());

        assertTrue(recovered.successful());
        assertEquals(
                published.generationManifest().orElseThrow().generationId(),
                recovered.recoveredGeneration().orElseThrow().generationRecord().generationId()
        );
        assertEquals(2, recovered.recoveredGeneration().orElseThrow().ownerSnapshots().size());
    }

    @Test
    void fallbackToOlderValidGenerationRestoresMatchingOwnerSnapshots() throws IOException {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(temporaryDirectory.resolve("fallback"));
        CheckpointPublicationReport first = publishClockScheduler(store, 1L, 5L, Optional.empty(), Optional.empty());
        CheckpointGenerationManifest firstManifest = first.generationManifest().orElseThrow();
        CheckpointPublicationReport second = publishClockScheduler(
                store,
                2L,
                6L,
                Optional.of(firstManifest.generationId()),
                Optional.of(firstManifest.manifestDigest())
        );
        CheckpointGenerationId secondId = second.generationManifest().orElseThrow().generationId();
        Files.writeString(
                store.layout().ownerPayload(
                        store.layout().finalGenerationDirectory(secondId),
                        CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER
                ),
                "corrupt",
                StandardCharsets.UTF_8
        );
        AtomicReference<SimulationClock> clockTarget = new AtomicReference<>();
        AtomicReference<SimulationSchedulerManager> schedulerTarget = new AtomicReference<>();

        CheckpointCoordinatedRestorationReport report = coordinatorFor(
                null,
                null,
                clockTarget,
                schedulerTarget
        ).restoreSelected(store, recoveryRequest());

        assertEquals(CheckpointCoordinatedRestorationOutcome.RESTORED, report.outcome());
        assertEquals(CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY,
                store.recover(recoveryRequest()).selection().outcome());
        assertEquals(5L, clockTarget.get().simulationTick());
        assertEquals(5L, schedulerTarget.get().lastFinalizedSimulationTick());
    }

    @Test
    void noAutomaticSaveHookOrStartupIntegrationExists() throws IOException {
        String entryPoint = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/ButcherCraft.java"
        ));
        String clockService = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/simulation/SimulationClockService.java"
        ));
        String schedulerService = Files.readString(TestProjectPaths.projectPath(
                "src/main/java/com/butchercraft/world/SimulationSchedulerService.java"
        ));

        assertFalse(entryPoint.contains("CheckpointOwnerSnapshotCoordinator"));
        assertFalse(entryPoint.contains("CheckpointFilesystemStore"));
        assertFalse(clockService.contains("CheckpointOwnerSnapshotCoordinator"));
        assertFalse(schedulerService.contains("CheckpointOwnerSnapshotCoordinator"));
    }

    private CheckpointPublicationReport publishClockScheduler(
            CheckpointFilesystemStore store,
            long sequence,
            long tick,
            Optional<CheckpointGenerationId> predecessorId,
            Optional<String> predecessorDigest
    ) {
        SimulationClock clock = clockAt(tick);
        SimulationSchedulerManager scheduler = schedulerAt(tick);
        scheduler.submit(work("test:work_" + sequence, tick, tick + 2L), tick);
        CheckpointOwnerSnapshotContext context = context(sequence, tick, predecessorId, predecessorDigest);
        CheckpointCoordinatedCaptureReport capture = coordinatorFor(clock, scheduler, null, null).capture(context);
        assertTrue(capture.successful(), () -> "Capture failed: " + capture.failures());
        CheckpointPublicationReport report = store.publish(capture.publicationRequest().orElseThrow());
        assertTrue(report.successful(), () -> "Publication failed: " + report.diagnostics());
        return report;
    }

    private CheckpointRecoveredGeneration recoveredFrom(CheckpointPublicationReport report) {
        CheckpointGenerationManifest manifest = report.generationManifest().orElseThrow();
        return new CheckpointRecoveredGeneration(
                new CheckpointGenerationRecord(manifest, CheckpointPublicationState.COMMITTED),
                manifest.ownerSnapshots().stream()
                        .map(descriptor -> {
                            if (descriptor.ownerId().equals(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER)) {
                                return new SimulationClockCheckpointSnapshotProvider(
                                        clockAt(descriptor.representedSimulationTick())
                                ).capture(context(
                                        descriptor.generationId().committedSequence(),
                                        descriptor.representedSimulationTick()
                                )).snapshot().orElseThrow().payload();
                            }
                            SimulationSchedulerManager scheduler = schedulerAt(descriptor.representedSimulationTick());
                            scheduler.submit(work(
                                    "test:work_" + descriptor.generationId().committedSequence(),
                                    descriptor.representedSimulationTick(),
                                    descriptor.representedSimulationTick() + 2L
                            ), descriptor.representedSimulationTick());
                            return new SimulationSchedulerCheckpointSnapshotProvider(scheduler)
                                    .capture(context(
                                            descriptor.generationId().committedSequence(),
                                            descriptor.representedSimulationTick()
                                    )).snapshot().orElseThrow().payload();
                        })
                        .toList()
        );
    }

    private CheckpointRecoveredGeneration withSnapshot(
            CheckpointRecoveredGeneration recovered,
            CheckpointOwnerSnapshotPayload replacement
    ) {
        List<CheckpointOwnerSnapshotPayload> snapshots = recovered.ownerSnapshots().stream()
                .map(snapshot -> snapshot.descriptor().ownerId().equals(replacement.descriptor().ownerId())
                        ? replacement : snapshot)
                .toList();
        List<OwnerSnapshotDescriptor> descriptors = snapshots.stream()
                .map(CheckpointOwnerSnapshotPayload::descriptor)
                .toList();
        CheckpointGenerationManifest manifest = new CheckpointGenerationCandidate(
                recovered.manifest().generationId(),
                recovered.manifest().predecessorGenerationId(),
                recovered.manifest().predecessorManifestDigest(),
                recovered.manifest().authoritativeSimulationTick(),
                descriptors,
                recovered.manifest().platformDeterminismManifest(),
                recovered.manifest().worldIdentityRoot(),
                CheckpointPublicationState.COMPLETE_CANDIDATE
        ).toManifest();
        return new CheckpointRecoveredGeneration(
                new CheckpointGenerationRecord(manifest, recovered.generationRecord().publicationState()),
                snapshots
        );
    }

    private CheckpointOwnerSnapshotPayload snapshotFor(
            CheckpointRecoveredGeneration recovered,
            CheckpointOwnerId ownerId
    ) {
        return recovered.ownerSnapshots().stream()
                .filter(snapshot -> snapshot.descriptor().ownerId().equals(ownerId))
                .findFirst()
                .orElseThrow();
    }

    private CheckpointOwnerSnapshotPayload schedulerPayloadAt(long tick) {
        SimulationSchedulerManager scheduler = schedulerAt(tick);
        scheduler.submit(work("test:work_scheduler_payload", tick, tick + 2L), tick);
        return new SimulationSchedulerCheckpointSnapshotProvider(scheduler)
                .capture(context(1L, tick))
                .snapshot().orElseThrow()
                .payload();
    }

    private CheckpointOwnerRestorationRequest restorationRequest(
            CheckpointOwnerSnapshotPayload payload,
            CheckpointOwnerSnapshotContext context
    ) {
        return new CheckpointOwnerRestorationRequest(
                payload.descriptor(),
                payload.payloadBytes(),
                context.generationId(),
                context.platformDeterminismManifest(),
                context.worldIdentityRoot()
        );
    }

    private CheckpointOwnerSnapshotPayload samePayloadWithNewDigest(
            CheckpointOwnerSnapshotPayload original,
            byte[] payloadBytes
    ) {
        String digest = CheckpointSnapshotDigest.sha256(payloadBytes);
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                original.descriptor().ownerId(),
                original.descriptor().snapshotSchemaVersion(),
                original.descriptor().snapshotIdentity() + "_" + CheckpointSnapshotDigest.shortHex(digest),
                digest,
                original.descriptor().participation(),
                original.descriptor().configurationIdentity(),
                original.descriptor().worldIdentityRoot(),
                original.descriptor().generationId(),
                original.descriptor().representedSimulationTick(),
                original.descriptor().ownerSequence()
        );
        return new CheckpointOwnerSnapshotPayload(descriptor, payloadBytes, digest);
    }

    private CheckpointOwnerSnapshotPayload withSchema(
            CheckpointOwnerSnapshotPayload original,
            int schemaVersion
    ) {
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                original.descriptor().ownerId(),
                schemaVersion,
                original.descriptor().snapshotIdentity(),
                original.descriptor().contentDigest(),
                original.descriptor().participation(),
                original.descriptor().configurationIdentity(),
                original.descriptor().worldIdentityRoot(),
                original.descriptor().generationId(),
                original.descriptor().representedSimulationTick(),
                original.descriptor().ownerSequence()
        );
        return new CheckpointOwnerSnapshotPayload(descriptor, original.payloadBytes(), original.expectedContentDigest());
    }

    private CheckpointOwnerSnapshotPayload fakePayload(
            CheckpointOwnerId ownerId,
            CheckpointGenerationId generationId,
            byte[] bytes,
            WorldIdentityRootReference worldRoot,
            PlatformDeterminismManifestReference manifest,
            long tick
    ) {
        String digest = CheckpointSnapshotDigest.sha256(bytes);
        String snapshotIdentity = ownerId.value() + "/snapshot/" + CheckpointSnapshotDigest.shortHex(digest);
        OwnerSnapshotDescriptor descriptor = new OwnerSnapshotDescriptor(
                ownerId,
                1,
                snapshotIdentity,
                digest,
                CheckpointSnapshotParticipation.REQUIRED,
                ownerId.value() + "/configuration",
                worldRoot,
                generationId,
                tick,
                tick
        );
        return new CheckpointOwnerSnapshotPayload(descriptor, bytes, digest);
    }

    private CheckpointRecoveredGeneration fakeRecoveredGeneration() {
        CheckpointGenerationId generationId = CheckpointGenerationId.of(1L, 1L);
        List<CheckpointOwnerSnapshotPayload> payloads = List.of(
                fakePayload(EXTRA_OWNER_A, generationId, "a".getBytes(StandardCharsets.UTF_8),
                        worldRoot(), platformManifest(), 1L),
                fakePayload(EXTRA_OWNER_B, generationId, "b".getBytes(StandardCharsets.UTF_8),
                        worldRoot(), platformManifest(), 1L)
        );
        CheckpointGenerationManifest manifest = new CheckpointGenerationCandidate(
                generationId,
                Optional.empty(),
                Optional.empty(),
                1L,
                payloads.stream().map(CheckpointOwnerSnapshotPayload::descriptor).toList(),
                platformManifest(),
                worldRoot(),
                CheckpointPublicationState.COMPLETE_CANDIDATE
        ).toManifest();
        return new CheckpointRecoveredGeneration(
                new CheckpointGenerationRecord(manifest, CheckpointPublicationState.COMMITTED),
                payloads
        );
    }

    private CheckpointOwnerSnapshotRestorer fakeRestorer(
            CheckpointOwnerId ownerId,
            AtomicInteger published,
            boolean rejectPublication
    ) {
        return fakeRestorer(ownerId, published, rejectPublication, false);
    }

    private CheckpointOwnerSnapshotRestorer fakeRestorer(
            CheckpointOwnerId ownerId,
            AtomicInteger published,
            boolean rejectPublication,
            boolean failDuringPublication
    ) {
        return new CheckpointOwnerSnapshotRestorer() {
            @Override
            public CheckpointOwnerId ownerId() {
                return ownerId;
            }

            @Override
            public CheckpointOwnerRestorationPreparation prepare(CheckpointOwnerRestorationRequest request) {
                CheckpointOwnerValidationMetadata metadata = new CheckpointOwnerValidationMetadata(
                        ownerId,
                        Map.of(
                                CheckpointOwnerSnapshotCoordinator.CONFIGURATION_IDENTITY_KEY,
                                request.descriptor().configurationIdentity(),
                                CheckpointOwnerSnapshotCoordinator.SNAPSHOT_IDENTITY_KEY,
                                request.descriptor().snapshotIdentity()
                        )
                );
                return CheckpointOwnerRestorationPreparation.prepared(new CheckpointOwnerRestorationCandidate() {
                    @Override
                    public CheckpointOwnerId ownerId() {
                        return ownerId;
                    }

                    @Override
                    public CheckpointOwnerValidationMetadata validationMetadata() {
                        return metadata;
                    }

                    @Override
                    public List<CheckpointFailure> validatePublication() {
                        return rejectPublication
                                ? List.of(new CheckpointFailure(
                                        CheckpointFailureCode.OWNER_PUBLICATION_FAILURE,
                                        ownerId.value(),
                                        "Owner rejected publication"
                                ))
                                : List.of();
                    }

                    @Override
                    public CheckpointOwnerRestorationPublicationResult publish() {
                        if (failDuringPublication) {
                            published.incrementAndGet();
                            return CheckpointOwnerRestorationPublicationResult.failed(ownerId, List.of(
                                    new CheckpointFailure(
                                            CheckpointFailureCode.OWNER_PUBLICATION_FAILURE,
                                            ownerId.value(),
                                            "Owner failed during publication"
                                    )
                            ));
                        }
                        published.incrementAndGet();
                        return CheckpointOwnerRestorationPublicationResult.published(ownerId);
                    }

                    @Override
                    public CheckpointOwnerRestorationPublicationResult rollbackPublication() {
                        if (published.get() > 0) {
                            published.decrementAndGet();
                        }
                        return CheckpointOwnerRestorationPublicationResult.published(ownerId);
                    }
                });
            }
        };
    }

    private CheckpointOwnerSnapshotCoordinator coordinatorFor(
            SimulationClock clock,
            SimulationSchedulerManager scheduler,
            AtomicReference<SimulationClock> clockTarget,
            AtomicReference<SimulationSchedulerManager> schedulerTarget
    ) {
        List<CheckpointOwnerSnapshotProvider> providers = clock == null || scheduler == null
                ? List.of()
                : List.of(
                        new SimulationClockCheckpointSnapshotProvider(clock),
                        new SimulationSchedulerCheckpointSnapshotProvider(scheduler)
                );
        return new CheckpointOwnerSnapshotCoordinator(
                requiredClockSchedulerOwners(),
                providers,
                List.of(
                        new SimulationClockCheckpointSnapshotRestorer(
                                CONFIGURATION,
                                new SimulationEventBus(),
                                clockTarget == null ? () -> null : clockTarget::get,
                                clockTarget == null ? ignored -> { } : clockTarget::set
                        ),
                        schedulerRestorer(schedulerTarget == null ? new AtomicReference<>() : schedulerTarget)
                )
        );
    }

    private CheckpointOwnerSnapshotCoordinator emptyRestoreCoordinator() {
        return new CheckpointOwnerSnapshotCoordinator(
                requiredClockSchedulerOwners(),
                List.of(),
                List.of()
        );
    }

    private SimulationSchedulerCheckpointSnapshotRestorer schedulerRestorer(
            AtomicReference<SimulationSchedulerManager> target
    ) {
        return new SimulationSchedulerCheckpointSnapshotRestorer(handlerRegistry(), target::get, target::set);
    }

    private CheckpointFilesystemRecoveryRequest recoveryRequest() {
        return new CheckpointFilesystemRecoveryRequest(
                requiredClockSchedulerOwners(),
                worldRoot(),
                platformManifest()
        );
    }

    private CheckpointOwnerSnapshotContext context(long sequence, long tick) {
        return context(sequence, tick, Optional.empty(), Optional.empty());
    }

    private CheckpointOwnerSnapshotContext context(
            long sequence,
            long tick,
            Optional<CheckpointGenerationId> predecessorId,
            Optional<String> predecessorDigest
    ) {
        return new CheckpointOwnerSnapshotContext(
                CheckpointGenerationId.of(sequence, tick),
                predecessorId,
                predecessorDigest,
                tick,
                platformManifest(),
                worldRoot()
        );
    }

    private SimulationClock clockAt(long tick) {
        SimulationClock clock = new SimulationClock(CONFIGURATION);
        clock.advance(tick);
        return clock;
    }

    private SimulationSchedulerManager schedulerAt(long tick) {
        return new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                handlerRegistry(),
                tick
        );
    }

    private SimulationWorkRequest work(String id, long submissionTick, long scheduledTick) {
        return new SimulationWorkRequest(
                SimulationWorkId.of(id),
                WORK_TYPE,
                BuiltInSimulationStages.EXECUTION,
                scheduledTick,
                WorkPriority.NORMAL,
                WorkOrigin.of("test:checkpoint", submissionTick, "test:checkpoint_test"),
                WorkPayload.empty(),
                RetryPolicy.never(),
                1,
                OptionalLong.empty(),
                List.of()
        );
    }

    private SimulationWorkHandlerRegistry handlerRegistry() {
        return new SimulationWorkHandlerRegistry(List.of(new SimulationWorkHandler() {
            @Override
            public SimulationWorkTypeId supportedTypeId() {
                return WORK_TYPE;
            }

            @Override
            public HandlerEffectType effectType() {
                return HandlerEffectType.READ_ONLY;
            }

            @Override
            public WorkValidationResult validate(ScheduledSimulationWork work) {
                return WorkValidationResult.acceptedResult();
            }

            @Override
            public SimulationWorkResult execute(SimulationExecutionContext context) {
                return new SimulationWorkResult(
                        SimulationWorkOutcome.COMPLETED,
                        Optional.empty(),
                        List.of(),
                        OptionalLong.empty(),
                        List.of(),
                        WorkPayload.empty(),
                        1,
                        context.authoritativeSimulationTick()
                );
            }
        }));
    }

    private SimulationWorkHandler nonRepeatableThrowingHandler(SimulationWorkTypeId type) {
        return new SimulationWorkHandler() {
            @Override
            public SimulationWorkTypeId supportedTypeId() {
                return type;
            }

            @Override
            public HandlerEffectType effectType() {
                return HandlerEffectType.NON_REPEATABLE;
            }

            @Override
            public SchedulerEffectPolicy effectPolicy() {
                return SchedulerEffectPolicy.nonRepeatable(
                        type,
                        "test:checkpoint_owner",
                        "test checkpoint unknown-outcome policy"
                );
            }

            @Override
            public WorkValidationResult validate(ScheduledSimulationWork work) {
                return WorkValidationResult.acceptedResult();
            }

            @Override
            public SimulationWorkResult execute(SimulationExecutionContext context) {
                throw new IllegalStateException("checkpoint unknown outcome");
            }
        };
    }

    private List<CheckpointOwnerId> requiredClockSchedulerOwners() {
        return List.of(
                CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
                CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER
        );
    }

    private WorldIdentityRootReference worldRoot() {
        WorldIdentityRootIdentity root = WorldIdentityRootIdentities.from(
                new WorldIdentityGenerator().generate(1_234L)
        );
        return new WorldIdentityRootReference(root.identity(), root.schemaVersion(), root.rootDigest());
    }

    private WorldIdentityRootReference otherWorldRoot() {
        WorldIdentityRootIdentity root = WorldIdentityRootIdentities.from(
                new WorldIdentityGenerator().generate(4_321L)
        );
        return new WorldIdentityRootReference(root.identity(), root.schemaVersion(), root.rootDigest());
    }

    private PlatformDeterminismManifestReference platformManifest() {
        return new PlatformDeterminismManifestReference(
                "butchercraft:platform_determinism/im006",
                1,
                CheckpointSnapshotDigest.sha256("im006-platform".getBytes(StandardCharsets.UTF_8))
        );
    }

    private PlatformDeterminismManifestReference otherPlatformManifest() {
        return new PlatformDeterminismManifestReference(
                "butchercraft:platform_determinism/other",
                1,
                CheckpointSnapshotDigest.sha256("other-platform".getBytes(StandardCharsets.UTF_8))
        );
    }

    private boolean hasFailure(List<CheckpointFailure> failures, CheckpointFailureCode code) {
        return failures.stream().anyMatch(failure -> failure.code() == code);
    }
}
