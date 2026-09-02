package com.butchercraft.world.checkpoint;

import com.butchercraft.persistence.AtomicFilePublication;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Coordinates immutable, resumable publication of all owner-native checkpoint state. */
public final class OwnerNativeRestorationCoordinator {
    private static final RecoveryMutationGate OPEN_GATE = new RecoveryMutationGate(1, false, List.of(), List.of());

    private final RestorationStorage storage;
    private final Map<CheckpointOwnerId, OwnerNativeRestorationAdapter> adapters;
    private final RestorationCompletionVerifier completionVerifier;

    public OwnerNativeRestorationCoordinator(
            RestorationStorage storage,
            List<OwnerNativeRestorationAdapter> adapters
    ) {
        this(storage, adapters, RestorationCompletionVerifier.NONE);
    }

    public OwnerNativeRestorationCoordinator(
            RestorationStorage storage,
            List<OwnerNativeRestorationAdapter> adapters,
            RestorationCompletionVerifier completionVerifier
    ) {
        this.storage = Objects.requireNonNull(storage, "storage");
        Map<CheckpointOwnerId, OwnerNativeRestorationAdapter> values = new HashMap<>();
        Objects.requireNonNull(adapters, "adapters").forEach(adapter -> {
            OwnerNativeRestorationAdapter previous = values.put(adapter.ownerId(), adapter);
            if (previous != null) throw new IllegalArgumentException("Duplicate native restoration adapter");
        });
        if (!values.keySet().stream().sorted().toList()
                .equals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS)) {
            throw new IllegalArgumentException("Native restoration requires the exact 17-owner adapter set");
        }
        this.adapters = Map.copyOf(values);
        this.completionVerifier = Objects.requireNonNull(completionVerifier, "completionVerifier");
    }

    public RestorationResult prepareAndRestore(
            OwnerNativeRestorationContext context,
            CheckpointRecoveredGeneration generation,
            CheckpointHeadRecord sourceHead,
            RestorationProbe probe
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(sourceHead, "sourceHead");
        RestorationProbe observer = Objects.requireNonNull(probe, "probe");
        requireContextGeneration(context, generation.manifest(), sourceHead);

        Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> snapshots = new HashMap<>();
        generation.ownerSnapshots().forEach(snapshot -> {
            CheckpointOwnerSnapshotPayload previous = snapshots.put(snapshot.descriptor().ownerId(), snapshot);
            if (previous != null) throw blocked("Checkpoint generation contains a duplicate owner snapshot");
        });
        if (!snapshots.keySet().stream().sorted().toList()
                .equals(LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS)) {
            throw blocked("Checkpoint generation does not contain the exact 17-owner snapshot set");
        }

        RestorationIdentity identity = RestorationIdentity.derive(
                context.worldIdentityRoot(),
                generation.manifest(),
                sourceHead,
                context.source(),
                context.legacyRecoveryResult()
        );
        Optional<RestorationResult> completed = storage.loadResult(identity);
        if (completed.isPresent()) return completed.orElseThrow();

        Optional<RestorationIntent> existingIntent = storage.loadIntent(identity);
        if (existingIntent.isPresent()) {
            return resume(context, existingIntent.orElseThrow(), observer);
        }

        List<OwnerNativeRestorationPlan> plans = new ArrayList<>();
        Set<String> targetPaths = new HashSet<>();
        for (CheckpointOwnerId ownerId : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            OwnerNativeRestorationAdapter adapter = adapters.get(ownerId);
            try {
                OwnerNativeRestorationPlan plan = adapter.prepare(context, snapshots.get(ownerId))
                        .withObservedPreconditions(context.ownerRoot());
                adapter.verify(context, plan);
                plan.nativeFiles().forEach(file -> {
                    if (!targetPaths.add(file.targetRelativePath())) {
                        throw blocked("Multiple owners target the same native file: " + file.targetRelativePath());
                    }
                });
                plans.add(storage.saveOrObservePreparedPlan(identity, plan));
                observer.reached(RestorationPhase.PREPARED_OWNER, Optional.of(ownerId), Optional.empty());
            } catch (StartupRecoveryException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new StartupRecoveryException(
                        StartupRecoveryFailureCode.CHECKPOINT_INVALID,
                        "Owner-native restoration preparation failed for " + ownerId.value(),
                        exception
                );
            }
        }
        requireExactReservationFileOwnership(plans);
        RestorationIntent intent = RestorationIntent.prepare(
                identity,
                generation.manifest(),
                sourceHead,
                context.source(),
                context.legacyRecoveryResult(),
                plans
        );
        storage.saveOrObserveIntent(intent);
        observer.reached(RestorationPhase.INTENT_PUBLISHED, Optional.empty(), Optional.empty());
        return resume(context, intent, observer);
    }

    public RestorationResult resume(
            OwnerNativeRestorationContext context,
            RestorationIntent intent,
            RestorationProbe probe
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(intent, "intent");
        RestorationProbe observer = Objects.requireNonNull(probe, "probe");
        requireIntentContext(context, intent);
        Optional<RestorationResult> completed = storage.loadResult(intent.restorationIdentity());
        if (completed.isPresent()) return completed.orElseThrow();

        List<OwnerNativeRestorationPlan> plans = storage.loadCompletePreparedPlans(intent);
        requireExactReservationFileOwnership(plans);
        List<RestorationParticipantResult> participants = new ArrayList<>();
        for (OwnerNativeRestorationPlan plan : plans) {
            OwnerNativeRestorationAdapter adapter = adapters.get(plan.ownerId());
            adapter.verify(context, plan);
            Optional<RestorationParticipantResult> existing = storage.loadParticipant(
                    intent.restorationIdentity(), plan.ownerId());
            if (existing.isPresent()) {
                verifyPublishedFiles(context.ownerRoot(), plan);
                participants.add(existing.orElseThrow());
                continue;
            }
            List<RestorationParticipantResult.PublishedFile> published = new ArrayList<>();
            for (OwnerNativeRestorationPlan.NativeFile file : plan.nativeFiles()) {
                Path target = target(context.ownerRoot(), file.targetRelativePath());
                try {
                    AtomicFilePublication.ConditionalPublication outcome =
                            AtomicFilePublication.publishBytesIfDigestMatches(
                                    target,
                                    file.observedPreRestorationDigest(),
                                    file.bytes(),
                                    "owner-native restoration " + plan.ownerId().value() + "/" + file.logicalName()
                            );
                    byte[] observed = AtomicFilePublication.readBytes(target, "restored owner-native file");
                    if (!CheckpointSnapshotDigest.sha256(observed).equals(file.contentDigest())) {
                        throw conflict("Restored owner-native file failed exact read-back verification: "
                                + file.targetRelativePath());
                    }
                    published.add(new RestorationParticipantResult.PublishedFile(
                            file.logicalName(),
                            file.targetRelativePath(),
                            file.contentDigest(),
                            file.bytes().length,
                            outcome == AtomicFilePublication.ConditionalPublication.OBSERVED_EXACT
                                    ? RestorationParticipantResult.Publication.OBSERVED_EXACT
                                    : RestorationParticipantResult.Publication.PUBLISHED_ATOMICALLY
                    ));
                    observer.reached(
                            RestorationPhase.NATIVE_FILE_PUBLISHED,
                            Optional.of(plan.ownerId()),
                            Optional.of(file.logicalName())
                    );
                } catch (StartupRecoveryException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    throw new StartupRecoveryException(
                            StartupRecoveryFailureCode.RESTORATION_CONFLICT,
                            "Owner-native restoration could not publish " + file.targetRelativePath(),
                            exception
                    );
                }
            }
            adapter.reconcileProjection(context, plan);
            RestorationParticipantResult participant = RestorationParticipantResult.complete(
                    intent.restorationIdentity(), plan, published, true);
            participants.add(storage.saveOrObserveParticipant(participant));
            observer.reached(RestorationPhase.OWNER_COMPLETED, Optional.of(plan.ownerId()), Optional.empty());
        }

        observer.reached(RestorationPhase.NATIVE_SET_PUBLISHED, Optional.empty(), Optional.empty());
        try {
            completionVerifier.verify(context, plans);
        } catch (StartupRecoveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new StartupRecoveryException(
                    StartupRecoveryFailureCode.RESTORATION_INCOMPLETE,
                    "Owner-native restoration failed final cross-owner verification",
                    exception
            );
        }
        observer.reached(RestorationPhase.RESTORATION_VERIFIED, Optional.empty(), Optional.empty());
        observer.reached(RestorationPhase.RESULT_PENDING, Optional.empty(), Optional.empty());
        RestorationResult result = RestorationResult.complete(
                intent,
                participants,
                mutationGate(plans),
                policyBRuns(plans),
                Optional.of("simulation_tick:" + intent.generationId().authoritativeSimulationTick())
        );
        RestorationResult stored = storage.saveOrObserveResult(result);
        observer.reached(RestorationPhase.RESULT_PUBLISHED, Optional.empty(), Optional.empty());
        return stored;
    }

    private static void requireContextGeneration(
            OwnerNativeRestorationContext context,
            CheckpointGenerationManifest manifest,
            CheckpointHeadRecord sourceHead
    ) {
        if (!context.generationManifest().equals(manifest)
                || !sourceHead.digestMatches()
                || !sourceHead.selectedGenerationId().equals(manifest.generationId())
                || !sourceHead.selectedGenerationManifestDigest().equals(manifest.manifestDigest())) {
            throw blocked("Restoration source head does not bind the selected committed generation");
        }
    }

    private static void requireIntentContext(
            OwnerNativeRestorationContext context,
            RestorationIntent intent
    ) {
        CheckpointGenerationManifest manifest = context.generationManifest();
        if (!intent.worldIdentityRoot().equals(context.worldIdentityRoot())
                || !intent.generationId().equals(manifest.generationId())
                || !intent.generationManifestDigest().equals(manifest.manifestDigest())
                || !intent.platformDeterminismManifest().equals(manifest.platformDeterminismManifest())
                || intent.source() != context.source()
                || intent.recoveryResultIdentity().isPresent() != context.legacyRecoveryResult().isPresent()) {
            throw conflict("Frozen restoration intent does not match the selected startup source");
        }
        context.legacyRecoveryResult().ifPresent(result -> {
            if (!intent.recoveryResultIdentity().orElseThrow().equals(result.resultIdentity())
                    || !intent.recoveryResultContentDigest().orElseThrow().equals(result.contentDigest())) {
                throw conflict("Frozen restoration intent binds another Recovery Result");
            }
        });
    }

    private static void verifyPublishedFiles(Path ownerRoot, OwnerNativeRestorationPlan plan) {
        for (OwnerNativeRestorationPlan.NativeFile file : plan.nativeFiles()) {
            Path target = target(ownerRoot, file.targetRelativePath());
            if (!Files.isRegularFile(target)) {
                throw conflict("Completed restoration participant is missing native file: "
                        + file.targetRelativePath());
            }
            byte[] observed = AtomicFilePublication.readBytes(target, "restored owner-native file");
            if (!CheckpointSnapshotDigest.sha256(observed).equals(file.contentDigest())) {
                throw conflict("Completed restoration participant native file changed: "
                        + file.targetRelativePath());
            }
        }
    }

    private static Path target(Path ownerRoot, String relative) {
        Path root = ownerRoot.toAbsolutePath().normalize();
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw conflict("Restoration target escapes owner root");
        return target;
    }

    private static RecoveryMutationGate mutationGate(List<OwnerNativeRestorationPlan> plans) {
        List<RecoveryMutationGate> blocking = plans.stream()
                .map(OwnerNativeRestorationPlan::mutationGate)
                .filter(gate -> !gate.equals(OPEN_GATE))
                .distinct()
                .toList();
        if (blocking.size() > 1) {
            throw conflict("Owner restoration plans contain conflicting mutation gates");
        }
        return blocking.isEmpty() ? OPEN_GATE : blocking.getFirst();
    }

    private static List<String> policyBRuns(List<OwnerNativeRestorationPlan> plans) {
        return plans.stream()
                .flatMap(plan -> plan.policyBRunIdentities().stream())
                .distinct().sorted().toList();
    }

    private static void requireExactReservationFileOwnership(List<OwnerNativeRestorationPlan> plans) {
        List<OwnerNativeRestorationPlan> owners = plans.stream()
                .filter(plan -> plan.nativeFiles().stream().anyMatch(file ->
                        file.targetRelativePath().equals("workstation_reservations.json")))
                .toList();
        if (owners.size() != 1) {
            throw conflict("Restoration requires exactly one owner for workstation_reservations.json");
        }
        OwnerNativeRestorationPlan owner = owners.getFirst();
        boolean historicalWorkstation = owner.ownerId().equals(LegacySplitRecoveryParticipants.WORKSTATION)
                && owner.ownerSchemaVersion() <= 3;
        boolean currentWorkforce = owner.ownerId().equals(LegacySplitRecoveryParticipants.WORKFORCE)
                && owner.ownerSchemaVersion() >= 2;
        if (!historicalWorkstation && !currentWorkforce) {
            throw conflict("Workstation reservation file ownership is incompatible with owner schema");
        }
    }

    private static StartupRecoveryException blocked(String message) {
        return new StartupRecoveryException(StartupRecoveryFailureCode.CHECKPOINT_INVALID, message);
    }

    private static StartupRecoveryException conflict(String message) {
        return new StartupRecoveryException(StartupRecoveryFailureCode.RESTORATION_CONFLICT, message);
    }
}
