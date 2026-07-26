package com.butchercraft.development.checkpoint;

import com.butchercraft.world.checkpoint.CheckpointCoordinatedCaptureReport;
import com.butchercraft.world.checkpoint.CheckpointCoordinatedRestorationOutcome;
import com.butchercraft.world.checkpoint.CheckpointCoordinatedRestorationReport;
import com.butchercraft.world.checkpoint.CheckpointFailure;
import com.butchercraft.world.checkpoint.CheckpointFailureCode;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryReport;
import com.butchercraft.world.checkpoint.CheckpointFilesystemRecoveryRequest;
import com.butchercraft.world.checkpoint.CheckpointFilesystemStore;
import com.butchercraft.world.checkpoint.CheckpointGenerationId;
import com.butchercraft.world.checkpoint.CheckpointGenerationManifest;
import com.butchercraft.world.checkpoint.CheckpointGenerationRecord;
import com.butchercraft.world.checkpoint.CheckpointOwnerId;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotContext;
import com.butchercraft.world.checkpoint.CheckpointOwnerSnapshotCoordinator;
import com.butchercraft.world.checkpoint.CheckpointPublicationReport;
import com.butchercraft.world.checkpoint.CheckpointRecoveryOutcome;
import com.butchercraft.world.simulation.checkpoint.SimulationClockCheckpointSnapshotProvider;
import com.butchercraft.world.simulation.checkpoint.SimulationClockCheckpointSnapshotRestorer;
import com.butchercraft.world.simulation.scheduler.checkpoint.SimulationSchedulerCheckpointSnapshotProvider;
import com.butchercraft.world.simulation.scheduler.checkpoint.SimulationSchedulerCheckpointSnapshotRestorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class DevelopmentCheckpointHarness {
    public static final List<CheckpointOwnerId> REQUIRED_CLOCK_SCHEDULER_OWNERS = List.of(
            CheckpointOwnerSnapshotCoordinator.CLOCK_OWNER,
            CheckpointOwnerSnapshotCoordinator.SCHEDULER_OWNER
    );

    private final DevelopmentCheckpointOperationGuard guard;

    public DevelopmentCheckpointHarness() {
        this(new DevelopmentCheckpointOperationGuard());
    }

    public DevelopmentCheckpointHarness(DevelopmentCheckpointOperationGuard guard) {
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    public DevelopmentCheckpointReport capture(DevelopmentCheckpointCaptureRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<DevelopmentCheckpointReport> blocked = beforeOperation(
                DevelopmentCheckpointOperation.CAPTURE,
                request.context()
        );
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        Optional<DevelopmentCheckpointOperationGuard.GuardLease> lease = guard.tryBegin();
        if (lease.isEmpty()) {
            return alreadyActive(DevelopmentCheckpointOperation.CAPTURE, request.context());
        }
        try (DevelopmentCheckpointOperationGuard.GuardLease ignored = lease.orElseThrow()) {
            CheckpointFilesystemStore store = store(request.context());
            CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest(request.context()));
            CheckpointOwnerSnapshotContext context = captureContext(request, recovery);
            CheckpointCoordinatedCaptureReport capture = coordinatorForCapture(request).capture(context);
            if (!capture.successful()) {
                return report(
                        DevelopmentCheckpointOperation.CAPTURE,
                        false,
                        request.context(),
                        Optional.empty(),
                        Optional.of(recovery),
                        Optional.empty(),
                        capture.failures(),
                        List.of(new DevelopmentCheckpointFailure(
                                DevelopmentCheckpointFailureCode.OWNER_CAPTURE_FAILURE,
                                "owners",
                                "One or more checkpoint owners rejected capture"
                        )),
                        List.of()
                );
            }

            CheckpointPublicationReport publication = store.publish(capture.publicationRequest().orElseThrow());
            CheckpointFilesystemRecoveryReport afterPublication = store.recover(recoveryRequest(request.context()));
            List<DevelopmentCheckpointFailure> failures = publication.successful()
                    ? List.of()
                    : List.of(new DevelopmentCheckpointFailure(
                            DevelopmentCheckpointFailureCode.PUBLICATION_FAILURE,
                            "publication",
                            "Checkpoint generation was not published"
                    ));
            return report(
                    DevelopmentCheckpointOperation.CAPTURE,
                    publication.successful(),
                    request.context(),
                    Optional.of(publication),
                    Optional.of(afterPublication),
                    Optional.empty(),
                    publication.diagnostics(),
                    failures,
                    List.of(DevelopmentPlatformDeterminismManifest.guaranteeDescription())
            );
        }
    }

    public DevelopmentCheckpointReport list(DevelopmentCheckpointRequestContext context) {
        Objects.requireNonNull(context, "context");
        Optional<DevelopmentCheckpointReport> blocked = beforeOperation(DevelopmentCheckpointOperation.LIST, context);
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        return report(
                DevelopmentCheckpointOperation.LIST,
                true,
                context,
                Optional.empty(),
                Optional.of(store(context).recover(recoveryRequest(context))),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of()
        );
    }

    public DevelopmentCheckpointReport validate(DevelopmentCheckpointRequestContext context) {
        Objects.requireNonNull(context, "context");
        Optional<DevelopmentCheckpointReport> blocked = beforeOperation(
                DevelopmentCheckpointOperation.VALIDATE,
                context
        );
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        CheckpointFilesystemRecoveryReport recovery = store(context).recover(recoveryRequest(context));
        List<CheckpointFailure> checkpointFailures = checkpointFailures(recovery);
        List<DevelopmentCheckpointFailure> failures = developmentFailuresFor(checkpointFailures);
        boolean successful = recovery.selection().selectedGenerationId().isPresent() && failures.isEmpty();
        return report(
                DevelopmentCheckpointOperation.VALIDATE,
                successful,
                context,
                Optional.empty(),
                Optional.of(recovery),
                Optional.empty(),
                checkpointFailures,
                failures,
                List.of()
        );
    }

    public DevelopmentCheckpointReport inspectSelected(DevelopmentCheckpointRequestContext context) {
        Objects.requireNonNull(context, "context");
        Optional<DevelopmentCheckpointReport> blocked = beforeOperation(
                DevelopmentCheckpointOperation.INSPECT_SELECTED,
                context
        );
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        CheckpointFilesystemRecoveryReport recovery = store(context).recover(recoveryRequest(context));
        List<CheckpointFailure> checkpointFailures = checkpointFailures(recovery);
        List<DevelopmentCheckpointFailure> failures = selectedGeneration(recovery).isPresent()
                ? developmentFailuresForWorldAndPlatform(checkpointFailures)
                : List.of(new DevelopmentCheckpointFailure(
                        DevelopmentCheckpointFailureCode.GENERATION_NOT_FOUND,
                        "selectedGeneration",
                        "No selected checkpoint generation is available"
                ));
        return report(
                DevelopmentCheckpointOperation.INSPECT_SELECTED,
                selectedGeneration(recovery).isPresent() && failures.isEmpty(),
                context,
                Optional.empty(),
                Optional.of(recovery),
                Optional.empty(),
                checkpointFailures,
                failures,
                List.of()
        );
    }

    public DevelopmentCheckpointReport inspectGeneration(
            DevelopmentCheckpointRequestContext context,
            String generationSelector
    ) {
        Objects.requireNonNull(context, "context");
        Optional<DevelopmentCheckpointReport> blocked = beforeOperation(
                DevelopmentCheckpointOperation.INSPECT_GENERATION,
                context
        );
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        DevelopmentCheckpointGenerationSelector.Selection selection =
                DevelopmentCheckpointGenerationSelector.parse(generationSelector);
        if (!selection.successful()) {
            return new DevelopmentCheckpointReport(
                    DevelopmentCheckpointOperation.INSPECT_GENERATION,
                    false,
                    Optional.of(context.checkpointRoot()),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    selection.failures(),
                    List.of()
            );
        }

        CheckpointFilesystemRecoveryReport recovery = store(context).recover(recoveryRequest(context));
        Optional<DevelopmentCheckpointGenerationSummary> generation = recovery.generationRecords().stream()
                .map(CheckpointGenerationRecord::manifest)
                .filter(manifest -> manifest.generationId().equals(selection.generationId().orElseThrow()))
                .map(DevelopmentCheckpointGenerationSummary::from)
                .findFirst();
        List<CheckpointFailure> checkpointFailures = checkpointFailures(recovery);
        List<DevelopmentCheckpointFailure> failures = new ArrayList<>(
                developmentFailuresForWorldAndPlatform(checkpointFailures)
        );
        if (generation.isEmpty()) {
            failures.add(new DevelopmentCheckpointFailure(
                    DevelopmentCheckpointFailureCode.GENERATION_NOT_FOUND,
                    "generation",
                    "Requested checkpoint generation was not found as a valid committed generation"
            ));
        }
        return new DevelopmentCheckpointReport(
                DevelopmentCheckpointOperation.INSPECT_GENERATION,
                generation.isPresent() && failures.isEmpty(),
                Optional.of(context.checkpointRoot()),
                Optional.empty(),
                Optional.of(recovery),
                Optional.empty(),
                generation,
                summaries(recovery),
                recovery.headRecords(),
                recovery.artifacts(),
                checkpointFailures,
                failures,
                List.of()
        );
    }

    public DevelopmentCheckpointReport rejectUnsafeLiveRestore(DevelopmentCheckpointRequestContext context) {
        Objects.requireNonNull(context, "context");
        Optional<DevelopmentCheckpointReport> blocked = beforeOperation(
                DevelopmentCheckpointOperation.RESTORE_SELECTED,
                context
        );
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        return DevelopmentCheckpointReport.blocked(
                DevelopmentCheckpointOperation.RESTORE_SELECTED,
                Optional.of(context.checkpointRoot()),
                new DevelopmentCheckpointFailure(
                        DevelopmentCheckpointFailureCode.UNSAFE_RESTORATION_BOUNDARY,
                        "liveWorld",
                        "Loaded-world restoration is gated until a safe Scheduler and Clock runtime boundary exists"
                )
        );
    }

    public DevelopmentCheckpointReport restoreSelectedControlled(DevelopmentCheckpointRestorationRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<DevelopmentCheckpointReport> blocked = beforeOperation(
                DevelopmentCheckpointOperation.RESTORE_SELECTED,
                request.context()
        );
        if (blocked.isPresent()) {
            return blocked.orElseThrow();
        }
        Optional<DevelopmentCheckpointOperationGuard.GuardLease> lease = guard.tryBegin();
        if (lease.isEmpty()) {
            return alreadyActive(DevelopmentCheckpointOperation.RESTORE_SELECTED, request.context());
        }
        try (DevelopmentCheckpointOperationGuard.GuardLease ignored = lease.orElseThrow()) {
            CheckpointFilesystemStore store = store(request.context());
            CheckpointCoordinatedRestorationReport restoration = coordinatorForRestore(request)
                    .restoreSelected(store, recoveryRequest(request.context()));
            CheckpointFilesystemRecoveryReport recovery = store.recover(recoveryRequest(request.context()));
            List<CheckpointFailure> checkpointFailures = new ArrayList<>(checkpointFailures(recovery));
            checkpointFailures.addAll(restoration.failures());
            List<DevelopmentCheckpointFailure> failures = developmentFailuresForRestoration(restoration);
            return report(
                    DevelopmentCheckpointOperation.RESTORE_SELECTED,
                    restoration.outcome() == CheckpointCoordinatedRestorationOutcome.RESTORED,
                    request.context(),
                    Optional.empty(),
                    Optional.of(recovery),
                    Optional.of(restoration),
                    checkpointFailures,
                    failures,
                    List.of(DevelopmentPlatformDeterminismManifest.guaranteeDescription())
            );
        }
    }

    private Optional<DevelopmentCheckpointReport> beforeOperation(
            DevelopmentCheckpointOperation operation,
            DevelopmentCheckpointRequestContext context
    ) {
        if (!context.developmentEnabled()) {
            return Optional.of(DevelopmentCheckpointReport.blocked(
                    operation,
                    Optional.of(context.checkpointRoot()),
                    new DevelopmentCheckpointFailure(
                            DevelopmentCheckpointFailureCode.NOT_IN_DEVELOPMENT_ENVIRONMENT,
                            "developmentEnabled",
                            "Development checkpoint invocation is disabled"
                    )
            ));
        }
        try {
            Files.createDirectories(context.checkpointRoot());
            if (!Files.isDirectory(context.checkpointRoot())) {
                return Optional.of(rootUnavailable(operation, context.checkpointRoot()));
            }
        } catch (IOException | SecurityException exception) {
            return Optional.of(rootUnavailable(operation, context.checkpointRoot()));
        }
        return Optional.empty();
    }

    private DevelopmentCheckpointReport alreadyActive(
            DevelopmentCheckpointOperation operation,
            DevelopmentCheckpointRequestContext context
    ) {
        return DevelopmentCheckpointReport.blocked(
                operation,
                Optional.of(context.checkpointRoot()),
                new DevelopmentCheckpointFailure(
                        DevelopmentCheckpointFailureCode.CHECKPOINT_OPERATION_ALREADY_ACTIVE,
                        "operation",
                        "Another development checkpoint operation is already active"
                )
        );
    }

    private DevelopmentCheckpointReport rootUnavailable(
            DevelopmentCheckpointOperation operation,
            Path checkpointRoot
    ) {
        return DevelopmentCheckpointReport.blocked(
                operation,
                Optional.of(checkpointRoot),
                new DevelopmentCheckpointFailure(
                        DevelopmentCheckpointFailureCode.CHECKPOINT_ROOT_UNAVAILABLE,
                        "checkpointRoot",
                        "Development checkpoint root is unavailable"
                )
        );
    }

    private CheckpointOwnerSnapshotContext captureContext(
            DevelopmentCheckpointCaptureRequest request,
            CheckpointFilesystemRecoveryReport recovery
    ) {
        long tick = request.clock().simulationTick();
        Optional<CheckpointGenerationId> predecessorId = recovery.selection().selectedGenerationId();
        Optional<String> predecessorDigest = recovery.selection().selectedManifestDigest();
        long committedSequence = nextCommittedSequence(recovery);
        return new CheckpointOwnerSnapshotContext(
                CheckpointGenerationId.of(committedSequence, tick),
                predecessorId,
                predecessorDigest,
                tick,
                request.context().platformDeterminismManifest(),
                request.context().worldIdentityRoot()
        );
    }

    private long nextCommittedSequence(CheckpointFilesystemRecoveryReport recovery) {
        long highestGeneration = recovery.generationRecords().stream()
                .map(CheckpointGenerationRecord::generationId)
                .mapToLong(CheckpointGenerationId::committedSequence)
                .max()
                .orElse(0L);
        long highestHead = recovery.headRecords().stream()
                .mapToLong(head -> head.selectedGenerationId().committedSequence())
                .max()
                .orElse(0L);
        return Math.addExact(Math.max(highestGeneration, highestHead), 1L);
    }

    private CheckpointOwnerSnapshotCoordinator coordinatorForCapture(DevelopmentCheckpointCaptureRequest request) {
        return new CheckpointOwnerSnapshotCoordinator(
                REQUIRED_CLOCK_SCHEDULER_OWNERS,
                List.of(
                        new SimulationClockCheckpointSnapshotProvider(request.clock()),
                        new SimulationSchedulerCheckpointSnapshotProvider(request.scheduler())
                ),
                List.of()
        );
    }

    private CheckpointOwnerSnapshotCoordinator coordinatorForRestore(DevelopmentCheckpointRestorationRequest request) {
        return new CheckpointOwnerSnapshotCoordinator(
                REQUIRED_CLOCK_SCHEDULER_OWNERS,
                List.of(),
                List.of(
                        new SimulationClockCheckpointSnapshotRestorer(
                                request.clockConfiguration(),
                                request.clockEventBus(),
                                request.currentClock(),
                                request.publishClock()
                        ),
                        new SimulationSchedulerCheckpointSnapshotRestorer(
                                request.schedulerHandlerRegistry(),
                                request.currentScheduler(),
                                request.publishScheduler()
                        )
                )
        );
    }

    private DevelopmentCheckpointReport report(
            DevelopmentCheckpointOperation operation,
            boolean successful,
            DevelopmentCheckpointRequestContext context,
            Optional<CheckpointPublicationReport> publication,
            Optional<CheckpointFilesystemRecoveryReport> recovery,
            Optional<CheckpointCoordinatedRestorationReport> restoration,
            List<CheckpointFailure> checkpointFailures,
            List<DevelopmentCheckpointFailure> failures,
            List<String> warnings
    ) {
        return new DevelopmentCheckpointReport(
                operation,
                successful,
                Optional.of(context.checkpointRoot()),
                publication,
                recovery,
                restoration,
                recovery.flatMap(this::selectedGeneration),
                recovery.map(this::summaries).orElse(List.of()),
                recovery.map(CheckpointFilesystemRecoveryReport::headRecords).orElse(List.of()),
                recovery.map(CheckpointFilesystemRecoveryReport::artifacts).orElse(List.of()),
                checkpointFailures,
                failures,
                warnings
        );
    }

    private Optional<DevelopmentCheckpointGenerationSummary> selectedGeneration(
            CheckpointFilesystemRecoveryReport recovery
    ) {
        Optional<CheckpointGenerationId> selectedId = recovery.selection().selectedGenerationId();
        Optional<String> selectedDigest = recovery.selection().selectedManifestDigest();
        if (selectedId.isEmpty() || selectedDigest.isEmpty()) {
            return Optional.empty();
        }
        return recovery.generationRecords().stream()
                .map(CheckpointGenerationRecord::manifest)
                .filter(manifest -> manifest.generationId().equals(selectedId.orElseThrow()))
                .filter(manifest -> manifest.manifestDigest().equals(selectedDigest.orElseThrow()))
                .map(DevelopmentCheckpointGenerationSummary::from)
                .findFirst();
    }

    private List<DevelopmentCheckpointGenerationSummary> summaries(CheckpointFilesystemRecoveryReport recovery) {
        return recovery.generationRecords().stream()
                .map(CheckpointGenerationRecord::manifest)
                .map(DevelopmentCheckpointGenerationSummary::from)
                .sorted()
                .toList();
    }

    private CheckpointFilesystemStore store(DevelopmentCheckpointRequestContext context) {
        return new CheckpointFilesystemStore(context.checkpointRoot());
    }

    private CheckpointFilesystemRecoveryRequest recoveryRequest(DevelopmentCheckpointRequestContext context) {
        return new CheckpointFilesystemRecoveryRequest(
                REQUIRED_CLOCK_SCHEDULER_OWNERS,
                context.worldIdentityRoot(),
                context.platformDeterminismManifest()
        );
    }

    private List<CheckpointFailure> checkpointFailures(CheckpointFilesystemRecoveryReport recovery) {
        List<CheckpointFailure> failures = new ArrayList<>(recovery.selection().diagnostics());
        recovery.artifacts().stream()
                .map(artifact -> artifact.failure())
                .forEach(failures::add);
        return failures;
    }

    private List<DevelopmentCheckpointFailure> developmentFailuresFor(List<CheckpointFailure> checkpointFailures) {
        List<DevelopmentCheckpointFailure> failures = new ArrayList<>();
        for (CheckpointFailure checkpointFailure : checkpointFailures) {
            failures.add(developmentFailureFor(checkpointFailure));
        }
        return failures;
    }

    private List<DevelopmentCheckpointFailure> developmentFailuresForWorldAndPlatform(
            List<CheckpointFailure> checkpointFailures
    ) {
        return checkpointFailures.stream()
                .filter(failure -> failure.code() == CheckpointFailureCode.WORLD_IDENTITY_MISMATCH
                        || failure.code() == CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH)
                .map(this::developmentFailureFor)
                .toList();
    }

    private List<DevelopmentCheckpointFailure> developmentFailuresForRestoration(
            CheckpointCoordinatedRestorationReport restoration
    ) {
        if (restoration.outcome() == CheckpointCoordinatedRestorationOutcome.RESTORED) {
            return List.of();
        }
        List<DevelopmentCheckpointFailure> failures = new ArrayList<>();
        if (restoration.failures().isEmpty()) {
            failures.add(new DevelopmentCheckpointFailure(
                    DevelopmentCheckpointFailureCode.RECOVERY_BLOCKED_STATE,
                    "restoration",
                    "Checkpoint restoration was blocked"
            ));
            return failures;
        }
        for (CheckpointFailure failure : restoration.failures()) {
            failures.add(developmentFailureFor(failure));
        }
        return failures;
    }

    private DevelopmentCheckpointFailure developmentFailureFor(CheckpointFailure failure) {
        DevelopmentCheckpointFailureCode code = switch (failure.code()) {
            case WORLD_IDENTITY_MISMATCH -> DevelopmentCheckpointFailureCode.WORLD_IDENTITY_MISMATCH;
            case PLATFORM_DETERMINISM_MANIFEST_MISMATCH ->
                    DevelopmentCheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH;
            case OWNER_RESTORATION_VALIDATION_FAILURE, UNSUPPORTED_OWNER_SNAPSHOT_SCHEMA,
                    SCHEDULER_INVARIANT_VIOLATION -> DevelopmentCheckpointFailureCode.OWNER_PREPARATION_FAILURE;
            case OWNER_PUBLICATION_FAILURE, COORDINATED_PUBLICATION_FAILURE ->
                    DevelopmentCheckpointFailureCode.OWNER_PUBLICATION_FAILURE;
            case PARTIAL_RESTORATION_ATTEMPT -> DevelopmentCheckpointFailureCode.ROLLBACK_FAILURE;
            case RECOVERY_BLOCKED_STATE -> DevelopmentCheckpointFailureCode.RECOVERY_BLOCKED_STATE;
            default -> DevelopmentCheckpointFailureCode.INTEGRITY_FAILURE;
        };
        return new DevelopmentCheckpointFailure(code, failure.field(), failure.message());
    }

    public boolean selectedWithFallback(DevelopmentCheckpointReport report) {
        return report.recoveryReport()
                .map(CheckpointFilesystemRecoveryReport::selection)
                .map(selection -> selection.outcome()
                        == CheckpointRecoveryOutcome.OLDER_VALID_GENERATION_SELECTED_AUTOMATICALLY)
                .orElse(false);
    }
}
