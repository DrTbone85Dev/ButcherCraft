package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointCoordinatedRestorationOutcome;
import com.butchercraft.world.checkpoint.CheckpointFailureCode;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointPublicationOutcome;
import com.butchercraft.world.checkpoint.CheckpointRecoveryOutcome;
import com.butchercraft.world.checkpoint.PlatformDeterminismManifestReference;
import com.butchercraft.world.checkpoint.WorldIdentityRootReference;
import com.butchercraft.world.identity.WorldIdentityGenerator;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.simulation.SimulationClock;
import com.butchercraft.world.simulation.SimulationConfiguration;
import com.butchercraft.world.simulation.SimulationEventBus;
import com.butchercraft.world.simulation.SimulationSchema;
import com.butchercraft.world.simulation.scheduler.BuiltInSimulationStages;
import com.butchercraft.world.simulation.scheduler.HandlerEffectType;
import com.butchercraft.world.simulation.scheduler.RetryPolicy;
import com.butchercraft.world.simulation.scheduler.ScheduledSimulationWork;
import com.butchercraft.world.simulation.scheduler.SchedulerSchema;
import com.butchercraft.world.simulation.scheduler.SimulationExecutionContext;
import com.butchercraft.world.simulation.scheduler.SimulationSchedulerManager;
import com.butchercraft.world.simulation.scheduler.SimulationStageRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandler;
import com.butchercraft.world.simulation.scheduler.SimulationWorkHandlerRegistry;
import com.butchercraft.world.simulation.scheduler.SimulationWorkId;
import com.butchercraft.world.simulation.scheduler.SimulationWorkOutcome;
import com.butchercraft.world.simulation.scheduler.SimulationWorkRequest;
import com.butchercraft.world.simulation.scheduler.SimulationWorkResult;
import com.butchercraft.world.simulation.scheduler.SimulationWorkTypeId;
import com.butchercraft.world.simulation.scheduler.WorkOrigin;
import com.butchercraft.world.simulation.scheduler.WorkPayload;
import com.butchercraft.world.simulation.scheduler.WorkPriority;
import com.butchercraft.world.simulation.scheduler.WorkValidationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentCheckpointHarnessTest {
    private static final SimulationConfiguration CONFIGURATION = SimulationConfiguration.standard();
    private static final SimulationWorkTypeId WORK_TYPE =
            SimulationWorkTypeId.of("test:development_checkpoint_work");

    @TempDir
    private Path temporaryDirectory;

    @Test
    void harnessUnavailableOutsideDevelopmentGate() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("disabled"), false);

        DevelopmentCheckpointReport report = harness.list(context);

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), DevelopmentCheckpointFailureCode.NOT_IN_DEVELOPMENT_ENVIRONMENT));
    }

    @Test
    void explicitCapturePublishesOneValidGeneration() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("capture"));

        DevelopmentCheckpointReport report = harness.capture(captureRequest(context, 5L));

        assertTrue(report.successful(), () -> report.failures().toString());
        assertEquals(CheckpointPublicationOutcome.PUBLISHED, report.publicationReport().orElseThrow().outcome());
        assertEquals(1, report.generations().size());
        assertEquals(5L, report.selectedGeneration().orElseThrow().authoritativeSimulationTick());
    }

    @Test
    void secondCaptureLinksPredecessorCorrectly() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("predecessor"));
        DevelopmentCheckpointReport first = harness.capture(captureRequest(context, 5L));

        DevelopmentCheckpointReport second = harness.capture(captureRequest(context, 8L));

        CheckpointGenerationId firstId = first.selectedGeneration().orElseThrow().generationId();
        assertTrue(second.successful());
        assertEquals(firstId, second.selectedGeneration().orElseThrow()
                .predecessorGenerationId().orElseThrow());
        assertEquals(first.selectedGeneration().orElseThrow().manifestDigest(),
                second.selectedGeneration().orElseThrow().predecessorManifestDigest().orElseThrow());
    }

    @Test
    void activeWorldScopesCheckpointRootDeterministically() {
        Path worldRoot = world("root_scope");
        Path checkpointRoot = DevelopmentCheckpointRoots.checkpointRoot(worldRoot);

        assertTrue(checkpointRoot.startsWith(worldRoot.toAbsolutePath().normalize()));
        assertEquals(worldRoot.toAbsolutePath().normalize()
                        .resolve("butchercraft")
                        .resolve("development_checkpoints")
                        .normalize(),
                checkpointRoot);
    }

    @Test
    void twoWorldsDoNotShareCheckpointRoots() {
        Path first = DevelopmentCheckpointRoots.checkpointRoot(world("world_one"));
        Path second = DevelopmentCheckpointRoots.checkpointRoot(world("world_two"));

        assertNotEquals(first, second);
    }

    @Test
    void captureIncludesClockSchedulerOwnersAndActiveWorldIdentity() {
        DevelopmentCheckpointRequestContext context = context(world("owners"));
        DevelopmentCheckpointReport report = new DevelopmentCheckpointHarness()
                .capture(captureRequest(context, 11L));

        List<String> ownerIds = report.selectedGeneration().orElseThrow().owners().stream()
                .map(DevelopmentCheckpointOwnerSummary::ownerId)
                .toList();
        assertTrue(ownerIds.contains(CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER.value()));
        assertTrue(ownerIds.contains(CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER.value()));
        assertEquals(context.worldIdentityRoot().identity(),
                report.selectedGeneration().orElseThrow().worldIdentityRootIdentity());
    }

    @Test
    void listOutputOrderingIsDeterministic() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("list_order"));
        harness.capture(captureRequest(context, 3L));
        harness.capture(captureRequest(context, 4L));

        DevelopmentCheckpointReport first = harness.list(context);
        DevelopmentCheckpointReport second = harness.list(context);

        assertEquals(first.generations(), second.generations());
        assertEquals(first.heads(), second.heads());
        assertEquals(first.artifacts(), second.artifacts());
    }

    @Test
    void validateIsReadOnlyForExistingCheckpointRoot() throws IOException {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("read_only"));
        harness.capture(captureRequest(context, 5L));
        Map<String, Integer> before = fileSnapshot(context.checkpointRoot());

        DevelopmentCheckpointReport report = harness.validate(context);
        Map<String, Integer> after = fileSnapshot(context.checkpointRoot());

        assertTrue(report.successful(), () -> report.failures().toString());
        assertEquals(before, after);
    }

    @Test
    void selectedGenerationInspectionReportsFallback() throws IOException {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("fallback"));
        DevelopmentCheckpointReport first = harness.capture(captureRequest(context, 5L));
        DevelopmentCheckpointReport second = harness.capture(captureRequest(context, 8L));
        corruptOwnerPayload(context, second.selectedGeneration().orElseThrow().generationId());

        DevelopmentCheckpointReport report = harness.inspectSelected(context);

        assertTrue(report.successful(), () -> report.failures().toString());
        assertEquals(CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY,
                report.recoveryReport().orElseThrow().selection().outcome());
        assertEquals(first.selectedGeneration().orElseThrow().generationId(),
                report.selectedGeneration().orElseThrow().generationId());
    }

    @Test
    void invalidGenerationSelectorFailsWithoutFilesystemSelection() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("invalid_selector"));

        DevelopmentCheckpointReport report = harness.inspectGeneration(context, "../not-a-generation");

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), DevelopmentCheckpointFailureCode.INVALID_GENERATION_SELECTOR));
        assertTrue(report.recoveryReport().isEmpty());
    }

    @Test
    void crossWorldCheckpointIsRejected() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        Path worldRoot = world("cross_world");
        DevelopmentCheckpointRequestContext original = context(worldRoot, true, worldIdentity(100L),
                DevelopmentPlatformDeterminismManifest.currentReference());
        harness.capture(captureRequest(original, 5L));
        DevelopmentCheckpointRequestContext otherWorld = context(worldRoot, true, worldIdentity(200L),
                DevelopmentPlatformDeterminismManifest.currentReference());

        DevelopmentCheckpointReport report = harness.validate(otherWorld);

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), DevelopmentCheckpointFailureCode.WORLD_IDENTITY_MISMATCH));
    }

    @Test
    void platformDeterminismManifestMismatchIsReported() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        Path worldRoot = world("platform_mismatch");
        WorldIdentityRootReference worldIdentity = worldIdentity(101L);
        DevelopmentCheckpointRequestContext original = context(worldRoot, true, worldIdentity,
                DevelopmentPlatformDeterminismManifest.currentReference());
        harness.capture(captureRequest(original, 5L));
        DevelopmentCheckpointRequestContext otherPlatform = context(worldRoot, true, worldIdentity,
                new PlatformDeterminismManifestReference(
                        "butchercraft:platform_determinism/other_development_fixture",
                        1,
                        "sha256:0000000000000000000000000000000000000000000000000000000000000001"
                ));

        DevelopmentCheckpointReport report = harness.validate(otherPlatform);

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(),
                DevelopmentCheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH));
    }

    @Test
    void corruptedPayloadIsReported() throws IOException {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("corrupt_payload"));
        DevelopmentCheckpointReport capture = harness.capture(captureRequest(context, 5L));
        corruptOwnerPayload(context, capture.selectedGeneration().orElseThrow().generationId());

        DevelopmentCheckpointReport report = harness.validate(context);

        assertFalse(report.successful());
        assertTrue(hasCheckpointFailure(report, CheckpointFailureCode.PAYLOAD_DIGEST_MISMATCH));
        assertTrue(hasFailure(report.failures(), DevelopmentCheckpointFailureCode.INTEGRITY_FAILURE));
    }

    @Test
    void corruptedHeadFallsBackDeterministically() throws IOException {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("corrupt_head"));
        DevelopmentCheckpointReport first = harness.capture(captureRequest(context, 5L));
        harness.capture(captureRequest(context, 8L));
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(context.checkpointRoot());
        Files.writeString(store.layout().headB(), "not a valid head", StandardCharsets.UTF_8);

        DevelopmentCheckpointReport report = harness.inspectSelected(context);

        assertTrue(report.successful(), () -> report.failures().toString());
        assertTrue(List.of(
                CheckpointRecoveryOutcome.LATEST_VALID_GENERATION_SELECTED,
                CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY
        ).contains(report.recoveryReport().orElseThrow().selection().outcome()));
        assertEquals(first.selectedGeneration().orElseThrow().generationId(),
                report.selectedGeneration().orElseThrow().generationId());
        assertTrue(hasCheckpointFailure(report, CheckpointFailureCode.INVALID_HEAD_DIGEST));
    }

    @Test
    void concurrentCheckpointOperationIsRejected() {
        DevelopmentCheckpointOperationGuard guard = new DevelopmentCheckpointOperationGuard();
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness(guard);
        DevelopmentCheckpointRequestContext context = context(world("concurrent"));

        try (DevelopmentCheckpointOperationGuard.GuardLease ignored = guard.tryBegin().orElseThrow()) {
            DevelopmentCheckpointReport report = harness.capture(captureRequest(context, 5L));

            assertFalse(report.successful());
            assertTrue(hasFailure(report.failures(),
                    DevelopmentCheckpointFailureCode.CHECKPOINT_OPERATION_ALREADY_ACTIVE));
        }
    }

    @Test
    void recursiveCheckpointOperationIsRejectedBySameGuard() {
        DevelopmentCheckpointOperationGuard guard = new DevelopmentCheckpointOperationGuard();
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness(guard);
        DevelopmentCheckpointRequestContext context = context(world("recursive"));

        try (DevelopmentCheckpointOperationGuard.GuardLease ignored = guard.tryBegin().orElseThrow()) {
            DevelopmentCheckpointReport report = harness.restoreSelectedControlled(restoreRequest(
                    context,
                    new AtomicReference<>(clockAt(10L)),
                    new AtomicReference<>(schedulerAt(10L)),
                    SimulationWorkHandlerRegistry.empty()
            ));

            assertFalse(report.successful());
            assertTrue(hasFailure(report.failures(),
                    DevelopmentCheckpointFailureCode.CHECKPOINT_OPERATION_ALREADY_ACTIVE));
        }
    }

    @Test
    void captureFailureLeavesPriorGenerationAuthoritative() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("capture_failure"));
        DevelopmentCheckpointReport prior = harness.capture(captureRequest(context, 5L));

        DevelopmentCheckpointReport failed = harness.capture(new DevelopmentCheckpointCaptureRequest(
                context,
                clockAt(8L),
                schedulerAt(7L)
        ));
        DevelopmentCheckpointReport selected = harness.inspectSelected(context);

        assertFalse(failed.successful());
        assertTrue(hasFailure(failed.failures(), DevelopmentCheckpointFailureCode.OWNER_CAPTURE_FAILURE));
        assertEquals(prior.selectedGeneration().orElseThrow().generationId(),
                selected.selectedGeneration().orElseThrow().generationId());
    }

    @Test
    void captureDoesNotReplaceNormalSaveFiles() {
        DevelopmentCheckpointRequestContext context = context(world("normal_saves"));

        new DevelopmentCheckpointHarness().capture(captureRequest(context, 5L));

        assertFalse(Files.exists(context.worldRoot()
                .resolve("butchercraft")
                .resolve(SimulationSchema.FILE_NAME)));
        assertFalse(Files.exists(context.worldRoot()
                .resolve("butchercraft")
                .resolve(SchedulerSchema.FILE_NAME)));
    }

    @Test
    void checkpointRootUnavailableIsReported() throws IOException {
        DevelopmentCheckpointRequestContext context = context(world("root_unavailable"));
        Files.createDirectories(context.checkpointRoot().getParent());
        Files.writeString(context.checkpointRoot(), "not a directory", StandardCharsets.UTF_8);

        DevelopmentCheckpointReport report = new DevelopmentCheckpointHarness().list(context);

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), DevelopmentCheckpointFailureCode.CHECKPOINT_ROOT_UNAVAILABLE));
    }

    @Test
    void restorationPreparationFailureLeavesRuntimeStateUnchanged() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("restore_prep_failure"));
        SimulationWorkHandlerRegistry capturingHandlers = handlerRegistry();
        SimulationSchedulerManager capturedScheduler = schedulerAt(5L, capturingHandlers);
        capturedScheduler.submit(work("test:restore_prep_work", 5L, 6L), 5L);
        harness.capture(new DevelopmentCheckpointCaptureRequest(context, clockAt(5L), capturedScheduler));
        AtomicReference<SimulationClock> clockTarget = new AtomicReference<>(clockAt(100L));
        AtomicReference<SimulationSchedulerManager> schedulerTarget = new AtomicReference<>(schedulerAt(100L));

        DevelopmentCheckpointReport report = harness.restoreSelectedControlled(restoreRequest(
                context,
                clockTarget,
                schedulerTarget,
                SimulationWorkHandlerRegistry.empty()
        ));

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), DevelopmentCheckpointFailureCode.OWNER_PREPARATION_FAILURE));
        assertEquals(100L, clockTarget.get().simulationTick());
        assertEquals(100L, schedulerTarget.get().lastFinalizedSimulationTick());
    }

    @Test
    void controlledRestorationRestoresClockAndSchedulerTogether() {
        DevelopmentCheckpointHarness harness = new DevelopmentCheckpointHarness();
        DevelopmentCheckpointRequestContext context = context(world("restore_success"));
        harness.capture(captureRequest(context, 5L));
        AtomicReference<SimulationClock> clockTarget = new AtomicReference<>(clockAt(8L));
        AtomicReference<SimulationSchedulerManager> schedulerTarget = new AtomicReference<>(schedulerAt(8L));

        DevelopmentCheckpointReport report = harness.restoreSelectedControlled(restoreRequest(
                context,
                clockTarget,
                schedulerTarget,
                SimulationWorkHandlerRegistry.empty()
        ));

        assertTrue(report.successful(), () -> report.failures().toString());
        assertEquals(CheckpointCoordinatedRestorationOutcome.RESTORED,
                report.restorationReport().orElseThrow().outcome());
        assertEquals(5L, clockTarget.get().simulationTick());
        assertEquals(5L, schedulerTarget.get().lastFinalizedSimulationTick());
    }

    @Test
    void unsafeLiveRestorationIsRejected() {
        DevelopmentCheckpointRequestContext context = context(world("unsafe_restore"));

        DevelopmentCheckpointReport report = new DevelopmentCheckpointHarness().rejectUnsafeLiveRestore(context);

        assertFalse(report.successful());
        assertTrue(hasFailure(report.failures(), DevelopmentCheckpointFailureCode.UNSAFE_RESTORATION_BOUNDARY));
    }

    private DevelopmentCheckpointCaptureRequest captureRequest(
            DevelopmentCheckpointRequestContext context,
            long tick
    ) {
        return new DevelopmentCheckpointCaptureRequest(context, clockAt(tick), schedulerAt(tick));
    }

    private DevelopmentCheckpointRestorationRequest restoreRequest(
            DevelopmentCheckpointRequestContext context,
            AtomicReference<SimulationClock> clockTarget,
            AtomicReference<SimulationSchedulerManager> schedulerTarget,
            SimulationWorkHandlerRegistry handlerRegistry
    ) {
        return new DevelopmentCheckpointRestorationRequest(
                context,
                CONFIGURATION,
                new SimulationEventBus(),
                clockTarget::get,
                clockTarget::set,
                handlerRegistry,
                schedulerTarget::get,
                schedulerTarget::set
        );
    }

    private DevelopmentCheckpointRequestContext context(Path worldRoot) {
        return context(worldRoot, true);
    }

    private DevelopmentCheckpointRequestContext context(Path worldRoot, boolean enabled) {
        return context(worldRoot, enabled, worldIdentity(1_234L),
                DevelopmentPlatformDeterminismManifest.currentReference());
    }

    private DevelopmentCheckpointRequestContext context(
            Path worldRoot,
            boolean enabled,
            WorldIdentityRootReference worldIdentity,
            PlatformDeterminismManifestReference platformManifest
    ) {
        return new DevelopmentCheckpointRequestContext(
                enabled,
                worldRoot,
                DevelopmentCheckpointRoots.checkpointRoot(worldRoot),
                worldIdentity,
                platformManifest
        );
    }

    private Path world(String name) {
        return temporaryDirectory.resolve(name);
    }

    private SimulationClock clockAt(long tick) {
        SimulationClock clock = new SimulationClock(CONFIGURATION);
        clock.advance(tick);
        return clock;
    }

    private SimulationSchedulerManager schedulerAt(long tick) {
        return schedulerAt(tick, SimulationWorkHandlerRegistry.empty());
    }

    private SimulationSchedulerManager schedulerAt(long tick, SimulationWorkHandlerRegistry handlerRegistry) {
        return new SimulationSchedulerManager(
                SimulationStageRegistry.builtIn(),
                handlerRegistry,
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
                WorkOrigin.of("test:development_checkpoint", submissionTick, "test:development_checkpoint"),
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
                        java.util.Optional.empty(),
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

    private WorldIdentityRootReference worldIdentity(long seed) {
        WorldIdentityRootIdentity root = WorldIdentityRootIdentities.from(
                new WorldIdentityGenerator().generate(seed)
        );
        return new WorldIdentityRootReference(root.identity(), root.schemaVersion(), root.rootDigest());
    }

    private void corruptOwnerPayload(
            DevelopmentCheckpointRequestContext context,
            CheckpointGenerationId generationId
    ) throws IOException {
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(context.checkpointRoot());
        Path generationDirectory = store.layout().finalGenerationDirectory(generationId);
        Files.writeString(
                store.layout().ownerPayload(generationDirectory, CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER),
                "corrupt",
                StandardCharsets.UTF_8
        );
    }

    private Map<String, Integer> fileSnapshot(Path root) throws IOException {
        Map<String, Integer> snapshot = new TreeMap<>();
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                snapshot.put(
                        root.relativize(path).toString(),
                        Arrays.hashCode(Files.readAllBytes(path))
                );
            }
        }
        return snapshot;
    }

    private boolean hasFailure(
            List<DevelopmentCheckpointFailure> failures,
            DevelopmentCheckpointFailureCode code
    ) {
        return failures.stream().anyMatch(failure -> failure.code() == code);
    }

    private boolean hasCheckpointFailure(DevelopmentCheckpointReport report, CheckpointFailureCode code) {
        return report.checkpointFailures().stream().anyMatch(failure -> failure.code() == code);
    }
}
