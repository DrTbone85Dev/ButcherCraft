package com.butchercraft.world.checkpoint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class CheckpointOwnerSnapshotCoordinator {
    public static final CheckpointOwnerId CLOCK_OWNER =
            CheckpointOwnerId.of("butchercraft:simulation_clock");
    public static final CheckpointOwnerId SCHEDULER_OWNER =
            CheckpointOwnerId.of("butchercraft:simulation_scheduler");
    public static final String CLOCK_TICK_KEY =
            "butchercraft:checkpoint_metadata/clock_tick";
    public static final String SCHEDULER_FINALIZED_TICK_KEY =
            "butchercraft:checkpoint_metadata/scheduler_finalized_tick";
    public static final String SNAPSHOT_IDENTITY_KEY =
            "butchercraft:checkpoint_metadata/snapshot_identity";
    public static final String CONFIGURATION_IDENTITY_KEY =
            "butchercraft:checkpoint_metadata/configuration_identity";

    private final List<CheckpointOwnerId> requiredOwners;
    private final List<CheckpointOwnerSnapshotProvider> providers;
    private final List<CheckpointOwnerSnapshotRestorer> restorers;

    public CheckpointOwnerSnapshotCoordinator(
            List<CheckpointOwnerId> requiredOwners,
            List<CheckpointOwnerSnapshotProvider> providers,
            List<CheckpointOwnerSnapshotRestorer> restorers
    ) {
        this.requiredOwners = Objects.requireNonNull(requiredOwners, "requiredOwners").stream()
                .map(owner -> Objects.requireNonNull(owner, "requiredOwner"))
                .sorted()
                .toList();
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.restorers = List.copyOf(Objects.requireNonNull(restorers, "restorers"));
    }

    public List<CheckpointOwnerId> requiredOwners() {
        return requiredOwners;
    }

    public CheckpointCoordinatedCaptureReport capture(CheckpointOwnerSnapshotContext context) {
        Objects.requireNonNull(context, "context");
        List<CheckpointFailure> failures = new ArrayList<>();
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotProvider> providerMap = providerMap(failures);
        for (CheckpointOwnerId requiredOwner : requiredOwners) {
            if (!providerMap.containsKey(requiredOwner)) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_PROVIDER_MISSING,
                        requiredOwner.value(),
                        "Required checkpoint owner provider is missing"
                ));
            }
        }
        if (!failures.isEmpty()) {
            return CheckpointCoordinatedCaptureReport.failed(List.of(), failures);
        }

        List<CheckpointCapturedOwnerSnapshot> captured = new ArrayList<>();
        for (CheckpointOwnerId ownerId : requiredOwners) {
            CheckpointOwnerSnapshotProvider provider = providerMap.get(ownerId);
            try {
                CheckpointOwnerSnapshotCaptureResult result = provider.capture(context);
                if (result.successful()) {
                    captured.add(result.snapshot().orElseThrow());
                } else {
                    failures.addAll(result.failures());
                }
            } catch (RuntimeException exception) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_SNAPSHOT_SERIALIZATION_FAILURE,
                        ownerId.value(),
                        "Owner snapshot capture failed unexpectedly"
                ));
            }
        }

        failures.addAll(validateCapturedSnapshots(captured, context));
        if (!failures.isEmpty()) {
            return CheckpointCoordinatedCaptureReport.failed(captured, failures);
        }

        CheckpointPublicationRequest request = new CheckpointPublicationRequest(
                context.generationId(),
                context.predecessorGenerationId(),
                context.predecessorManifestDigest(),
                context.authoritativeSimulationTick(),
                captured.stream().map(CheckpointCapturedOwnerSnapshot::payload).toList(),
                requiredOwners,
                context.platformDeterminismManifest(),
                context.worldIdentityRoot()
        );
        return CheckpointCoordinatedCaptureReport.captured(request, captured);
    }

    public CheckpointCoordinatedRestorationReport restoreSelected(
            CheckpointFilesystemStore store,
            CheckpointFilesystemRecoveryRequest request
    ) {
        Objects.requireNonNull(store, "store");
        CheckpointRecoveredGenerationReport report = store.loadSelectedGeneration(request);
        if (!report.successful()) {
            return CheckpointCoordinatedRestorationReport.blocked(
                    report.filesystemRecoveryReport().selection().selectedGenerationId(),
                    List.of(),
                    List.of(),
                    report.failures()
            );
        }
        return restore(report.recoveredGeneration().orElseThrow());
    }

    public CheckpointCoordinatedRestorationReport restore(CheckpointRecoveredGeneration recoveredGeneration) {
        Objects.requireNonNull(recoveredGeneration, "recoveredGeneration");
        CheckpointGenerationManifest manifest = recoveredGeneration.manifest();
        List<CheckpointFailure> failures = new ArrayList<>();
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotRestorer> restorerMap = restorerMap(failures);
        for (CheckpointOwnerId requiredOwner : requiredOwners) {
            if (!restorerMap.containsKey(requiredOwner)) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_PROVIDER_MISSING,
                        requiredOwner.value(),
                        "Required checkpoint owner restorer is missing"
                ));
            }
        }
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> payloads = payloadMap(recoveredGeneration, failures);
        for (CheckpointOwnerId requiredOwner : requiredOwners) {
            if (!payloads.containsKey(requiredOwner)) {
                failures.add(failure(
                        CheckpointFailureCode.REQUIRED_OWNER_ABSENT,
                        requiredOwner.value(),
                        "Recovered checkpoint generation is missing a required owner payload"
                ));
            }
        }
        if (!failures.isEmpty()) {
            return blocked(manifest.generationId(), List.of(), List.of(), failures);
        }

        List<CheckpointOwnerRestorationCandidate> candidates = new ArrayList<>();
        for (CheckpointOwnerId ownerId : requiredOwners) {
            CheckpointOwnerSnapshotPayload snapshot = payloads.get(ownerId);
            CheckpointOwnerSnapshotRestorer restorer = restorerMap.get(ownerId);
            try {
                CheckpointOwnerRestorationPreparation preparation = restorer.prepare(
                        new CheckpointOwnerRestorationRequest(
                                snapshot.descriptor(),
                                snapshot.payloadBytes(),
                                manifest.generationId(),
                                manifest.platformDeterminismManifest(),
                                manifest.worldIdentityRoot()
                        )
                );
                if (preparation.successful()) {
                    candidates.add(preparation.candidate().orElseThrow());
                } else {
                    failures.addAll(preparation.failures());
                }
            } catch (RuntimeException exception) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_RESTORATION_VALIDATION_FAILURE,
                        ownerId.value(),
                        "Owner restoration validation failed unexpectedly"
                ));
            }
        }

        List<CheckpointOwnerId> preparedOwners = candidates.stream()
                .map(CheckpointOwnerRestorationCandidate::ownerId)
                .sorted()
                .toList();
        failures.addAll(validatePreparedCandidates(candidates, manifest));
        if (!failures.isEmpty()) {
            return blocked(manifest.generationId(), preparedOwners, List.of(), failures);
        }

        for (CheckpointOwnerRestorationCandidate candidate : candidates) {
            List<CheckpointFailure> publicationFailures = candidate.validatePublication();
            if (!publicationFailures.isEmpty()) {
                failures.addAll(publicationFailures);
            }
        }
        if (!failures.isEmpty()) {
            failures.add(failure(
                    CheckpointFailureCode.COORDINATED_PUBLICATION_FAILURE,
                    "restorationPublication",
                    "At least one owner rejected restoration publication before state mutation"
            ));
            return blocked(manifest.generationId(), preparedOwners, List.of(), failures);
        }

        List<CheckpointOwnerRestorationCandidate> attemptedPublicationCandidates = new ArrayList<>();
        List<CheckpointOwnerId> publishedOwners = new ArrayList<>();
        for (CheckpointOwnerRestorationCandidate candidate : candidates) {
            attemptedPublicationCandidates.add(candidate);
            CheckpointOwnerRestorationPublicationResult result = candidate.publish();
            if (!result.successful()) {
                failures.addAll(result.failures());
                failures.add(failure(
                        CheckpointFailureCode.COORDINATED_PUBLICATION_FAILURE,
                        candidate.ownerId().value(),
                        "Owner publication failed during coordinated restoration"
                ));
                List<CheckpointOwnerId> remainingPublishedOwners =
                        rollbackAttemptedPublications(attemptedPublicationCandidates, failures);
                return blocked(manifest.generationId(), preparedOwners, remainingPublishedOwners, failures);
            }
            publishedOwners.add(candidate.ownerId());
        }
        return CheckpointCoordinatedRestorationReport.restored(
                manifest.generationId(),
                preparedOwners,
                publishedOwners
        );
    }

    private List<CheckpointOwnerId> rollbackAttemptedPublications(
            List<CheckpointOwnerRestorationCandidate> attemptedPublicationCandidates,
            List<CheckpointFailure> failures
    ) {
        List<CheckpointOwnerId> remainingPublishedOwners = attemptedPublicationCandidates.stream()
                .map(CheckpointOwnerRestorationCandidate::ownerId)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        for (int index = attemptedPublicationCandidates.size() - 1; index >= 0; index--) {
            CheckpointOwnerRestorationCandidate candidate = attemptedPublicationCandidates.get(index);
            CheckpointOwnerRestorationPublicationResult rollback = candidate.rollbackPublication();
            if (rollback.successful()) {
                remainingPublishedOwners.remove(candidate.ownerId());
                continue;
            }
            failures.addAll(rollback.failures());
            failures.add(failure(
                    CheckpointFailureCode.PARTIAL_RESTORATION_ATTEMPT,
                    candidate.ownerId().value(),
                    "Owner rollback failed after coordinated restoration publication failure"
            ));
        }
        return remainingPublishedOwners.stream().sorted().toList();
    }

    private List<CheckpointFailure> validateCapturedSnapshots(
            List<CheckpointCapturedOwnerSnapshot> snapshots,
            CheckpointOwnerSnapshotContext context
    ) {
        List<CheckpointFailure> failures = new ArrayList<>();
        Map<CheckpointOwnerId, CheckpointCapturedOwnerSnapshot> byOwner = new TreeMap<>();
        for (CheckpointCapturedOwnerSnapshot snapshot : snapshots) {
            CheckpointCapturedOwnerSnapshot previous = byOwner.putIfAbsent(snapshot.ownerId(), snapshot);
            if (previous != null) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_PROVIDER_DUPLICATE,
                        snapshot.ownerId().value(),
                        "Checkpoint capture produced duplicate owner snapshots"
                ));
            }
            OwnerSnapshotDescriptor descriptor = snapshot.payload().descriptor();
            if (!descriptor.generationId().equals(context.generationId())) {
                failures.add(failure(
                        CheckpointFailureCode.MIXED_GENERATION_IDENTITY,
                        descriptor.ownerId().value(),
                        "Owner snapshot generation does not match the capture context"
                ));
            }
            if (!descriptor.worldIdentityRoot().equals(context.worldIdentityRoot())) {
                failures.add(failure(
                        CheckpointFailureCode.WORLD_IDENTITY_MISMATCH,
                        descriptor.ownerId().value(),
                        "Owner snapshot World Identity root does not match the capture context"
                ));
            }
            if (!descriptor.configurationIdentity()
                    .equals(snapshot.validationMetadata().value(CONFIGURATION_IDENTITY_KEY).orElse(""))) {
                failures.add(failure(
                        CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH,
                        descriptor.ownerId().value(),
                        "Owner snapshot configuration identity does not match owner metadata"
                ));
            }
            if (!descriptor.snapshotIdentity()
                    .equals(snapshot.validationMetadata().value(SNAPSHOT_IDENTITY_KEY).orElse(""))) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_RESTORATION_VALIDATION_FAILURE,
                        descriptor.ownerId().value(),
                        "Owner snapshot identity does not match owner metadata"
                ));
            }
        }
        for (CheckpointOwnerId ownerId : requiredOwners) {
            if (!byOwner.containsKey(ownerId)) {
                failures.add(failure(
                        CheckpointFailureCode.REQUIRED_OWNER_ABSENT,
                        ownerId.value(),
                        "Required owner did not produce a checkpoint snapshot"
                ));
            }
        }
        failures.addAll(validateClockSchedulerRelationship(
                snapshots.stream().map(CheckpointCapturedOwnerSnapshot::validationMetadata).toList()
        ));
        return failures;
    }

    private List<CheckpointFailure> validatePreparedCandidates(
            List<CheckpointOwnerRestorationCandidate> candidates,
            CheckpointGenerationManifest manifest
    ) {
        List<CheckpointFailure> failures = new ArrayList<>();
        Map<CheckpointOwnerId, CheckpointOwnerRestorationCandidate> byOwner = new TreeMap<>();
        for (CheckpointOwnerRestorationCandidate candidate : candidates) {
            CheckpointOwnerRestorationCandidate previous = byOwner.putIfAbsent(candidate.ownerId(), candidate);
            if (previous != null) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_PROVIDER_DUPLICATE,
                        candidate.ownerId().value(),
                        "Checkpoint restoration produced duplicate owner candidates"
                ));
            }
        }
        for (OwnerSnapshotDescriptor descriptor : manifest.ownerSnapshots()) {
            CheckpointOwnerRestorationCandidate candidate = byOwner.get(descriptor.ownerId());
            if (candidate == null) {
                continue;
            }
            if (!descriptor.snapshotIdentity()
                    .equals(candidate.validationMetadata().value(SNAPSHOT_IDENTITY_KEY).orElse(""))) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_RESTORATION_VALIDATION_FAILURE,
                        descriptor.ownerId().value(),
                        "Prepared owner snapshot identity does not match generation metadata"
                ));
            }
            if (!descriptor.configurationIdentity()
                    .equals(candidate.validationMetadata().value(CONFIGURATION_IDENTITY_KEY).orElse(""))) {
                failures.add(failure(
                        CheckpointFailureCode.PLATFORM_DETERMINISM_MANIFEST_MISMATCH,
                        descriptor.ownerId().value(),
                        "Prepared owner configuration identity does not match generation metadata"
                ));
            }
        }
        failures.addAll(validateClockSchedulerRelationship(
                candidates.stream().map(CheckpointOwnerRestorationCandidate::validationMetadata).toList()
        ));
        return failures;
    }

    private List<CheckpointFailure> validateClockSchedulerRelationship(
            List<CheckpointOwnerValidationMetadata> metadata
    ) {
        Map<CheckpointOwnerId, CheckpointOwnerValidationMetadata> byOwner = new TreeMap<>();
        for (CheckpointOwnerValidationMetadata value : metadata) {
            byOwner.put(value.ownerId(), value);
        }
        Optional<String> clockTick = Optional.ofNullable(byOwner.get(CLOCK_OWNER))
                .flatMap(value -> value.value(CLOCK_TICK_KEY));
        Optional<String> schedulerTick = Optional.ofNullable(byOwner.get(SCHEDULER_OWNER))
                .flatMap(value -> value.value(SCHEDULER_FINALIZED_TICK_KEY));
        if (clockTick.isPresent() && schedulerTick.isPresent() && !clockTick.get().equals(schedulerTick.get())) {
            return List.of(failure(
                    CheckpointFailureCode.CLOCK_SCHEDULER_TICK_MISMATCH,
                    "authoritativeSimulationTick",
                    "Clock tick and Scheduler finalized tick differ"
            ));
        }
        return List.of();
    }

    private Map<CheckpointOwnerId, CheckpointOwnerSnapshotProvider> providerMap(
            List<CheckpointFailure> failures
    ) {
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotProvider> byOwner = new LinkedHashMap<>();
        for (CheckpointOwnerSnapshotProvider provider : providers) {
            CheckpointOwnerSnapshotProvider value = Objects.requireNonNull(provider, "provider");
            CheckpointOwnerSnapshotProvider previous = byOwner.putIfAbsent(value.ownerId(), value);
            if (previous != null) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_PROVIDER_DUPLICATE,
                        value.ownerId().value(),
                        "Duplicate checkpoint owner provider"
                ));
            }
        }
        return byOwner;
    }

    private Map<CheckpointOwnerId, CheckpointOwnerSnapshotRestorer> restorerMap(
            List<CheckpointFailure> failures
    ) {
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotRestorer> byOwner = new LinkedHashMap<>();
        for (CheckpointOwnerSnapshotRestorer restorer : restorers) {
            CheckpointOwnerSnapshotRestorer value = Objects.requireNonNull(restorer, "restorer");
            CheckpointOwnerSnapshotRestorer previous = byOwner.putIfAbsent(value.ownerId(), value);
            if (previous != null) {
                failures.add(failure(
                        CheckpointFailureCode.OWNER_PROVIDER_DUPLICATE,
                        value.ownerId().value(),
                        "Duplicate checkpoint owner restorer"
                ));
            }
        }
        return byOwner;
    }

    private Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> payloadMap(
            CheckpointRecoveredGeneration generation,
            List<CheckpointFailure> failures
    ) {
        Map<CheckpointOwnerId, CheckpointOwnerSnapshotPayload> byOwner = new TreeMap<>();
        for (CheckpointOwnerSnapshotPayload payload : generation.ownerSnapshots()) {
            CheckpointOwnerSnapshotPayload previous = byOwner.putIfAbsent(payload.descriptor().ownerId(), payload);
            if (previous != null) {
                failures.add(failure(
                        CheckpointFailureCode.DUPLICATE_OWNER_SNAPSHOT,
                        payload.descriptor().ownerId().value(),
                        "Recovered generation contains duplicate owner payloads"
                ));
            }
        }
        return byOwner;
    }

    private CheckpointCoordinatedRestorationReport blocked(
            CheckpointGenerationId generationId,
            List<CheckpointOwnerId> preparedOwners,
            List<CheckpointOwnerId> publishedOwners,
            List<CheckpointFailure> failures
    ) {
        return CheckpointCoordinatedRestorationReport.blocked(
                Optional.of(generationId),
                preparedOwners,
                publishedOwners,
                failures
        );
    }

    private CheckpointFailure failure(CheckpointFailureCode code, String field, String message) {
        return new CheckpointFailure(code, field, message);
    }
}
