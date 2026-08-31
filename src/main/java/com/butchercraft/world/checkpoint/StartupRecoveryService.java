package com.butchercraft.world.checkpoint;

import com.butchercraft.ButcherCraft;
import com.butchercraft.integration.checkpoint.LiveOwnerCoherenceAnalyzer;
import com.butchercraft.integration.checkpoint.LiveOwnerCoherenceReport;
import com.butchercraft.integration.checkpoint.LiveOwnerCoherenceStatus;
import com.butchercraft.integration.checkpoint.NativeOwnerRestorationAdapters;
import com.butchercraft.integration.checkpoint.NativeOwnerLogicalStateVerifier;
import com.butchercraft.integration.checkpoint.StartupCheckpointCandidateSelector;
import com.butchercraft.world.WorldIdentityService;
import com.butchercraft.world.identity.WorldIdentityRootIdentities;
import com.butchercraft.world.identity.WorldIdentityRootIdentity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicReference;

public final class StartupRecoveryService {
    public static final StartupRecoveryService INSTANCE = new StartupRecoveryService();

    private final AtomicReference<StartupRecoveryStatus> status =
            new AtomicReference<>(StartupRecoveryStatus.notStarted());

    private StartupRecoveryService() {
    }

    public void begin(ServerAboutToStartEvent event) {
        StartupMutationGateService.INSTANCE.begin(event.getServer());
        status.set(StartupRecoveryStatus.notStarted());
    }

    public void initialize(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        long started = System.nanoTime();
        StartupMutationGateService.INSTANCE.begin(server);
        status.set(analyzing());
        try {
            start(server, started);
        } catch (StartupRecoveryException exception) {
            status.set(blocked(status.get(), exception.failureCode(), exception.getMessage(), started));
            ButcherCraft.LOGGER.error("ButcherCraft startup recovery blocked: {}: {}",
                    exception.failureCode(), exception.getMessage(), exception);
            throw exception;
        } catch (RuntimeException exception) {
            StartupRecoveryException typed = new StartupRecoveryException(
                    StartupRecoveryFailureCode.UNKNOWN_OUTCOME,
                    "Startup recovery failed before owner mutation was authorized",
                    exception
            );
            status.set(blocked(status.get(), typed.failureCode(), typed.getMessage(), started));
            ButcherCraft.LOGGER.error("ButcherCraft startup recovery blocked", exception);
            throw typed;
        }
    }

    public void stop(ServerStoppingEvent event) {
        StartupMutationGateService.INSTANCE.clear(event.getServer());
        status.set(StartupRecoveryStatus.notStarted());
    }

    public StartupRecoveryStatus status() {
        return status.get();
    }

    private void start(MinecraftServer server, long started) {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path ownerRoot = worldRoot.resolve("butchercraft");
        Path checkpointRoot = LiveCheckpointParticipantRegistry.checkpointRoot(server);
        WorldIdentityRootReference world = worldReference(server);
        PlatformDeterminismManifestReference platform = LivePlatformDeterminismManifest.currentReference(server);
        CheckpointFilesystemRecoveryRequest request = new CheckpointFilesystemRecoveryRequest(
                LiveCheckpointParticipantRegistry.requiredOwners(), world, platform,
                LivePlatformDeterminismManifest.acceptedRecoveryReferences(server));
        CheckpointFilesystemStore checkpointStore = new CheckpointFilesystemStore(checkpointRoot);
        RestorationStorage restorationStorage = new RestorationStorage(checkpointRoot);
        OwnerNativeRestorationCoordinator coordinator = new OwnerNativeRestorationCoordinator(
                restorationStorage,
                NativeOwnerRestorationAdapters.forServer(server),
                (context, plans) -> verifyCompleteNativeSet(server, context, plans)
        );

        List<RestorationIntent> incomplete = restorationStorage.incompleteIntents();
        if (incomplete.size() > 1) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.RESTORATION_CONFLICT,
                    "Multiple incomplete restoration intents require operator review"
            );
        }
        if (incomplete.size() == 1) {
            resumeIncomplete(
                    server, worldRoot, ownerRoot, checkpointRoot, request, checkpointStore,
                    restorationStorage, coordinator, incomplete.get(0), started);
            return;
        }

        LiveOwnerCoherenceReport live = analyze(server, ownerRoot, world);
        if (live.coherent()) {
            StartupMutationGateService.INSTANCE.install(server, live.mutationGate());
            Optional<RestorationResult> last = restorationStorage.completedResults().stream().reduce((left, right) -> right);
            Optional<LegacySplitRecoveryResult> lastLegacy = last
                    .flatMap(result -> restorationStorage.loadIntent(result.restorationIdentity()))
                    .flatMap(RestorationIntent::recoveryResultIdentity)
                    .flatMap(identity -> new LegacySplitRecoveryPublicationStorage(checkpointRoot)
                            .completedResults().stream()
                            .filter(result -> result.resultIdentity().equals(identity))
                            .findFirst());
            boolean mutationPermitted = consequentialMutationPermitted(live.mutationGate());
            status.set(new StartupRecoveryStatus(
                    StartupRecoveryState.LIVE_SELECTED,
                    live.status(),
                    StartupRecoverySource.LIVE,
                    Optional.empty(), Optional.empty(), Optional.of("not_applicable_live_selected"),
                    Optional.empty(), 0,
                    live.clockTick(),
                    live.mutationGate().blockIdentities(),
                    last.map(RestorationResult::policyBRunIdentities).orElse(List.of()),
                    preservedAuthorizedWork(lastLegacy),
                    Optional.empty(),
                    last.map(RestorationResult::resultIdentity),
                    Optional.empty(),
                    mutationPermitted,
                    mutationPermitted
                            ? Optional.empty()
                            : Optional.of("Resolve the persisted recovery authority block before consequential mutation"),
                    live.analysisDurationNanos(), 0L, 0L, System.nanoTime() - started
            ));
            return;
        }

        long selectionStarted = System.nanoTime();
        var selected = new StartupCheckpointCandidateSelector().select(checkpointStore, request);
        long selectionDuration = System.nanoTime() - selectionStarted;
        if (!selected.successful()) {
            throw new StartupRecoveryException(
                    selected.failureCode(),
                    "Live owner state is incoherent and no valid complete-restorable checkpoint is available: "
                            + selected.rejectionSummary()
            );
        }
        CheckpointRecoveredGeneration generation = selected.generation().orElseThrow();
        Optional<LegacySplitRecoveryResult> legacyResult = matchingRecoveryResult(
                checkpointStore, request, checkpointRoot, generation);
        RestorationSource source = legacyResult.isPresent()
                ? RestorationSource.RECOVERY_GENERATION : RestorationSource.CHECKPOINT;
        CheckpointHeadRecord sourceHead = selected.sourceHead().orElseThrow();
        OwnerNativeRestorationContext context = new OwnerNativeRestorationContext(
                worldRoot, ownerRoot, world, generation.manifest(), source, legacyResult);
        String fallbackReason = selected.rejectedCandidates().isEmpty()
                ? "Live state rejected: " + live.issueSummary()
                : "Live state rejected: " + live.issueSummary()
                + "; checkpoint fallback: " + selected.rejectionSummary();
        status.set(restoring(live, generation.manifest(), source, selectionDuration,
                selected.previousValidGeneration(),
                selected.workstationRestorability().orElseThrow().status().serializedName(),
                preservedAuthorizedWork(legacyResult),
                fallbackReason, started));
        long restorationStarted = System.nanoTime();
        RestorationResult result = coordinator.prepareAndRestore(
                context, generation, sourceHead, RestorationProbe.NONE);
        finishRestoration(server, ownerRoot, world, live, result, selectionDuration,
                System.nanoTime() - restorationStarted, started,
                preservedAuthorizedWork(legacyResult),
                selected.previousValidGeneration(), fallbackReason);
    }

    private void resumeIncomplete(
            MinecraftServer server,
            Path worldRoot,
            Path ownerRoot,
            Path checkpointRoot,
            CheckpointFilesystemRecoveryRequest request,
            CheckpointFilesystemStore checkpointStore,
            RestorationStorage restorationStorage,
            OwnerNativeRestorationCoordinator coordinator,
            RestorationIntent intent,
            long started
    ) {
        long selectionStarted = System.nanoTime();
        CheckpointRecoveredGenerationReport recovered = checkpointStore.loadCommittedGenerationReadOnly(
                request, intent.generationId(), intent.generationManifestDigest());
        long selectionDuration = System.nanoTime() - selectionStarted;
        if (!recovered.successful()) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.RESTORATION_INCOMPLETE,
                    "Incomplete restoration no longer has its exact valid committed generation"
            );
        }
        CheckpointRecoveredGeneration generation = recovered.recoveredGeneration().orElseThrow();
        var workstation = com.butchercraft.workstation.checkpoint.WorkstationCheckpointRestorabilityVerifier
                .verify(generation);
        if (!workstation.restorable()) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.WORKSTATION_PROJECTION_INCOMPLETE,
                    "Incomplete restoration generation is no longer complete-restorable: "
                            + workstation.status()
            );
        }
        Optional<LegacySplitRecoveryResult> legacy = intent.source() == RestorationSource.RECOVERY_GENERATION
                ? Optional.of(matchingRecoveryResult(
                        checkpointStore, request, checkpointRoot, generation)
                .orElseThrow(() -> new StartupRecoveryException(
                        StartupRecoveryFailureCode.RESTORATION_INCOMPLETE,
                        "Incomplete recovery-generation restoration is missing its Recovery Result")))
                : Optional.empty();
        OwnerNativeRestorationContext context = new OwnerNativeRestorationContext(
                worldRoot, ownerRoot, intent.worldIdentityRoot(), generation.manifest(), intent.source(), legacy);
        status.set(new StartupRecoveryStatus(
                StartupRecoveryState.RESTORING,
                LiveOwnerCoherenceStatus.INCOHERENT,
                source(intent.source()),
                Optional.of(intent.generationId()),
                Optional.empty(),
                Optional.of(workstation.status().serializedName()),
                Optional.of(intent.restorationIdentity()),
                completedParticipants(restorationStorage, intent),
                OptionalLong.empty(), List.of(), List.of(),
                preservedAuthorizedWork(legacy),
                Optional.of("Resuming incomplete immutable restoration intent"),
                Optional.empty(), Optional.empty(),
                false,
                Optional.of("Wait for the exact immutable restoration intent to complete"),
                0L, selectionDuration, 0L, System.nanoTime() - started
        ));
        long restorationStarted = System.nanoTime();
        RestorationResult result = coordinator.resume(context, intent, RestorationProbe.NONE);
        finishRestoration(server, ownerRoot, intent.worldIdentityRoot(), null, result, selectionDuration,
                System.nanoTime() - restorationStarted, started,
                preservedAuthorizedWork(legacy),
                Optional.empty(), "Resumed incomplete immutable restoration intent");
    }

    private void finishRestoration(
            MinecraftServer server,
            Path ownerRoot,
            WorldIdentityRootReference world,
            LiveOwnerCoherenceReport priorLive,
            RestorationResult result,
            long selectionDuration,
            long restorationDuration,
            long started,
            List<String> preservedAuthorizedWork,
            Optional<CheckpointGenerationId> previousValidGeneration,
            String fallbackReason
    ) {
        LiveOwnerCoherenceReport restored = analyze(server, ownerRoot, world);
        if (!restored.coherent()) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.RESTORATION_INCOMPLETE,
                    "Owner-native restoration completed publication but restored live state is not coherent"
            );
        }
        if (!restored.mutationGate().equals(result.mutationGate())) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.AUTHORITY_BLOCK,
                    "Restored authority gate differs from immutable Restoration Result"
            );
        }
        StartupMutationGateService.INSTANCE.install(server, result.mutationGate());
        boolean blocked = result.mutationGate().wholeWorldConsequentialMutationBlocked();
        boolean mutationPermitted = consequentialMutationPermitted(result.mutationGate());
        status.set(new StartupRecoveryStatus(
                blocked ? StartupRecoveryState.RESTORED_WITH_AUTHORITY_BLOCK : StartupRecoveryState.RESTORED,
                priorLive == null ? LiveOwnerCoherenceStatus.INCOHERENT : priorLive.status(),
                source(result.source()),
                Optional.of(result.generationId()),
                previousValidGeneration,
                Optional.of("complete_restorable"),
                Optional.of(result.restorationIdentity()),
                result.participants().size(),
                OptionalLong.of(result.restoredSimulationTick()),
                result.mutationGate().blockIdentities(),
                result.policyBRunIdentities(),
                preservedAuthorizedWork,
                Optional.of(fallbackReason),
                Optional.of(result.resultIdentity()),
                Optional.empty(),
                mutationPermitted,
                mutationPermitted
                        ? Optional.empty()
                        : Optional.of("Resolve the persisted recovery authority block before consequential mutation"),
                (priorLive == null ? 0L : priorLive.analysisDurationNanos()) + restored.analysisDurationNanos(),
                selectionDuration,
                restorationDuration,
                System.nanoTime() - started
        ));
    }

    private static LiveOwnerCoherenceReport analyze(
            MinecraftServer server,
            Path ownerRoot,
            WorldIdentityRootReference world
    ) {
        return new LiveOwnerCoherenceAnalyzer().analyze(server, ownerRoot, world);
    }

    private static void verifyCompleteNativeSet(
            MinecraftServer server,
            OwnerNativeRestorationContext context,
            List<OwnerNativeRestorationPlan> plans
    ) {
        NativeOwnerLogicalStateVerifier.verify(server, context);
        LiveOwnerCoherenceReport restored = analyze(server, context.ownerRoot(), context.worldIdentityRoot());
        if (!restored.coherent()) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.RESTORATION_INCOMPLETE,
                    "Restored owner-native set is not cross-owner coherent: " + restored.issueSummary()
            );
        }
        List<RecoveryMutationGate> expectedGates = plans.stream()
                .map(OwnerNativeRestorationPlan::mutationGate)
                .filter(gate -> gate.wholeWorldConsequentialMutationBlocked()
                        || !gate.blockIdentities().isEmpty()
                        || !gate.blockedAuthorityIdentities().isEmpty())
                .distinct().toList();
        if (expectedGates.size() > 1
                || (!expectedGates.isEmpty() && !expectedGates.getFirst().equals(restored.mutationGate()))
                || (expectedGates.isEmpty() && (restored.mutationGate().wholeWorldConsequentialMutationBlocked()
                || !restored.mutationGate().blockIdentities().isEmpty()
                || !restored.mutationGate().blockedAuthorityIdentities().isEmpty()))) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.AUTHORITY_BLOCK,
                    "Restored Planning authority state differs from the immutable owner plan"
            );
        }
    }

    private static WorldIdentityRootReference worldReference(MinecraftServer server) {
        WorldIdentityRootIdentity identity = WorldIdentityRootIdentities.from(
                WorldIdentityService.INSTANCE.getOrCreate(server));
        return new WorldIdentityRootReference(
                identity.identity(), identity.schemaVersion(), identity.rootDigest());
    }

    static Optional<LegacySplitRecoveryResult> matchingRecoveryResult(
            CheckpointFilesystemStore checkpointStore,
            CheckpointFilesystemRecoveryRequest request,
            Path checkpointRoot,
            CheckpointRecoveredGeneration generation
    ) {
        CheckpointGenerationManifest manifest = generation.manifest();
        List<LegacySplitRecoveryResult> matches = new LegacySplitRecoveryPublicationStorage(checkpointRoot)
                .completedResults().stream()
                .filter(result -> recoveryResultBindsGeneration(
                        checkpointStore, request, result, generation))
                .toList();
        if (matches.size() > 1) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.RESTORATION_CONFLICT,
                    "Multiple Recovery Results claim the same checkpoint generation");
        }
        return matches.stream().findFirst();
    }

    private static boolean recoveryResultBindsGeneration(
            CheckpointFilesystemStore checkpointStore,
            CheckpointFilesystemRecoveryRequest request,
            LegacySplitRecoveryResult result,
            CheckpointRecoveredGeneration generation
    ) {
        CheckpointGenerationManifest manifest = generation.manifest();
        boolean direct = result.recoveryGenerationId().equals(manifest.generationId())
                && result.generationManifestDigest().equals(manifest.manifestDigest());
        if (direct) return true;
        if (!manifest.triggerCauses().equals(List.of(
                CheckpointTriggerIdentities.LEGACY_WORKSTATION_PROJECTION_SUCCESSOR))
                || manifest.generationId().committedSequence()
                != result.recoveryGenerationId().committedSequence() + 1L
                || manifest.predecessorGenerationId().filter(result.recoveryGenerationId()::equals).isEmpty()
                || manifest.predecessorManifestDigest().filter(result.generationManifestDigest()::equals).isEmpty()
                || manifest.authoritativeSimulationTick()
                != result.recoveryGenerationId().authoritativeSimulationTick()) {
            return false;
        }
        CheckpointRecoveredGenerationReport predecessorReport = checkpointStore.loadCommittedGenerationReadOnly(
                request, result.recoveryGenerationId(), result.generationManifestDigest());
        return predecessorReport.successful()
                && preservesR3cOwnerSnapshots(
                predecessorReport.recoveredGeneration().orElseThrow(), generation);
    }

    private static boolean preservesR3cOwnerSnapshots(
            CheckpointRecoveredGeneration predecessor,
            CheckpointRecoveredGeneration successor
    ) {
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> prior = snapshotsByOwner(predecessor);
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> next = snapshotsByOwner(successor);
        if (!prior.keySet().equals(next.keySet())) return false;
        for (CheckpointOwnerId owner : prior.keySet()) {
            if (owner.equals(LegacySplitRecoveryParticipants.WORKSTATION)) continue;
            CheckpointOwnerSnapshotPayload left = prior.get(owner);
            CheckpointOwnerSnapshotPayload right = next.get(owner);
            OwnerSnapshotDescriptor leftDescriptor = left.descriptor();
            OwnerSnapshotDescriptor rightDescriptor = right.descriptor();
            if (!leftDescriptor.ownerId().equals(rightDescriptor.ownerId())
                    || leftDescriptor.snapshotSchemaVersion() != rightDescriptor.snapshotSchemaVersion()
                    || !leftDescriptor.snapshotIdentity().equals(rightDescriptor.snapshotIdentity())
                    || !leftDescriptor.contentDigest().equals(rightDescriptor.contentDigest())
                    || leftDescriptor.participation() != rightDescriptor.participation()
                    || !leftDescriptor.configurationIdentity().equals(rightDescriptor.configurationIdentity())
                    || !leftDescriptor.worldIdentityRoot().equals(rightDescriptor.worldIdentityRoot())
                    || leftDescriptor.representedSimulationTick()
                    != rightDescriptor.representedSimulationTick()
                    || leftDescriptor.ownerSequence() != rightDescriptor.ownerSequence()
                    || !Arrays.equals(left.payloadBytes(), right.payloadBytes())) {
                return false;
            }
        }
        return true;
    }

    private static Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> snapshotsByOwner(
            CheckpointRecoveredGeneration generation
    ) {
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> snapshots = new HashMap<>();
        generation.ownerSnapshots().forEach(snapshot -> snapshots.put(snapshot.descriptor().ownerId(), snapshot));
        return Map.copyOf(snapshots);
    }

    private static int completedParticipants(RestorationStorage storage, RestorationIntent intent) {
        return (int) intent.expectedOwners().stream()
                .filter(owner -> storage.loadParticipant(intent.restorationIdentity(), owner.ownerId()).isPresent())
                .count();
    }

    private static StartupRecoverySource source(RestorationSource source) {
        return source == RestorationSource.RECOVERY_GENERATION
                ? StartupRecoverySource.RECOVERY_GENERATION : StartupRecoverySource.CHECKPOINT;
    }

    private static StartupRecoveryStatus analyzing() {
        return new StartupRecoveryStatus(
                StartupRecoveryState.ANALYZING,
                LiveOwnerCoherenceStatus.NOT_ANALYZED,
                StartupRecoverySource.NONE,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 0, OptionalLong.empty(),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                false,
                Optional.of("Wait for startup recovery analysis to complete"),
                0L, 0L, 0L, 0L
        );
    }

    private static StartupRecoveryStatus restoring(
            LiveOwnerCoherenceReport live,
            CheckpointGenerationManifest manifest,
            RestorationSource source,
            long selectionNanos,
            Optional<CheckpointGenerationId> previousValidGeneration,
            String workstationRestorabilityStatus,
            List<String> preservedAuthorizedWork,
            String reason,
            long started
    ) {
        return new StartupRecoveryStatus(
                StartupRecoveryState.RESTORING, live.status(), source(source),
                Optional.of(manifest.generationId()), previousValidGeneration,
                Optional.of(workstationRestorabilityStatus),
                Optional.empty(), 0, OptionalLong.empty(),
                List.of(), List.of(), preservedAuthorizedWork,
                Optional.of(reason), Optional.empty(), Optional.empty(),
                false,
                Optional.of("Wait for owner-native restoration and exact read-back verification to complete"),
                live.analysisDurationNanos(), selectionNanos, 0L, System.nanoTime() - started
        );
    }

    private static StartupRecoveryStatus blocked(
            StartupRecoveryStatus previous,
            StartupRecoveryFailureCode code,
            String message,
            long started
    ) {
        Objects.requireNonNull(previous, "previous");
        return new StartupRecoveryStatus(
                StartupRecoveryState.BLOCKED,
                previous.liveCoherence(),
                previous.selectedSource(),
                previous.selectedGeneration(),
                previous.previousValidGeneration(),
                previous.workstationRestorabilityStatus(),
                previous.restorationIdentity(),
                previous.completedParticipants(),
                previous.restoredSimulationTick(),
                previous.remainingAuthorityBlocks(),
                previous.policyBRuns(),
                previous.preservedAuthorizedWork(),
                Optional.ofNullable(message),
                previous.lastRestorationResultIdentity(),
                Optional.of(code),
                false,
                Optional.of("Review the typed startup recovery failure and preserve the world before operator action"),
                previous.liveAnalysisNanos(),
                previous.checkpointSelectionNanos(),
                previous.restorationNanos(),
                System.nanoTime() - started
        );
    }

    private static List<String> preservedAuthorizedWork(Optional<LegacySplitRecoveryResult> result) {
        return result.stream()
                .flatMap(value -> value.preservedAuthorizedWork().stream())
                .map(value -> value.childIdentity() + " (run=" + value.machineRunIdentity()
                        + ", sequence=" + value.childSequence() + ", policy="
                        + value.restartPolicyIdentity() + ")")
                .sorted()
                .toList();
    }

    private static boolean consequentialMutationPermitted(RecoveryMutationGate gate) {
        return !gate.wholeWorldConsequentialMutationBlocked()
                && gate.blockedAuthorityIdentities().isEmpty()
                && gate.blockIdentities().isEmpty();
    }
}
