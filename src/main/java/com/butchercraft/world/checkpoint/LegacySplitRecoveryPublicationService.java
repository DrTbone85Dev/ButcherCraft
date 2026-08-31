package com.butchercraft.world.checkpoint;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class LegacySplitRecoveryPublicationService {
    private final SplitSnapshotRecoveryAnalyzer analyzer;
    private final LegacySplitRecoveryPublicationProbe probe;

    public LegacySplitRecoveryPublicationService() {
        this(LegacySplitRecoveryPublicationProbe.NONE);
    }

    public LegacySplitRecoveryPublicationService(LegacySplitRecoveryPublicationProbe probe) {
        this.analyzer = new SplitSnapshotRecoveryAnalyzer();
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    public LegacySplitRecoveryDryRunPreview preview(LegacySplitRecoveryPreviewRequest request) {
        Objects.requireNonNull(request, "request");
        SplitSnapshotRecoveryPlan plan = analyzer.analyze(request.analysisSource().reloadReadOnly());
        List<LegacySplitRecoveryPublicationFailure> failures = protectedPathFailures(
                request.targetWorldRoot(),
                request.checkpointRoot(),
                request.analysisSource().sourceRoots(),
                request.protectedWorldRoots()
        );
        boolean protectedPathRejected = !failures.isEmpty();
        if (!plan.publicationEligible()) {
            failures.add(failure(
                    LegacySplitRecoveryPublicationFailure.Code.ANALYSIS_NOT_ELIGIBLE,
                    "eligibility",
                    "Recovery analysis is not publication-eligible: " + plan.eligibility()
            ));
        }

        Optional<GenerationBoundary> boundary = Optional.empty();
        if (!protectedPathRejected) {
            try {
                boundary = Optional.of(resolveGenerationBoundary(plan, request.checkpointRoot()));
            } catch (RuntimeException exception) {
                failures.add(failure(
                        LegacySplitRecoveryPublicationFailure.Code.GENERATION_CONFLICT,
                        "generation",
                        detail(exception, "Recovery generation could not be derived read-only")
                ));
            }
        }
        return new LegacySplitRecoveryDryRunPreview(
                plan,
                boundary.map(GenerationBoundary::generationId),
                boundary.flatMap(GenerationBoundary::predecessorGenerationId),
                LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                RecoveryMutationGate.fromPlan(plan),
                protectedPathRejected,
                failures.isEmpty(),
                failures
        );
    }

    public synchronized LegacySplitRecoveryPublicationReport publish(
            LegacySplitRecoveryPublicationRequest request
    ) {
        Objects.requireNonNull(request, "request");
        SplitSnapshotRecoveryPlan plan = null;
        try {
            List<LegacySplitRecoveryPublicationFailure> pathFailures = protectedPathFailures(
                    request.targetWorldRoot(),
                    request.checkpointRoot(),
                    request.analysisSource().sourceRoots(),
                    request.protectedWorldRoots()
            );
            if (!pathFailures.isEmpty()) {
                return LegacySplitRecoveryPublicationReport.failed(
                        LegacySplitRecoveryPublicationReport.Outcome.RECOVERY_BLOCKED,
                        null,
                        pathFailures
                );
            }

            plan = analyzer.analyze(request.analysisSource().reloadReadOnly());
            reached(LegacySplitRecoveryPublicationPhase.BEFORE_AUTHORIZATION_VALIDATION);
            List<LegacySplitRecoveryPublicationFailure> authorizationFailures = validateAuthorization(
                    plan,
                    request.authorization()
            );
            if (!authorizationFailures.isEmpty()) {
                return LegacySplitRecoveryPublicationReport.failed(
                        LegacySplitRecoveryPublicationReport.Outcome.AUTHORIZATION_REJECTED,
                        plan,
                        authorizationFailures
                );
            }
            reached(LegacySplitRecoveryPublicationPhase.AFTER_REANALYSIS_BEFORE_OWNER_PREPARATION);

            LegacySplitRecoveryPublicationStorage storage =
                    new LegacySplitRecoveryPublicationStorage(request.checkpointRoot());
            Optional<LegacySplitRecoveryResult> existingResult = storage.loadResult(plan.recoveryIdentity());
            if (existingResult.isPresent()) {
                LegacySplitRecoveryResult result = existingResult.orElseThrow();
                if (!result.analysisDigest().equals(plan.analysisDigest())
                        || !result.authorizationIdentity().equals(request.authorization().authorizationIdentity())) {
                    return LegacySplitRecoveryPublicationReport.failed(
                            LegacySplitRecoveryPublicationReport.Outcome.RECOVERY_BLOCKED,
                            plan,
                            List.of(failure(
                                    LegacySplitRecoveryPublicationFailure.Code.RECOVERY_RESULT_CONFLICT,
                                    "recoveryResult",
                                    "Existing Recovery Result does not match current exact authorization"
                            ))
                    );
                }
                return LegacySplitRecoveryPublicationReport.committed(plan, result, true);
            }

            GenerationBoundary boundary = resolveGenerationBoundary(plan, request.checkpointRoot());
            List<LegacySplitRecoveryPreparedOwnerSnapshot> prepared = prepareOwners(
                    plan,
                    request.authorization(),
                    boundary.generationId(),
                    request.ownerPreparers()
            );
            reached(LegacySplitRecoveryPublicationPhase.AFTER_ALL_OWNER_PREPARATIONS);

            LegacySplitRecoveryPublicationIntent frozen = LegacySplitRecoveryPublicationIntent.freeze(
                    plan,
                    request.authorization(),
                    boundary.generationId(),
                    boundary.predecessorGenerationId(),
                    boundary.predecessorManifestDigest(),
                    LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                    prepared
            );
            Optional<LegacySplitRecoveryPublicationIntent> existingIntent = storage.loadIntent(plan.recoveryIdentity());
            if (existingIntent.isPresent()) {
                LegacySplitRecoveryPublicationIntent observed = existingIntent.orElseThrow();
                if (!observed.targets(plan, request.authorization())) {
                    return LegacySplitRecoveryPublicationReport.failed(
                            LegacySplitRecoveryPublicationReport.Outcome.RECOVERY_BLOCKED,
                            plan,
                            List.of(failure(
                                    LegacySplitRecoveryPublicationFailure.Code.GENERATION_CONFLICT,
                                    "publicationIntent",
                                    "Existing frozen publication intent targets different recovery content"
                            ))
                    );
                }
                frozen = observed;
                if (!frozen.preparedOwners().equals(LegacySplitRecoveryPublicationIntent.freeze(
                        plan,
                        request.authorization(),
                        frozen.generationId(),
                        frozen.predecessorGenerationId(),
                        frozen.predecessorManifestDigest(),
                        frozen.requiredOwners(),
                        prepared
                ).preparedOwners())) {
                    return LegacySplitRecoveryPublicationReport.failed(
                            LegacySplitRecoveryPublicationReport.Outcome.RECOVERY_BLOCKED,
                            plan,
                            List.of(failure(
                                    LegacySplitRecoveryPublicationFailure.Code.OWNER_SOURCE_MISMATCH,
                                    "preparedOwners",
                                    "Reprepared owner snapshots differ from the frozen publication intent"
                            ))
                    );
                }
            } else {
                frozen = storage.saveOrObserveIntent(frozen);
            }

            CheckpointPublicationRequest checkpointRequest = new CheckpointPublicationRequest(
                    frozen.generationId(),
                    frozen.predecessorGenerationId(),
                    frozen.predecessorManifestDigest(),
                    plan.authoritativeClockTick(),
                    prepared.stream().map(value -> value.payload()).toList(),
                    LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                    plan.platformDeterminismManifest(),
                    plan.worldIdentityRoot()
            );
            CheckpointFilesystemStore checkpointStore = new CheckpointFilesystemStore(
                    request.checkpointRoot(),
                    this::onCheckpointPhase
            );
            CheckpointPublicationReport checkpointReport = checkpointStore.publish(checkpointRequest);
            if (!checkpointReport.successful()) {
                return LegacySplitRecoveryPublicationReport.failed(
                        LegacySplitRecoveryPublicationReport.Outcome.PUBLICATION_FAILED,
                        plan,
                        List.of(failure(
                                LegacySplitRecoveryPublicationFailure.Code.CHECKPOINT_PUBLICATION_FAILED,
                                "checkpointPublication",
                                checkpointReport.diagnostics().isEmpty()
                                        ? "Checkpoint generation publication failed"
                                        : checkpointReport.diagnostics().getFirst().message()
                        ))
                );
            }
            CheckpointGenerationManifest manifest = checkpointReport.generationManifest().orElseThrow();
            SplitSnapshotRecoveryPlan committedPlan = plan;
            CheckpointHeadRecord head = checkpointReport.headRecord()
                    .orElseGet(() -> matchingHead(checkpointStore, committedPlan, manifest).orElseThrow(() ->
                            new IllegalStateException("Committed recovery generation has no matching head")));
            reached(LegacySplitRecoveryPublicationPhase.AFTER_COMMITTED_HEAD_BEFORE_RESULT_OBSERVATION);
            LegacySplitRecoveryResult result = LegacySplitRecoveryResult.committed(
                    plan,
                    request.authorization(),
                    frozen,
                    manifest,
                    head,
                    checkpointReport.diagnostics(),
                    request.completionTimestampMetadata()
            );
            LegacySplitRecoveryResult observed = storage.saveOrObserveResult(result);
            return LegacySplitRecoveryPublicationReport.committed(
                    plan,
                    observed,
                    checkpointReport.outcome() == CheckpointPublicationOutcome.DUPLICATE_OBSERVATION
            );
        } catch (PublicationInterruptedException exception) {
            return LegacySplitRecoveryPublicationReport.failed(
                    LegacySplitRecoveryPublicationReport.Outcome.PUBLICATION_INTERRUPTED,
                    plan,
                    List.of(failure(
                            LegacySplitRecoveryPublicationFailure.Code.PUBLICATION_INTERRUPTED,
                            "publicationPhase",
                            exception.getMessage()
                    ))
            );
        } catch (OwnerPreparationRejectedException exception) {
            return LegacySplitRecoveryPublicationReport.failed(
                    LegacySplitRecoveryPublicationReport.Outcome.RECOVERY_BLOCKED,
                    plan,
                    exception.failures()
            );
        } catch (SecurityException exception) {
            return LegacySplitRecoveryPublicationReport.failed(
                    LegacySplitRecoveryPublicationReport.Outcome.AUTHORIZATION_REJECTED,
                    plan,
                    List.of(failure(
                            LegacySplitRecoveryPublicationFailure.Code.OPERATOR_AUTHORITY_REQUIRED,
                            "operatorAuthority",
                            detail(exception, "Operator authority is required")
                    ))
            );
        } catch (RuntimeException exception) {
            return LegacySplitRecoveryPublicationReport.failed(
                    LegacySplitRecoveryPublicationReport.Outcome.RECOVERY_BLOCKED,
                    plan,
                    List.of(failure(
                            LegacySplitRecoveryPublicationFailure.Code.RECOVERY_RESULT_CONFLICT,
                            "recoveryPublication",
                            detail(exception, "Recovery publication failed safely")
                    ))
            );
        }
    }

    public LegacySplitRecoveryStatusSnapshot status(
            LegacySplitRecoveryAnalysisSource source,
            Path checkpointRoot
    ) {
        SplitSnapshotRecoveryPlan plan = analyzer.analyze(source.reloadReadOnly());
        Optional<LegacySplitRecoveryResult> result = new LegacySplitRecoveryPublicationStorage(checkpointRoot)
                .loadResult(plan.recoveryIdentity());
        if (result.isPresent()) {
            LegacySplitRecoveryResult value = result.orElseThrow();
            return new LegacySplitRecoveryStatusSnapshot(
                    LegacySplitRecoveryStatusSnapshot.State.RECOVERY_COMMITTED,
                    Optional.of(value.recoveryIdentity()),
                    Optional.of(value.recoveryGenerationId()),
                    value.authorityBlocks(),
                    !value.mutationGate().wholeWorldConsequentialMutationBlocked()
                            && value.mutationGate().blockedAuthorityIdentities().isEmpty(),
                    value.authorityBlocks().isEmpty()
                            ? "Recovery generation committed; no authority blocks remain"
                            : "Recovery generation committed; authority blocks remain"
            );
        }
        LegacySplitRecoveryStatusSnapshot.State state = switch (plan.eligibility()) {
            case NOT_SPLIT -> LegacySplitRecoveryStatusSnapshot.State.NO_SPLIT_DETECTED;
            case RECOVERABLE_PROOF_COMPLETE, RECOVERABLE_WITH_AUTHORITY_BLOCKS ->
                    LegacySplitRecoveryStatusSnapshot.State.AUTHORIZATION_REQUIRED;
            default -> LegacySplitRecoveryStatusSnapshot.State.RECOVERY_BLOCKED;
        };
        return new LegacySplitRecoveryStatusSnapshot(
                state,
                Optional.of(plan.recoveryIdentity()),
                Optional.empty(),
                plan.authorityBlocks(),
                false,
                state == LegacySplitRecoveryStatusSnapshot.State.AUTHORIZATION_REQUIRED
                        ? "Exact operator authorization is required"
                        : "Recovery analysis state: " + plan.eligibility()
        );
    }

    private List<LegacySplitRecoveryPreparedOwnerSnapshot> prepareOwners(
            SplitSnapshotRecoveryPlan plan,
            RecoveryOperatorAuthorization authorization,
            CheckpointGenerationId generationId,
            List<LegacySplitRecoveryOwnerPreparer> preparers
    ) {
        Map<CheckpointOwnerId, LegacySplitRecoveryOwnerPreparer> byOwner = new LinkedHashMap<>();
        for (LegacySplitRecoveryOwnerPreparer preparer : preparers) {
            LegacySplitRecoveryOwnerPreparer value = Objects.requireNonNull(preparer, "ownerPreparer");
            if (byOwner.putIfAbsent(value.ownerId(), value) != null) {
                throw rejected(failure(
                        LegacySplitRecoveryPublicationFailure.Code.OWNER_PREPARER_DUPLICATE,
                        value.ownerId().value(),
                        "Duplicate recovery owner preparer"
                ));
            }
        }
        for (CheckpointOwnerId required : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            if (!byOwner.containsKey(required)) {
                throw rejected(failure(
                        LegacySplitRecoveryPublicationFailure.Code.OWNER_PREPARER_MISSING,
                        required.value(),
                        "Missing required recovery owner preparer"
                ));
            }
        }
        List<CheckpointOwnerId> unexpectedOwners = byOwner.keySet().stream()
                .filter(owner -> !LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS.contains(owner))
                .sorted()
                .toList();
        if (!unexpectedOwners.isEmpty()) {
            throw rejected(failure(
                    LegacySplitRecoveryPublicationFailure.Code.INCOMPLETE_PARTICIPANT_SET,
                    "ownerPreparers",
                    "Recovery owner preparer set contains unexpected owners: " + unexpectedOwners
            ));
        }
        LegacySplitRecoveryOwnerPreparationRequest preparationRequest =
                new LegacySplitRecoveryOwnerPreparationRequest(plan, authorization, generationId);
        List<LegacySplitRecoveryPreparedOwnerSnapshot> prepared = new ArrayList<>();
        for (CheckpointOwnerId ownerId : LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS) {
            LegacySplitRecoveryOwnerPreparationResult result = byOwner.get(ownerId).prepare(preparationRequest);
            if (!result.successful()) {
                throw new OwnerPreparationRejectedException(result.failures());
            }
            if (!result.ownerId().equals(ownerId)) {
                throw rejected(failure(
                        LegacySplitRecoveryPublicationFailure.Code.OWNER_SOURCE_MISMATCH,
                        ownerId.value(),
                        "Recovery owner preparer returned a snapshot for " + result.ownerId().value()
                ));
            }
            LegacySplitRecoveryPreparedOwnerSnapshot snapshot = result.snapshot().orElseThrow();
            try {
                validatePreparedSnapshot(snapshot, preparationRequest);
            } catch (IllegalArgumentException exception) {
                throw rejected(failure(
                        LegacySplitRecoveryPublicationFailure.Code.OWNER_SOURCE_MISMATCH,
                        ownerId.value(),
                        detail(exception, "Prepared owner snapshot failed exact source validation")
                ));
            }
            prepared.add(snapshot);
            if (prepared.size() == 1) {
                reached(LegacySplitRecoveryPublicationPhase.AFTER_SOME_OWNER_PREPARATIONS);
            }
        }
        return prepared.stream().sorted().toList();
    }

    private void validatePreparedSnapshot(
            LegacySplitRecoveryPreparedOwnerSnapshot snapshot,
            LegacySplitRecoveryOwnerPreparationRequest request
    ) {
        OwnerSnapshotDescriptor descriptor = snapshot.payload().descriptor();
        if (!descriptor.generationId().equals(request.generationId())
                || !descriptor.worldIdentityRoot().equals(request.plan().worldIdentityRoot())
                || descriptor.representedSimulationTick() != request.plan().authoritativeClockTick()) {
            throw new IllegalArgumentException("Prepared owner snapshot crosses the recovery publication boundary");
        }
        if (!descriptor.ownerId().equals(LegacySplitRecoveryParticipants.CHECKPOINT_RECOVERY)) {
            RecoverySourceSnapshot source = request.requireSource(descriptor.ownerId());
            if (!source.snapshotIdentity().equals(snapshot.sourceSnapshotIdentity())
                    || !source.contentDigest().equals(snapshot.sourceSnapshotContentDigest())) {
                throw new IllegalArgumentException("Prepared owner snapshot does not bind its exact source");
            }
        }
    }

    private List<LegacySplitRecoveryPublicationFailure> validateAuthorization(
            SplitSnapshotRecoveryPlan plan,
            RecoveryOperatorAuthorization authorization
    ) {
        List<LegacySplitRecoveryPublicationFailure> failures = new ArrayList<>();
        if (!plan.publicationEligible()) {
            failures.add(failure(
                    LegacySplitRecoveryPublicationFailure.Code.ANALYSIS_NOT_ELIGIBLE,
                    "eligibility",
                    "Recovery analysis is not publication-eligible: " + plan.eligibility()
            ));
        }
        if (authorization.disposition() == RecoveryOperatorAuthorization.Disposition.ABORT) {
            failures.add(failure(
                    LegacySplitRecoveryPublicationFailure.Code.AUTHORIZATION_REQUIRED,
                    "disposition",
                    "Operator disposition aborts recovery publication"
            ));
        }
        if (!authorization.targets(plan)) {
            failures.add(failure(
                    LegacySplitRecoveryPublicationFailure.Code.AUTHORIZATION_STALE,
                    "authorization",
                    "Authorization does not match the recomputed Recovery Identity, analysis, world, blocks, or sources"
            ));
        }
        try {
            authorization.operatorEvidence().requirePublicationAuthority();
        } catch (SecurityException exception) {
            failures.add(failure(
                    LegacySplitRecoveryPublicationFailure.Code.OPERATOR_AUTHORITY_REQUIRED,
                    "operatorAuthority",
                    exception.getMessage()
            ));
        }
        return failures;
    }

    private GenerationBoundary resolveGenerationBoundary(
            SplitSnapshotRecoveryPlan plan,
            Path checkpointRoot
    ) {
        LegacySplitRecoveryPublicationStorage storage = new LegacySplitRecoveryPublicationStorage(checkpointRoot);
        Optional<LegacySplitRecoveryPublicationIntent> intent = storage.loadIntent(plan.recoveryIdentity());
        if (intent.isPresent()) {
            LegacySplitRecoveryPublicationIntent frozen = intent.orElseThrow();
            return new GenerationBoundary(
                    frozen.generationId(),
                    frozen.predecessorGenerationId(),
                    frozen.predecessorManifestDigest()
            );
        }
        Optional<LegacySplitRecoveryResult> result = storage.loadResult(plan.recoveryIdentity());
        if (result.isPresent()) {
            LegacySplitRecoveryResult committed = result.orElseThrow();
            return new GenerationBoundary(committed.recoveryGenerationId(), Optional.empty(), Optional.empty());
        }
        CheckpointFilesystemStore store = new CheckpointFilesystemStore(checkpointRoot);
        CheckpointFilesystemRecoveryReport inspection = store.inspectReadOnly(new CheckpointFilesystemRecoveryRequest(
                List.of(),
                plan.worldIdentityRoot(),
                plan.platformDeterminismManifest()
        ));
        long maximumSequence = java.util.stream.Stream.concat(
                        inspection.generationRecords().stream()
                                .map(record -> record.generationId().committedSequence()),
                        inspection.headRecords().stream().map(CheckpointHeadRecord::headSequence)
                )
                .mapToLong(Long::longValue)
                .max()
                .orElse(0L);
        CheckpointGenerationId generationId = CheckpointGenerationId.of(
                Math.addExact(maximumSequence, 1L),
                plan.authoritativeClockTick()
        );
        Optional<CheckpointGenerationId> predecessor = inspection.selection().selectedGenerationId();
        Optional<String> predecessorDigest = inspection.selection().selectedManifestDigest();
        return new GenerationBoundary(generationId, predecessor, predecessorDigest);
    }

    private Optional<CheckpointHeadRecord> matchingHead(
            CheckpointFilesystemStore store,
            SplitSnapshotRecoveryPlan plan,
            CheckpointGenerationManifest manifest
    ) {
        return store.inspectReadOnly(new CheckpointFilesystemRecoveryRequest(
                        LegacySplitRecoveryParticipants.REQUIRED_R2_OWNERS,
                        plan.worldIdentityRoot(),
                        plan.platformDeterminismManifest()
                )).headRecords().stream()
                .filter(CheckpointHeadRecord::digestMatches)
                .filter(head -> head.selectedGenerationId().equals(manifest.generationId()))
                .filter(head -> head.selectedGenerationManifestDigest().equals(manifest.manifestDigest()))
                .findFirst();
    }

    private void onCheckpointPhase(CheckpointPublicationPhase phase) throws IOException {
        switch (phase) {
            case AFTER_MANIFEST -> probe.reached(
                    LegacySplitRecoveryPublicationPhase.AFTER_GENERATION_METADATA_PUBLICATION
            );
            case AFTER_FINAL_MOVE -> probe.reached(
                    LegacySplitRecoveryPublicationPhase.BEFORE_INACTIVE_HEAD_UPDATE
            );
            case DURING_HEAD_WRITE -> probe.reached(
                    LegacySplitRecoveryPublicationPhase.DURING_INACTIVE_HEAD_UPDATE
            );
            default -> {
            }
        }
    }

    private void reached(LegacySplitRecoveryPublicationPhase phase) {
        try {
            probe.reached(phase);
        } catch (IOException exception) {
            throw new PublicationInterruptedException("Recovery publication interrupted at " + phase, exception);
        }
    }

    private List<LegacySplitRecoveryPublicationFailure> protectedPathFailures(
            Path targetWorldRoot,
            Path checkpointRoot,
            List<Path> sourceRoots,
            List<Path> protectedRoots
    ) {
        List<LegacySplitRecoveryPublicationFailure> failures = new ArrayList<>();
        for (Path candidate : java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(targetWorldRoot, checkpointRoot),
                        sourceRoots.stream().map(path -> path.toAbsolutePath().normalize())
                ).toList()) {
            for (Path protectedRoot : protectedRoots) {
                if (candidate.equals(protectedRoot) || candidate.startsWith(protectedRoot)) {
                    failures.add(failure(
                            LegacySplitRecoveryPublicationFailure.Code.PROTECTED_PATH,
                            "path",
                            "Recovery publication is prohibited for protected path " + protectedRoot
                    ));
                }
            }
        }
        return failures.stream().distinct().toList();
    }

    private static LegacySplitRecoveryPublicationFailure failure(
            LegacySplitRecoveryPublicationFailure.Code code,
            String field,
            String detail
    ) {
        return new LegacySplitRecoveryPublicationFailure(code, field, detail);
    }

    private static String detail(RuntimeException exception, String fallback) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? fallback
                : exception.getMessage();
    }

    private static OwnerPreparationRejectedException rejected(
            LegacySplitRecoveryPublicationFailure failure
    ) {
        return new OwnerPreparationRejectedException(List.of(failure));
    }

    private record GenerationBoundary(
            CheckpointGenerationId generationId,
            Optional<CheckpointGenerationId> predecessorGenerationId,
            Optional<String> predecessorManifestDigest
    ) {
        private GenerationBoundary {
            generationId = Objects.requireNonNull(generationId, "generationId");
            predecessorGenerationId = Objects.requireNonNull(predecessorGenerationId, "predecessorGenerationId");
            predecessorManifestDigest = CheckpointValidation.optionalDigest(
                    predecessorManifestDigest,
                    "predecessorManifestDigest"
            );
        }
    }

    private static final class PublicationInterruptedException extends RuntimeException {
        private PublicationInterruptedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class OwnerPreparationRejectedException extends RuntimeException {
        private final List<LegacySplitRecoveryPublicationFailure> failures;

        private OwnerPreparationRejectedException(List<LegacySplitRecoveryPublicationFailure> failures) {
            super("Recovery owner preparation rejected");
            this.failures = List.copyOf(failures);
        }

        private List<LegacySplitRecoveryPublicationFailure> failures() {
            return failures;
        }
    }
}
