package com.butchercraft.world.checkpoint;

import com.butchercraft.ButcherCraft;
import com.butchercraft.world.SimulationSchedulerService;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import com.butchercraft.world.simulation.SimulationClockService;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class LiveCheckpointService {
    public static final LiveCheckpointService INSTANCE = new LiveCheckpointService();

    private final AtomicReference<ActiveWorld> active = new AtomicReference<>();
    private final AtomicReference<LiveCheckpointStatus> status = new AtomicReference<>(LiveCheckpointStatus.inactive());

    private LiveCheckpointService() {
    }

    public synchronized void initialize(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ActiveWorld existing = active.get();
        if (existing != null && existing.server() == server) return;
        if (existing != null) closeExecutor(existing, true);
        CheckpointPublicationTiming timing = new CheckpointPublicationTiming();
        ActiveWorld created = new ActiveWorld(
                server,
                new CheckpointFilesystemStore(
                        LiveCheckpointParticipantRegistry.checkpointRoot(server),
                        timing
                ),
                timing,
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "butchercraft-live-checkpoint-publication");
                    thread.setDaemon(true);
                    return thread;
                }),
                EnumSet.noneOf(LiveCheckpointTriggerCause.class),
                new AtomicReference<>(),
                committedState(server),
                0L
        );
        created.periodicEligibilityTick(nextPeriodicTick(created.committedState()));
        active.set(created);
        publishIdleStatus(created);
    }

    public void advance(ServerTickEvent.Post event) {
        if (!StartupMutationGateService.INSTANCE.permits(
                event.getServer(), LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY)) return;
        ActiveWorld current = active.get();
        if (current == null || current.server() != event.getServer()) return;
        long tick = SimulationClockService.INSTANCE.clock(event.getServer()).simulationTick();
        long finalizedTick = SimulationSchedulerService.INSTANCE.managerFor(event.getServer())
                .lastFinalizedSimulationTick();
        if (tick != finalizedTick) {
            failBoundary(current, tick, new CheckpointFailure(
                    CheckpointFailureCode.CLOCK_SCHEDULER_TICK_MISMATCH,
                    "authoritativeSimulationTick",
                    "Live checkpoint boundary rejected because Clock and Scheduler ticks differ"
            ));
            return;
        }
        synchronized (this) {
            if (tick >= current.periodicEligibilityTick()) {
                current.pendingCauses().add(LiveCheckpointTriggerCause.PERIODIC);
                current.periodicEligibilityTick(Math.addExact(tick, LiveCheckpointPolicy.PERIODIC_INTERVAL_TICKS));
            }
            if (!current.pendingCauses().isEmpty() && !publicationActive(current)) {
                freezeAndPublish(current, tick);
            }
        }
    }

    public synchronized LiveCheckpointRequestResult requestManual(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!StartupMutationGateService.INSTANCE.permits(
                server, LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY)) {
            return new LiveCheckpointRequestResult(
                    LiveCheckpointRequestOutcome.RECOVERY_BLOCKED,
                    Optional.empty(),
                    "Checkpoint publication is blocked by startup recovery authority"
            );
        }
        ActiveWorld current = active.get();
        if (current == null || current.server() != server) {
            return new LiveCheckpointRequestResult(
                    LiveCheckpointRequestOutcome.NOT_INITIALIZED,
                    Optional.empty(),
                    "Live checkpoint service is not initialized"
            );
        }
        if (publicationActive(current)) {
            return new LiveCheckpointRequestResult(
                    LiveCheckpointRequestOutcome.BUSY,
                    status.get().currentGeneration(),
                    "A checkpoint generation is already being published"
            );
        }
        boolean added = current.pendingCauses().add(LiveCheckpointTriggerCause.MANUAL);
        LiveCheckpointStatus prior = status.get();
        status.set(new LiveCheckpointStatus(
                LiveCheckpointStatus.State.REQUESTED,
                Optional.empty(),
                prior.committedGeneration(),
                prior.previousValidGeneration(),
                prior.checkpointTick(),
                prior.participantCount(),
                Set.copyOf(current.pendingCauses()),
                0L, 0L, 0L, 0L, prior.checkpointSizeBytes(),
                current.periodicEligibilityTick(), true, List.of()
        ));
        return new LiveCheckpointRequestResult(
                added ? LiveCheckpointRequestOutcome.ACCEPTED : LiveCheckpointRequestOutcome.COALESCED,
                Optional.empty(),
                added ? "Checkpoint requested for the next safe boundary"
                        : "An identical checkpoint request is already pending"
        );
    }

    public synchronized void stop(ServerStoppingEvent event) {
        ActiveWorld current = active.get();
        if (current == null || current.server() != event.getServer()) return;
        if (!awaitPublication(current, LiveCheckpointPolicy.SHUTDOWN_WAIT_MILLIS)) {
            closeExecutor(current, true);
            active.compareAndSet(current, null);
            return;
        }
        if (!StartupMutationGateService.INSTANCE.permits(
                event.getServer(), LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY)) {
            closeExecutor(current, false);
            active.compareAndSet(current, null);
            return;
        }
        long tick = SimulationClockService.INSTANCE.clock(event.getServer()).simulationTick();
        long finalizedTick = SimulationSchedulerService.INSTANCE.managerFor(event.getServer())
                .lastFinalizedSimulationTick();
        if (tick == finalizedTick && tick > current.committedState().tick()) {
            current.pendingCauses().add(LiveCheckpointTriggerCause.GRACEFUL_SHUTDOWN);
            freezeAndPublish(current, tick);
            boolean completed = awaitPublication(current, LiveCheckpointPolicy.SHUTDOWN_WAIT_MILLIS);
            closeExecutor(current, !completed);
        } else {
            closeExecutor(current, false);
        }
        active.compareAndSet(current, null);
    }

    public LiveCheckpointStatus status() {
        return status.get();
    }

    private void freezeAndPublish(ActiveWorld current, long tick) {
        Set<LiveCheckpointTriggerCause> causes = Set.copyOf(current.pendingCauses());
        current.pendingCauses().clear();
        long totalStart = System.nanoTime();
        PreparedContext prepared;
        try {
            prepared = prepareContext(current, tick);
        } catch (RuntimeException exception) {
            failBoundary(current, tick, failure(exception));
            return;
        }
        status.set(statusFor(
                LiveCheckpointStatus.State.FREEZING,
                prepared.context().generationId(),
                current,
                tick,
                causes,
                0L,
                0L,
                0L,
                0L,
                0L,
                List.of()
        ));
        long freezeStart = System.nanoTime();
        CheckpointCoordinatedCaptureReport capture = new CheckpointOwnerSnapshotCoordinator(
                LiveCheckpointParticipantRegistry.requiredOwners(),
                LiveCheckpointParticipantRegistry.providers(current.server(), tick),
                List.of()
        ).capture(prepared.context());
        long freezeDuration = System.nanoTime() - freezeStart;
        if (!capture.successful()) {
            status.set(statusFor(
                    LiveCheckpointStatus.State.FAILED,
                    prepared.context().generationId(),
                    current,
                    tick,
                    causes,
                    freezeDuration,
                    0L,
                    0L,
                    System.nanoTime() - totalStart,
                    0L,
                    capture.failures()
            ));
            return;
        }
        CheckpointPublicationRequest publicationRequest = capture.publicationRequest().orElseThrow()
                .withTriggerCauses(causes.stream()
                        .map(cause -> "butchercraft:checkpoint_trigger/" + cause.name().toLowerCase(java.util.Locale.ROOT))
                        .toList());
        status.set(statusFor(
                LiveCheckpointStatus.State.PUBLISHING,
                prepared.context().generationId(),
                current,
                tick,
                causes,
                freezeDuration,
                0L,
                0L,
                System.nanoTime() - totalStart,
                0L,
                List.of()
        ));
        Runnable publication = () -> publishFrozen(
                current,
                publicationRequest,
                causes,
                freezeDuration,
                totalStart
        );
        CompletableFuture<Void> future = CompletableFuture.runAsync(publication, current.executor());
        current.inProgress().set(future);
    }

    private void publishFrozen(
            ActiveWorld current,
            CheckpointPublicationRequest request,
            Set<LiveCheckpointTriggerCause> causes,
            long freezeDuration,
            long totalStart
    ) {
        long publicationStart = System.nanoTime();
        current.timing().beginPublication();
        CheckpointPublicationReport report = current.store().publish(request);
        long publicationDuration = System.nanoTime() - publicationStart;
        long headCommitDuration = current.timing().headCommitDurationNanos();
        long size = report.generationManifest()
                .map(manifest -> directorySize(current.store().layout().finalGenerationDirectory(manifest.generationId())))
                .orElse(0L);
        synchronized (this) {
            if (report.successful()) {
                CheckpointGenerationManifest manifest = report.generationManifest().orElseThrow();
                current.committedState(new CommittedState(
                        manifest.generationId(),
                        manifest.manifestDigest(),
                        manifest.authoritativeSimulationTick(),
                        previousGeneration(current.committedState())
                ));
                current.periodicEligibilityTick(Math.addExact(
                        manifest.authoritativeSimulationTick(),
                        LiveCheckpointPolicy.PERIODIC_INTERVAL_TICKS
                ));
                status.set(statusFor(
                        LiveCheckpointStatus.State.COMMITTED,
                        manifest.generationId(),
                        current,
                        manifest.authoritativeSimulationTick(),
                        causes,
                        freezeDuration,
                        publicationDuration,
                        headCommitDuration,
                        System.nanoTime() - totalStart,
                        size,
                        report.diagnostics()
                ));
                ButcherCraft.LOGGER.info(
                        "Live checkpoint committed: generation={}, tick={}, participants={}, causes={}, "
                                + "freeze_ms={}, publication_ms={}, head_commit_ms={}, total_ms={}, size_bytes={}",
                        manifest.generationId().canonicalValue(),
                        manifest.authoritativeSimulationTick(),
                        manifest.ownerSnapshots().size(),
                        causes,
                        TimeUnit.NANOSECONDS.toMillis(freezeDuration),
                        TimeUnit.NANOSECONDS.toMillis(publicationDuration),
                        TimeUnit.NANOSECONDS.toMillis(headCommitDuration),
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - totalStart),
                        size
                );
            } else {
                status.set(statusFor(
                        LiveCheckpointStatus.State.FAILED,
                        request.generationId(),
                        current,
                        request.authoritativeSimulationTick(),
                        causes,
                        freezeDuration,
                        publicationDuration,
                        headCommitDuration,
                        System.nanoTime() - totalStart,
                        size,
                        report.diagnostics()
                ));
            }
            current.inProgress().set(null);
        }
    }

    private PreparedContext prepareContext(ActiveWorld current, long tick) {
        WorldIdentityRootIdentity world = WorldIdentityRootIdentities.from(
                WorldIdentityService.INSTANCE.getOrCreate(current.server())
        );
        WorldIdentityRootReference worldRoot = new WorldIdentityRootReference(
                world.identity(), world.schemaVersion(), world.rootDigest()
        );
        PlatformDeterminismManifestReference platform =
                LivePlatformDeterminismManifest.currentReference(current.server());
        CommittedState committed = current.committedState();
        long sequence = committed.generationId().map(CheckpointGenerationId::committedSequence).orElse(0L) + 1L;
        CheckpointOwnerSnapshotContext context = new CheckpointOwnerSnapshotContext(
                CheckpointGenerationId.of(sequence, tick),
                committed.generationId(),
                committed.manifestDigest(),
                tick,
                platform,
                worldRoot
        );
        return new PreparedContext(context);
    }

    private CommittedState committedState(MinecraftServer server) {
        WorldIdentityRootIdentity world = WorldIdentityRootIdentities.from(WorldIdentityService.INSTANCE.getOrCreate(server));
        WorldIdentityRootReference worldRoot = new WorldIdentityRootReference(
                world.identity(), world.schemaVersion(), world.rootDigest()
        );
        CheckpointFilesystemRecoveryReport report = new CheckpointFilesystemStore(
                LiveCheckpointParticipantRegistry.checkpointRoot(server)
        ).inspectReadOnly(new CheckpointFilesystemRecoveryRequest(
                LiveCheckpointParticipantRegistry.requiredOwners(),
                worldRoot,
                LivePlatformDeterminismManifest.currentReference(server),
                LivePlatformDeterminismManifest.acceptedRecoveryReferences(server)
        ));
        Optional<CheckpointGenerationId> selected = report.selection().selectedGenerationId();
        Optional<String> digest = report.selection().selectedManifestDigest();
        if (selected.isEmpty()) return CommittedState.empty();
        List<CheckpointGenerationId> headed = report.headRecords().stream()
                .map(CheckpointHeadRecord::selectedGenerationId)
                .distinct()
                .sorted()
                .toList();
        Optional<CheckpointGenerationId> previous = headed.stream()
                .filter(id -> id.compareTo(selected.orElseThrow()) < 0)
                .reduce((left, right) -> right);
        return new CommittedState(
                selected,
                digest,
                selected.orElseThrow().authoritativeSimulationTick(),
                previous
        );
    }

    private void publishIdleStatus(ActiveWorld current) {
        status.set(new LiveCheckpointStatus(
                LiveCheckpointStatus.State.IDLE,
                Optional.empty(),
                current.committedState().generationId(),
                current.committedState().previousGeneration(),
                current.committedState().tick(),
                LiveCheckpointParticipantRegistry.requiredOwners().size(),
                Set.of(), 0L, 0L, 0L, 0L,
                0L,
                current.periodicEligibilityTick(), true, List.of()
        ));
    }

    private LiveCheckpointStatus statusFor(
            LiveCheckpointStatus.State state,
            CheckpointGenerationId currentGeneration,
            ActiveWorld current,
            long tick,
            Set<LiveCheckpointTriggerCause> causes,
            long freezeDuration,
            long publicationDuration,
            long headCommitDuration,
            long totalDuration,
            long size,
            List<CheckpointFailure> failures
    ) {
        return new LiveCheckpointStatus(
                state,
                Optional.of(currentGeneration),
                current.committedState().generationId(),
                current.committedState().previousGeneration(),
                tick,
                LiveCheckpointParticipantRegistry.requiredOwners().size(),
                causes,
                freezeDuration,
                publicationDuration,
                headCommitDuration,
                totalDuration,
                size,
                current.periodicEligibilityTick(),
                true,
                failures
        );
    }

    private void failBoundary(ActiveWorld current, long tick, CheckpointFailure failure) {
        LiveCheckpointStatus prior = status.get();
        status.set(new LiveCheckpointStatus(
                LiveCheckpointStatus.State.FAILED,
                Optional.empty(),
                current.committedState().generationId(),
                current.committedState().previousGeneration(),
                tick,
                LiveCheckpointParticipantRegistry.requiredOwners().size(),
                Set.copyOf(current.pendingCauses()),
                0L, 0L, 0L, 0L, prior.checkpointSizeBytes(),
                current.periodicEligibilityTick(), true, List.of(failure)
        ));
    }

    private static CheckpointFailure failure(RuntimeException exception) {
        return new CheckpointFailure(
                CheckpointFailureCode.RECOVERY_BLOCKED_STATE,
                "checkpointBoundary",
                exception.getMessage() == null ? "Live checkpoint boundary preparation failed" : exception.getMessage()
        );
    }

    private static boolean publicationActive(ActiveWorld current) {
        CompletableFuture<Void> future = current.inProgress().get();
        return future != null && !future.isDone();
    }

    private static boolean awaitPublication(ActiveWorld current, long timeoutMillis) {
        CompletableFuture<Void> future = current.inProgress().get();
        if (future == null) return true;
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (TimeoutException exception) {
            ButcherCraft.LOGGER.warn(
                    "Live checkpoint publication exceeded the {} ms shutdown wait; shutdown will continue and the "
                            + "already-frozen generation may still complete atomically",
                    timeoutMillis
            );
            return false;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ButcherCraft.LOGGER.warn(
                    "Live checkpoint shutdown wait was interrupted; shutdown will continue without further waiting"
            );
            return false;
        } catch (ExecutionException exception) {
            ButcherCraft.LOGGER.error(
                    "Live checkpoint publication failed during the shutdown wait; preserving the prior committed head",
                    exception.getCause()
            );
            return false;
        }
    }

    private static void closeExecutor(ActiveWorld current, boolean interruptPublication) {
        if (interruptPublication) {
            current.executor().shutdownNow();
        } else {
            current.executor().shutdown();
        }
        try {
            if (!current.executor().awaitTermination(1L, TimeUnit.SECONDS)) {
                ButcherCraft.LOGGER.warn(
                        "Live checkpoint publication executor did not terminate within the bounded shutdown grace period"
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static long nextPeriodicTick(CommittedState state) {
        long base = state.tick();
        return Math.addExact(base, LiveCheckpointPolicy.PERIODIC_INTERVAL_TICKS);
    }

    private static Optional<CheckpointGenerationId> previousGeneration(CommittedState state) {
        return state.generationId();
    }

    private static long directorySize(Path directory) {
        if (!Files.isDirectory(directory)) return 0L;
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile).mapToLong(path -> {
                try {
                    return Files.size(path);
                } catch (IOException exception) {
                    return 0L;
                }
            }).sum();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private record PreparedContext(CheckpointOwnerSnapshotContext context) {
    }

    private static final class ActiveWorld {
        private final MinecraftServer server;
        private final CheckpointFilesystemStore store;
        private final CheckpointPublicationTiming timing;
        private final ExecutorService executor;
        private final EnumSet<LiveCheckpointTriggerCause> pendingCauses;
        private final AtomicReference<CompletableFuture<Void>> inProgress;
        private volatile CommittedState committedState;
        private volatile long periodicEligibilityTick;

        private ActiveWorld(
                MinecraftServer server,
                CheckpointFilesystemStore store,
                CheckpointPublicationTiming timing,
                ExecutorService executor,
                EnumSet<LiveCheckpointTriggerCause> pendingCauses,
                AtomicReference<CompletableFuture<Void>> inProgress,
                CommittedState committedState,
                long periodicEligibilityTick
        ) {
            this.server = server;
            this.store = store;
            this.timing = timing;
            this.executor = executor;
            this.pendingCauses = pendingCauses;
            this.inProgress = inProgress;
            this.committedState = committedState;
            this.periodicEligibilityTick = periodicEligibilityTick;
        }

        private MinecraftServer server() { return server; }
        private CheckpointFilesystemStore store() { return store; }
        private CheckpointPublicationTiming timing() { return timing; }
        private ExecutorService executor() { return executor; }
        private EnumSet<LiveCheckpointTriggerCause> pendingCauses() { return pendingCauses; }
        private AtomicReference<CompletableFuture<Void>> inProgress() { return inProgress; }
        private CommittedState committedState() { return committedState; }
        private void committedState(CommittedState value) { committedState = value; }
        private long periodicEligibilityTick() { return periodicEligibilityTick; }
        private void periodicEligibilityTick(long value) { periodicEligibilityTick = value; }
    }

    private record CommittedState(
            Optional<CheckpointGenerationId> generationId,
            Optional<String> manifestDigest,
            long tick,
            Optional<CheckpointGenerationId> previousGeneration
    ) {
        private CommittedState {
            generationId = Objects.requireNonNull(generationId, "generationId");
            manifestDigest = Objects.requireNonNull(manifestDigest, "manifestDigest");
            tick = CheckpointValidation.nonNegative(tick, "tick");
            previousGeneration = Objects.requireNonNull(previousGeneration, "previousGeneration");
        }

        private CommittedState(
                CheckpointGenerationId generationId,
                String manifestDigest,
                long tick,
                Optional<CheckpointGenerationId> previousGeneration
        ) {
            this(Optional.of(generationId), Optional.of(manifestDigest), tick, previousGeneration);
        }

        private static CommittedState empty() {
            return new CommittedState(Optional.empty(), Optional.empty(), 0L, Optional.empty());
        }
    }
}
